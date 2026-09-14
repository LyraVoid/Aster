//! Conservative, current-boot-only operations. No custom KernelPatch interfaces.
mod config;
mod mount;
mod platform;
mod probe;
#[cfg(test)]
mod tests;

use anyhow::{Context, Result, ensure};
use config::{AutoState, Config, STABLE_SECONDS};
use serde::{Deserialize, Serialize};
use std::{
    fs::{self, File, OpenOptions},
    io::{Read, Write},
    os::{
        fd::AsRawFd,
        unix::{fs::OpenOptionsExt, process::CommandExt},
    },
    path::{Path, PathBuf},
    process::{Command, Stdio},
    time::Duration,
};

const ROOT: &str = "/data/adb/ap/runtime-safety";
const LIMIT: u64 = 256 * 1024;
const TRIAL_SECONDS: u64 = 60;

#[derive(clap::Subcommand, Debug)]
pub enum Action {
    Status,
    Logs,
    Preview {
        #[arg(value_parser = ["hide", "umount"])]
        kind: String,
        #[arg(long)]
        source: Option<String>,
    },
    /// Persist preferences only. Never starts, applies or restores anything.
    Configure {
        #[arg(long)]
        source: Option<String>,
        #[arg(long, value_parser = ["on", "off"])]
        hide_auto: Option<String>,
        #[arg(long, value_parser = ["on", "off"])]
        umount_auto: Option<String>,
        #[arg(long, value_parser = ["on", "off"])]
        probe: Option<String>,
    },
    Apply {
        token: String,
    },
    Confirm {
        token: String,
    },
    Restore,
    #[command(hide = true)]
    Watch {
        token: String,
    },
    #[command(hide = true)]
    Probe {
        target: String,
    },
    /// Boot-stage entry point. Inspection by default; activation only when the
    /// user explicitly enabled it.
    #[command(hide = true)]
    Boot {
        stage: String,
    },
}

#[derive(Debug, Clone, Serialize, Deserialize, PartialEq, Eq)]
#[serde(tag = "type")]
pub enum Change {
    Property {
        key: String,
        before: String,
        after: String,
    },
    Mount(mount::Plan),
}
#[derive(Debug, Clone, Serialize, Deserialize)]
struct State {
    token: String,
    boot: String,
    kind: String,
    phase: String,
    changes: Vec<Change>,
    attempted: usize,
    deadline: u64,
    owner: Option<(u32, String)>,
    error: Option<String>,
    #[serde(default)]
    notice: Option<String>,
    #[serde(default)]
    property_checks: Vec<platform::PropertyCheck>,
    /// True for sessions started by the boot-stage runner. Those skip the
    /// manual 60-second trial and are covered by interruption detection.
    #[serde(default)]
    auto: bool,
}

trait Backend {
    fn safe(&mut self) -> Result<bool>;
    fn validate(&mut self, change: &Change) -> Result<()>;
    fn apply(&mut self, change: &Change) -> Result<()>;
    fn restore(&mut self, change: &Change) -> Result<()>;
}
trait Journal {
    fn save(&mut self, state: &State) -> Result<()>;
    fn event(&mut self, message: &str) -> Result<()>;
}

fn rollback(
    state: &mut State,
    backend: &mut impl Backend,
    journal: &mut impl Journal,
) -> Result<()> {
    state.phase = "restoring".into();
    // Recovery must still be attempted when storage/logging is failing.
    let _ = journal.save(state);
    let mut failures = Vec::new();
    for change in state.changes.iter().take(state.attempted).rev() {
        let _ = journal.event(&format!("restoring {change:?}"));
        if let Err(error) = backend.restore(change) {
            failures.push(format!("{error:#}"));
        }
    }
    if failures.is_empty() {
        state.phase = "off".into();
        state.attempted = 0;
        state.owner = None;
        let _ = journal.event("restore complete; feature off");
    } else {
        state.phase = "recovery_failed".into();
        state.error = Some(failures.join("; "));
        let _ = journal.event(&format!("restore failed: {}", failures.join("; ")));
    }
    journal.save(state)?;
    ensure!(failures.is_empty(), "Recovery incomplete; backups retained");
    Ok(())
}

