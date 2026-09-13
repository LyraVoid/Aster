//! Conservative, current-boot-only operations. No custom KernelPatch interfaces.
mod mount;
mod platform;
#[cfg(test)]
mod tests;

use anyhow::{Context, Result, ensure};
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
        state.phase = "trial".into();
        state.deadline = platform::uptime()? + TRIAL_SECONDS;
        journal.save(state)?;
        journal.event("trial started; auto-restore in 60 seconds")
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
        let temp = self.0.join("state.tmp");
        let mut file = OpenOptions::new()
            .write(true)
            .create(true)
            .truncate(true)
            .mode(0o600)
            .custom_flags(libc::O_NOFOLLOW)
            .open(&temp)?;
        file.write_all(&serde_json::to_vec_pretty(state)?)?;
        file.sync_all()?;
        fs::rename(temp, self.0.join("state.json"))?;
        File::open(&self.0)?.sync_all()?;
        Ok(())
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
        state.phase = "off".into();
        state.attempted = 0;
        state.owner = None;
        state.error = Some("Previous boot ended; feature remains off".into());
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
    let (mut store, _lock) = Store::open()?;
    let mut backend = platform::Real;
    let boot = platform::boot()?;
    // Unknown safety status blocks new actions and triggers conservative recovery.
    let safety = backend.safe();
    let safe = safety.as_ref().copied().unwrap_or(true);
    let mut state = store.load()?;
    if let Some(state) = &mut state {
        if let Err(error) = reconcile(
            state,
            &mut backend,
            &mut store,
            &boot,
            safe,
            platform::uptime()?,
        ) {
            state.error = Some(format!("Recovery incomplete: {error:#}"));
            let _ = store.event(&format!("recovery requires attention: {error:#}"));
            // Status/logs and an explicit recovery retry must remain available.
            if !matches!(action, Action::Status | Action::Restore) {
                return Err(error);
            }
        }
    }
    let result = (|| {
        match action {
            Action::Status => {
                println!(
                    "{}",
                    serde_json::json!({"safe_mode":safe,"safety_error":safety.err().map(|e|e.to_string()),"state":state})
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
                let token = platform::token()?;
                let changes = if kind == "hide" {
                    platform::property_plan()?
                } else {
                    vec![Change::Mount(mount::plan(
                        source.as_deref().context("Module resource path required")?,
                        &token,
                    )?)]
                };
                ensure!(!changes.is_empty(), "No changes required");
                let preview = State {
                    token,
                    boot,
                    kind,
                    phase: "preview".into(),
                    changes,
                    attempted: 0,
                    deadline: platform::uptime()? + 120,
                    owner: None,
                    error: None,
                };
                store.save(&preview)?;
                store.event(&format!("preview {} {}", preview.kind, preview.token))?;
                println!("{}", serde_json::to_string(&preview)?);
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
                // Child cannot acquire the store lock until owner identity is saved.
                let mut child = Command::new(std::env::current_exe()?)
                    .args(["runtime-safety", "watch", &token])
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
                    store.save(&state)?;
                    store.event(&format!("supervisor started for {}", state.token))
                })();
                if let Err(error) = started {
                    let _ = child.kill();
                    let _ = child.wait();
                    return Err(error);
                }
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
            if state.token != token || state.phase == "off" || state.phase == "recovery_failed" {
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
            if state.phase == "off" {
                return Ok(());
            }
            if !initialized {
                ensure!(state.phase == "queued", "Unexpected supervisor state");
                apply(&mut state, &mut backend, &mut store)?;
                initialized = true;
            }
        }
        std::thread::sleep(Duration::from_secs(1));
    }
}

/// Boot stages only schedule inspection/recovery, never enable either feature.
pub fn boot_check() {
    if Path::new(ROOT).join("state.json").exists() {
        let result = Command::new("/data/adb/apd")
            .args(["runtime-safety", "status"])
            .process_group(0)
            .stdin(Stdio::null())
            .stdout(Stdio::null())
            .stderr(Stdio::null())
            .spawn();
        if let Err(error) = result {
            log::warn!("Cannot schedule runtime safety recovery: {error}");
        }
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
                        boot_check();
                    }
                }
            }
            std::thread::sleep(Duration::from_secs(5));
        }
    });
}