fn apply(state: &mut State, backend: &mut impl Backend, journal: &mut impl Journal) -> Result<()> {
    let result = (|| {
        ensure!(!backend.safe()?, "Safe mode: operation blocked");
        for change in &state.changes {
            backend.validate(change)?;
        }
        state.phase = "applying".into();
        journal.save(state)?;
        for i in 0..state.changes.len() {
            ensure!(!backend.safe()?, "Safe mode detected during apply");
            backend.validate(&state.changes[i])?;
            // Write intent BEFORE the operation; recovery also covers partial failures.
            state.attempted = i + 1;
            journal.save(state)?;
            journal.event(&format!("applying item {}: {:?}", i + 1, state.changes[i]))?;
            backend.apply(&state.changes[i])?;
        }
        // An automatically started session has no user to confirm it, so it goes
        // straight to `active`. The supervisor still restores it on safe mode and
        // the boot-scoped stability watchdog still guards against a broken boot.
        state.phase = if state.auto { "active" } else { "trial" }.into();
        state.deadline = platform::uptime()? + TRIAL_SECONDS;
        journal.save(state)?;
        journal.event(if state.auto {
            "automatic session active; monitoring for interruption"
        } else {
            "trial started; auto-restore in 60 seconds"
        })
    })();
    if let Err(error) = result {
        state.error = Some(format!("{error:#}"));
        let _ = journal.event(&format!("apply failed: {error:#}"));
        rollback(state, backend, journal)?;
        return Err(error);
    }
    Ok(())
}

struct Store(PathBuf);
impl Store {
    fn open() -> Result<(Self, File)> {
        let path = PathBuf::from(ROOT);
        fs::create_dir_all(&path)?;
        use std::os::unix::fs::PermissionsExt;
        ensure!(
            path.canonicalize()? == path,
            "Safety directory must not be a symlink"
        );
        fs::set_permissions(&path, fs::Permissions::from_mode(0o700))?;
        let file = OpenOptions::new()
            .read(true)
            .write(true)
            .create(true)
            .truncate(false)
            .custom_flags(libc::O_NOFOLLOW)
            .mode(0o600)
            .open(path.join("lock"))?;
        // Nonblocking: never wait indefinitely on a stuck recovery process.
        ensure!(
            unsafe { libc::flock(file.as_raw_fd(), libc::LOCK_EX | libc::LOCK_NB) } == 0,
            "Another operation is running; retry shortly"
        );
        Ok((Self(path), file))
    }
    fn load(&self) -> Result<Option<State>> {
        let path = self.0.join("state.json");
        if !path.try_exists()? {
            return Ok(None);
        }
        Ok(Some(serde_json::from_slice(&bounded_read(&path)?)?))
    }
    fn load_config(&self) -> Result<Config> {
        self.load_json("config.json")
    }
    fn save_config(&mut self, config: &Config) -> Result<()> {
        self.write_json("config.json", config)
    }
    fn load_auto(&self) -> Result<AutoState> {
        self.load_json("auto.json")
    }
    fn save_auto(&mut self, auto: &AutoState) -> Result<()> {
        self.write_json("auto.json", auto)
    }
    fn load_json<T: serde::de::DeserializeOwned + Default>(&self, name: &str) -> Result<T> {
        let path = self.0.join(name);
        if !path.try_exists()? {
            return Ok(T::default());
        }
        serde_json::from_slice(&bounded_read(&path)?)
            .with_context(|| format!("Invalid saved {name}"))
    }
    fn write_json<T: Serialize>(&self, name: &str, value: &T) -> Result<()> {
        let temp = self.0.join(format!("{name}.tmp"));
        let mut file = OpenOptions::new()
            .write(true)
            .create(true)
            .truncate(true)
            .mode(0o600)
            .custom_flags(libc::O_NOFOLLOW)
            .open(&temp)?;
        file.write_all(&serde_json::to_vec_pretty(value)?)?;
        file.sync_all()?;
        fs::rename(temp, self.0.join(name))?;
        File::open(&self.0)?.sync_all()?;
        Ok(())
    }
    /// Append one line to the read-only boot probe log, rotating like the audit log.
    fn probe_line(&mut self, line: &str) -> Result<()> {
        let path = self.0.join("boot-probe.log");
        if path.metadata().map(|m| m.len() >= LIMIT).unwrap_or(false) {
            fs::rename(&path, self.0.join("boot-probe.previous.log"))?;
        }
        let mut file = OpenOptions::new()
            .append(true)
            .create(true)
            .custom_flags(libc::O_NOFOLLOW)
            .mode(0o600)
            .open(path)?;
        writeln!(file, "{}", line.replace(['\n', '\r'], " "))?;
        file.sync_data()?;
        Ok(())
    }
}
fn bounded_read(path: &Path) -> Result<Vec<u8>> {
    let mut bytes = Vec::new();
    OpenOptions::new()
        .read(true)
        .custom_flags(libc::O_NOFOLLOW)
        .open(path)?
        .take(LIMIT + 1)
        .read_to_end(&mut bytes)?;
    ensure!(bytes.len() as u64 <= LIMIT, "Safety file too large");
    Ok(bytes)
}
impl Journal for Store {
    fn save(&mut self, state: &State) -> Result<()> {
        self.write_json("state.json", state)
    }
    fn event(&mut self, message: &str) -> Result<()> {
        let path = self.0.join("audit.log");
        if path.metadata().map(|m| m.len() >= LIMIT).unwrap_or(false) {
            fs::rename(&path, self.0.join("audit.previous.log"))?;
        }
        let mut file = OpenOptions::new()
            .append(true)
            .create(true)
            .custom_flags(libc::O_NOFOLLOW)
            .mode(0o600)
            .open(path)?;
        writeln!(
            file,
            "unix={} uptime={} {}",
            std::time::SystemTime::now()
                .duration_since(std::time::UNIX_EPOCH)
                .unwrap_or_default()
                .as_secs(),
            platform::uptime().unwrap_or(0),
            message.replace(['\n', '\r'], " ")
        )?;
        file.sync_data()?;
        Ok(())
    }
}
fn owner_alive(state: &State) -> bool {
    state
        .owner
        .as_ref()
        .is_some_and(|(pid, start)| platform::process_start(*pid).as_ref() == Some(start))
}
fn reconcile(
    state: &mut State,
    backend: &mut impl Backend,
    journal: &mut impl Journal,
    boot: &str,
    safe: bool,
    now: u64,
) -> Result<()> {
    if state.boot != boot {
        // Kernel properties/mounts from an earlier boot no longer exist. Never
        // replay old originals over a new boot or automatically re-enable.
        for change in &state.changes {
            if let Change::Mount(plan) = change {
                if let Err(error) = mount::discard_old_boot(plan) {
                    let _ = journal.event(&format!("old backup retained: {error:#}"));
                }
            }
        }
        // A session that could not be restored keeps its warning: the retained
        // backups must not look like a clean shutdown.
        let unrestored = state.phase == "recovery_failed";
        state.phase = "off".into();
        state.attempted = 0;
        state.owner = None;
        state.error = Some(
            if unrestored {
                "Previous session could not be fully restored; backups were kept for manual recovery"
            } else {
                "Previous boot ended; feature remains off"
            }
            .into(),
        );
        state.boot = boot.into();
        journal.save(state)?;
        journal.event("new boot: previous session disabled; no replay")?;
    } else if state.phase != "off"
        && (safe
            || (state.phase == "trial" && now >= state.deadline)
            || (matches!(
                state.phase.as_str(),
                "queued" | "applying" | "trial" | "active"
            ) && !owner_alive(state)))
    {
        state.error = Some(
            if safe {
                "Safe mode: automatically disabled"
            } else {
                "Trial expired or supervisor stopped"
            }
            .into(),
        );
        rollback(state, backend, journal)?;
    }
    Ok(())
}

/// Result of inspecting the single current session.
struct Inspection {
    state: Option<State>,
    safe: bool,
    safety_error: Option<String>,
    recovery_error: Option<String>,
}

fn inspect(store: &mut Store, backend: &mut platform::Real, boot: &str) -> Result<Inspection> {
    // Unknown safety status blocks new actions and triggers conservative recovery.
    let safety = backend.safe();
    let safe = safety.as_ref().copied().unwrap_or(true);
    let safety_error = safety.err().map(|error| error.to_string());
    let mut state = store.load()?;
    let mut recovery_error = None;
    if let Some(state) = &mut state {
        if let Err(error) = reconcile(state, backend, store, boot, safe, platform::uptime()?) {
            let message = format!("{error:#}");
            state.error = Some(format!("Recovery incomplete: {message}"));
            let _ = store.event(&format!("recovery requires attention: {message}"));
            recovery_error = Some(message);
        }
    }
    Ok(Inspection {
        state,
        safe,
        safety_error,
        recovery_error,
    })
}

/// Spawn the detached supervisor. It waits for the store lock, so the owner
/// identity must be saved before the child can proceed.
fn spawn_supervisor(store: &mut Store, state: &mut State) -> Result<()> {
    let mut child = Command::new(std::env::current_exe()?)
        .args(["runtime-safety", "watch", &state.token])
        .process_group(0)
        .stdin(Stdio::null())
        .stdout(Stdio::null())
        .stderr(Stdio::null())
        .spawn()?;
    let started = (|| {
        state.owner = Some((
            child.id(),
            platform::process_start(child.id()).context("Supervisor did not start")?,
        ));
        store.save(state)?;
        store.event(&format!("supervisor started for {}", state.token))
    })();
    if let Err(error) = started {
        let _ = child.kill();
        let _ = child.wait();
        return Err(error);
    }
    Ok(())
}

/// The session ended. An automatically started session never restarts itself:
/// its option is turned off while the configured paths and the log are kept.
fn settle_auto(
    store: &mut Store,
    config: &mut Config,
    auto: &mut AutoState,
    kinds: &[String],
    reason: &str,
) -> Result<()> {
    let mut disabled = Vec::new();
    for kind in kinds {
        if config.disable_auto(kind) {
            disabled.push(kind.clone());
        }
    }
    auto.pending.clear();
    auto.healthy = true;
    auto.last_error = Some(reason.to_owned());
    store.save_config(config)?;
    store.save_auto(auto)?;
    store.event(&format!(
        "automatic options disabled ({reason}): {}",
        if disabled.is_empty() {
            "none".to_owned()
        } else {
            disabled.join(", ")
        }
    ))
}

fn settle_after_session(store: &mut Store, state: &State) -> Result<()> {
    let mut config = store.load_config()?;
    let mut auto = store.load_auto()?;
    if auto.boot != state.boot {
        return Ok(());
    }
    let kinds = if auto.pending.is_empty() {
        config.enabled_kinds()
    } else {
        auto.pending.clone()
    };
    let reason = state
        .error
        .clone()
        .unwrap_or_else(|| "automatic session ended".into());
    settle_auto(store, &mut config, &mut auto, &kinds, &reason)
}

/// Mark an automatically applied session stable once the framework has finished
/// booting and the stability window has elapsed. Until then an abnormal reboot
/// is still treated as an interruption.
fn mark_healthy(store: &mut Store, state: &State) -> Result<()> {
    let mut auto = store.load_auto()?;
    if auto.boot != state.boot || auto.pending.is_empty() || auto.pending_since == 0 {
        return Ok(());
    }
    let booted = crate::utils::getprop("sys.boot_completed").as_deref() == Some("1");
    if !booted || platform::uptime()? < auto.pending_since.saturating_add(STABLE_SECONDS) {
        return Ok(());
    }
    auto.pending.clear();
    auto.healthy = true;
    auto.last_result = Some(format!("{} active", state.kind));
    store.save_auto(&auto)?;
    store.event("automatic session observed stable; interruption detection armed")
}

/// Fresh preflight for one kind against the current boot. Nothing is reused.
fn auto_preflight(kind: &str, config: &Config) -> Result<(Vec<Change>, Vec<platform::PropertyCheck>)> {
    match kind {
        "hide" => platform::property_plan(),
        "umount" => Ok((mount::batch_plan(&config.umount_paths)?, Vec::new())),
        other => anyhow::bail!("Unknown automatic kind: {other}"),
    }
}

/// Boot-stage entry point. Read-only unless the user enabled an automatic
/// option, and it never replays a token, property original or mount identity
/// from an earlier boot.
fn boot_run(stage: &str) -> Result<()> {
    let (mut store, _lock) = Store::open()?;
    let mut backend = platform::Real;
    let boot = platform::boot()?;
    let mut config = store.load_config()?;
    let mut auto = store.load_auto()?;

    // Observation first: this is what proves a boot event was received without
    // opening the manager. It changes no state and is skipped by the 5s monitor.
    if config.boot_probe && stage != "monitor" {
        // A failed observation must never block recovery or activation.
        if let Err(error) = probe::record(stage, &boot).and_then(|report| store.probe_line(&report))
        {
            let _ = store.event(&format!("boot probe failed: {error:#}"));
        }
    }

    // A record from an earlier boot only ever disables; it never re-enables.
    if auto.boot != boot {
        let interrupted = auto.interrupted();
        let mut interruption = None;
        if !interrupted.is_empty() {
            let mut disabled = Vec::new();
            for kind in interrupted {
                if config.disable_auto(&kind) {
                    disabled.push(kind);
                }
            }
            store.save_config(&config)?;
            interruption = Some(format!(
                "previous boot did not finish cleanly; disabled: {}",
                if disabled.is_empty() {
                    "none".to_owned()
                } else {
                    disabled.join(", ")
                }
            ));
            store.event(&format!(
                "{}; paths and log kept",
                interruption.as_deref().unwrap_or("interrupted boot")
            ))?;
        }
        auto = AutoState {
            boot: boot.clone(),
            interruption,
            ..AutoState::default()
        };
        store.save_auto(&auto)?;
    }

    let inspection = match inspect(&mut store, &mut backend, &boot) {
        Ok(inspection) => inspection,
        Err(error) => {
            // Reported through the audit log: this process runs detached, so an
            // error on stderr would be lost.
            let _ = store.event(&format!(
                "automatic activation skipped: inspection failed: {error:#}"
            ));
            return Ok(());
        }
    };
    if let Some(state) = &inspection.state
        && state.phase != "off"
    {
        // Never start a second session: that could overwrite a live recovery record.
        store.event(&format!(
            "automatic activation skipped: session {} is {}",
            state.kind, state.phase
        ))?;
        return Ok(());
    }
    if let Some(error) = &inspection.recovery_error {
        store.event(&format!("automatic activation skipped: {error}"))?;
        return Ok(());
    }
    if auto.attempted {
        return Ok(());
    }
    let kinds = config.enabled_kinds();
    if kinds.is_empty() {
        return Ok(());
    }
    // One attempt per boot, whatever the outcome; duplicate boot events stop here.
    auto.attempted = true;
    store.save_auto(&auto)?;

    let blocked = match (&inspection.safety_error, inspection.safe) {
        (Some(error), _) => Some(format!("safety status unavailable: {error}")),
        (None, true) => Some("safe mode".to_owned()),
        (None, false) => None,
    };
    if let Some(reason) = blocked {
        store.event(&format!("automatic activation blocked: {reason}"))?;
        return settle_auto(&mut store, &mut config, &mut auto, &kinds, &reason);
    }

    let mut planned = Vec::new();
    let mut changes = Vec::new();
    let mut checks = Vec::new();
    for kind in &kinds {
        match auto_preflight(kind, &config) {
            Ok((items, property_checks)) => {
                planned.push(kind.clone());
                changes.extend(items);
                checks.extend(property_checks);
            }
            Err(error) => {
                // The failing kind is dropped and disabled; the others still run.
                config.disable_auto(kind);
                auto.last_error = Some(format!("{kind}: {error:#}"));
                store.event(&format!("automatic {kind} preflight failed: {error:#}"))?;
            }
        }
    }
    store.save_config(&config)?;
    if planned.is_empty() {
        store.event("automatic activation: no kind passed preflight")?;
        return settle_auto(
            &mut store,
            &mut config,
            &mut auto,
            &kinds,
            "preflight failed",
        );
    }
    if changes.is_empty() {
        // Nothing was changed, so this boot finished cleanly: a later boot must
        // not report it as an interruption.
        auto.healthy = true;
        auto.last_result = Some(format!("nothing to do for {}", planned.join(", ")));
        store.save_auto(&auto)?;
        store.event(&format!(
            "automatic activation: already satisfied ({})",
            planned.join(", ")
        ))?;
        return Ok(());
    }

    // Pending is written *before* the first mutation so an abnormal reboot
    // during activation is detected on the next boot.
    auto.pending = planned.clone();
    auto.pending_since = 0;
    auto.healthy = false;
    store.save_auto(&auto)?;

    let mut state = State {
        token: platform::token()?,
        boot: boot.clone(),
        kind: planned.join("+"),
        phase: "queued".into(),
        changes,
        attempted: 0,
        deadline: 0,
        owner: None,
        error: None,
        notice: Some("automatic".into()),
        property_checks: checks,
        auto: true,
    };
    store.save(&state)?;
    store.event(&format!(
        "automatic session queued: {} ({} changes)",
        state.kind,
        state.changes.len()
    ))?;
    if let Err(error) = spawn_supervisor(&mut store, &mut state) {
        let reason = format!("supervisor did not start: {error:#}");
        store.event(&format!("automatic activation failed: {reason}"))?;
        settle_auto(&mut store, &mut config, &mut auto, &planned, &reason)?;
        return Err(error);
    }
    Ok(())
}

pub fn run(action: Action) -> Result<()> {
    ensure!(unsafe { libc::geteuid() } == 0, "Root is required");
    if let Action::Probe { target } = &action {
        return mount::probe(target);
    }
    if matches!(action, Action::Logs) {
        let (store, _lock) = Store::open()?;
        for name in ["audit.previous.log", "audit.log"] {
            let path = store.0.join(name);
            if path.exists() {
                print!("{}", String::from_utf8_lossy(&bounded_read(&path)?));
            }
        }
        return Ok(());
    }
    // Failure is fatal: never operate in an accidental namespace.
    crate::utils::switch_mnt_ns(1)?;
    ensure!(
        fs::read_link("/proc/thread-self/ns/mnt")? == fs::read_link("/proc/1/ns/mnt")?,
        "Wrong mount namespace"
    );
    if let Action::Watch { token } = action {
        return watch(&token);
    }
    if let Action::Boot { stage } = action {
        return boot_run(&stage);
    }
    let (mut store, _lock) = Store::open()?;
    let mut backend = platform::Real;
    let boot = platform::boot()?;
    let Inspection {
        mut state,
        safe,
        safety_error,
        recovery_error,
    } = inspect(&mut store, &mut backend, &boot)?;
    if let Some(error) = &recovery_error
        && !matches!(
            action,
            Action::Status | Action::Restore | Action::Configure { .. }
        )
    {
        // Status, an explicit recovery retry and saving preferences (which never
        // executes anything) must remain available.
        anyhow::bail!("{error}");
    }
    let result = (|| {
        match action {
            Action::Status => {
                let config = store.load_config()?;
                let auto = store.load_auto()?;
                println!(
                    "{}",
                    serde_json::json!({
                        "safe_mode": safe,
                        "safety_error": safety_error,
                        "state": state,
                        "config": config,
                        "auto": auto,
                    })
                );
            }
            Action::Logs => {
                let path = store.0.join("audit.log");
                if path.exists() {
                    print!("{}", String::from_utf8_lossy(&bounded_read(&path)?));
                }
            }
            Action::Preview { kind, source } => {
                ensure!(
                    !safe,
                    "Safe mode or unavailable safety check: feature disabled"
                );
                ensure!(
                    state
                        .as_ref()
                        .is_none_or(|s| matches!(s.phase.as_str(), "off" | "preview")),
                    "Restore the current session first"
                );
                // A failed replacement preview must not leave an older batch executable.
                if let Some(previous) = state.as_mut() {
                    if previous.phase == "preview" {
                        previous.phase = "off".into();
                        previous.notice = None;
                        store.save(previous)?;
                    }
                }
                let token = platform::token()?;
                let (changes, property_checks) = if kind == "hide" {
                    platform::property_plan()?
                } else {
                    (
                        mount::batch_plan(
                            source
                                .as_deref()
                                .context("Module resource paths required")?,
                        )?,
                        Vec::new(),
                    )
                };
                let notice = if kind == "hide" {
                    let missing = property_checks.iter().any(|c| c.current.is_none());
                    Some(
                        if missing {
                            "properties_unavailable"
                        } else if changes.is_empty() {
                            "already_matches"
                        } else {
                            "changes_ready"
                        }
                        .to_owned(),
                    )
                } else {
                    None
                };
                let phase = if changes.is_empty() { "off" } else { "preview" };
                let preview = State {
                    token,
                    boot,
                    kind,
                    phase: phase.into(),
                    changes,
                    attempted: 0,
                    deadline: platform::uptime()? + 120,
                    owner: None,
                    error: None,
                    notice,
                    property_checks,
                    auto: false,
                };
                store.save(&preview)?;
                store.event(&format!(
                    "preview {} {}: {} changes; {:?}; checks={:?}",
                    preview.kind,
                    preview.token,
                    preview.changes.len(),
                    preview.notice,
                    preview.property_checks
                ))?;
                println!("{}", serde_json::to_string(&preview)?);
            }
            Action::Configure {
                source,
                hide_auto,
                umount_auto,
                probe,
            } => {
                // Preferences only. Nothing here validates live mounts, starts a
                // trial or unmounts anything: the boot runner preflights later.
                let mut config = store.load_config()?;
                if let Some(source) = source {
                    if source.trim().is_empty() {
                        config.umount_paths.clear();
                    } else {
                        mount::source_lines(&source).context("Invalid resource list")?;
                        config.umount_paths = source;
                    }
                }
                for (value, flag) in [
                    (hide_auto, &mut config.hide_auto),
                    (umount_auto, &mut config.umount_auto),
                    (probe, &mut config.boot_probe),
                ] {
                    if let Some(value) = value {
                        *flag = value == "on";
                    }
                }
                if config.umount_auto {
                    ensure!(
                        !config.validated_umount_paths()?.is_empty(),
                        "Save at least one module resource path before enabling automatic unmount"
                    );
                }
                store.save_config(&config)?;
                // Re-enabling clears the earlier interruption notice, which stays
                // visible in the manager until the user acts on it.
                let mut auto = store.load_auto()?;
                let cleared = (!config.enabled_kinds().is_empty())
                    .then(|| auto.interruption.take())
                    .flatten();
                if cleared.is_some() {
                    store.save_auto(&auto)?;
                }
                store.event(&format!(
                    "configuration saved: paths={} hide_auto={} umount_auto={} probe={}",
                    config.validated_umount_paths().map(|p| p.len()).unwrap_or(0),
                    config.hide_auto,
                    config.umount_auto,
                    config.boot_probe
                ))?;
                if let Some(reason) = cleared {
                    store.event(&format!("automatic activation enabled again after: {reason}"))?;
                }
                println!("{}", serde_json::to_string(&config)?);
            }
            Action::Apply { token } => {
                ensure!(
                    !safe,
                    "Safe mode or unavailable safety check: feature disabled"
                );
                let mut state = state.context("Preview required")?;
                ensure!(
                    state.token == token
                        && state.phase == "preview"
                        && platform::uptime()? < state.deadline,
                    "Preview expired or changed; preview again"
                );
                for change in &state.changes {
                    backend.validate(change)?;
                }
                state.phase = "queued".into();
                spawn_supervisor(&mut store, &mut state)?;
            }
            Action::Confirm { token } => {
                ensure!(!safe, "Safe mode: confirmation blocked");
                let mut state = state.context("No trial")?;
                ensure!(
                    state.token == token
                        && state.phase == "trial"
                        && platform::uptime()? < state.deadline
                        && owner_alive(&state),
                    "Trial is no longer active"
                );
                state.phase = "active".into();
                store.event("user confirmed: keep only for current boot")?;
                store.save(&state)?;
            }
            Action::Restore => {
                if let Some(mut state) = state {
                    rollback(&mut state, &mut backend, &mut store)?;
                }
            }
            _ => unreachable!(),
        }
        Ok(())
    })();
    if let Err(error) = &result {
        let _ = store.event(&format!("request failed: {error:#}"));
    }
    result
}

fn watch(token: &str) -> Result<()> {
    let mut initialized = false;
    loop {
        // Poll a contended lock; no mutex is held while sleeping.
        if let Ok((mut store, _lock)) = Store::open() {
            let Some(mut state) = store.load()? else {
                return Ok(());
            };
            if state.token != token {
                return Ok(());
            }
            if state.phase == "off" || state.phase == "recovery_failed" {
                // An automatic session never restarts itself once it ended.
                if state.auto {
                    settle_after_session(&mut store, &state)?;
                }
                return Ok(());
            }
            let mut backend = platform::Real;
            let safe = backend.safe().unwrap_or(true);
            reconcile(
                &mut state,
                &mut backend,
                &mut store,
                &platform::boot()?,
                safe,
                platform::uptime()?,
            )?;
            if state.phase == "off" || state.phase == "recovery_failed" {
                if state.auto {
                    settle_after_session(&mut store, &state)?;
                }
                return Ok(());
            }
            if !initialized {
                ensure!(state.phase == "queued", "Unexpected supervisor state");
                if let Err(error) = apply(&mut state, &mut backend, &mut store) {
                    if state.auto {
                        settle_after_session(&mut store, &state)?;
                    }
                    return Err(error);
                }
                if state.auto {
                    // Start the per-boot stability window only now: the boot is
                    // only considered interrupted if it dies before this elapses.
                    let mut auto = store.load_auto()?;
                    if auto.boot == state.boot {
                        auto.pending_since = platform::uptime()?;
                        store.save_auto(&auto)?;
                        store.event("automatic session applied; stability window started")?;
                    }
                }
                initialized = true;
            } else if state.auto {
                mark_healthy(&mut store, &state)?;
            }
        }
        std::thread::sleep(Duration::from_secs(1));
    }
}

/// Boot stages only schedule a detached inspection of the current boot. The
/// child re-reads and re-preflights everything; it is never told to replay an
/// earlier decision, and enabling a feature is only ever done by the user.
pub fn boot_check(stage: &str) {
    let config = config::peek();
    let busy = Path::new(ROOT).join("state.json").exists();
    if !busy && !config.boot_probe && config.enabled_kinds().is_empty() {
        return;
    }
    let result = Command::new("/data/adb/apd")
        .args(["runtime-safety", "boot", stage])
        .process_group(0)
        .stdin(Stdio::null())
        .stdout(Stdio::null())
        .stderr(Stdio::null())
        .spawn();
    if let Err(error) = result {
        log::warn!("Cannot schedule runtime safety check: {error}");
    }
}

/// Independent recovery path if a trial supervisor dies. This monitor belongs
/// to the existing UID-listener process; it never enables a feature.
pub fn start_recovery_monitor() {
    std::thread::spawn(|| {
        loop {
            let path = Path::new(ROOT).join("state.json");
            if let Ok(bytes) = bounded_read(&path) {
                if let Ok(state) = serde_json::from_slice::<State>(&bytes) {
                    if matches!(
                        state.phase.as_str(),
                        "queued" | "applying" | "trial" | "active" | "restoring"
                    ) {
                        boot_check("monitor");
                    }
                }
            }
            std::thread::sleep(Duration::from_secs(5));
        }
    });
}
