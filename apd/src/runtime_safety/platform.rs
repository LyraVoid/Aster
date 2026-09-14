use super::{Backend, Change};
use anyhow::{Context, Result, ensure};
use std::{
    fs::{self, OpenOptions},
    io::Read,
    os::unix::{fs::OpenOptionsExt, process::CommandExt},
    path::Path,
    process::{Command, Stdio},
    time::{Duration, Instant},
};

// Deliberately exclude ADB, debuggable, bootmode, persist.* and log properties.
pub const PROPERTIES: &[(&str, &str)] = &[
    ("ro.boot.vbmeta.device_state", "locked"),
    ("ro.boot.verifiedbootstate", "green"),
    ("ro.boot.flash.locked", "1"),
    ("ro.boot.veritymode", "enforcing"),
];

pub fn boot() -> Result<String> {
    Ok(fs::read_to_string("/proc/sys/kernel/random/boot_id")?
        .trim()
        .into())
}
pub fn uptime() -> Result<u64> {
    let raw = fs::read_to_string("/proc/uptime")?;
    Ok(raw.split('.').next().context("Invalid uptime")?.parse()?)
}
pub fn token() -> Result<String> {
    let mut bytes = [0u8; 16];
    fs::File::open("/dev/urandom")?.read_exact(&mut bytes)?;
    Ok(bytes.iter().map(|b| format!("{b:02x}")).collect())
}
pub fn process_start(pid: u32) -> Option<String> {
    let raw = fs::read_to_string(format!("/proc/{pid}/stat")).ok()?;
    // Field 22, with field 3 immediately after the closing ')' of comm.
    raw.rsplit_once(") ")?
        .1
        .split_whitespace()
        .nth(19)
        .map(str::to_owned)
}

pub fn command(args: &[&str]) -> Result<String> {
    let path = Path::new(super::ROOT).join(format!("command-{}.tmp", std::process::id()));
    let output = OpenOptions::new()
        .create(true)
        .truncate(true)
        .write(true)
        .read(true)
        .mode(0o600)
        .custom_flags(libc::O_NOFOLLOW)
        .open(&path)?;
    let result = (|| {
        let mut child = Command::new(std::env::current_exe()?)
            .args(args)
            .process_group(0)
            .stdin(Stdio::null())
            .stdout(output.try_clone()?)
            .stderr(Stdio::null())
            .spawn()?;
        let started = Instant::now();
        loop {
            if let Some(status) = child.try_wait()? {
                ensure!(status.success(), "Subcommand failed: {args:?} ({status})");
                let mut bytes = Vec::new();
                fs::File::open(&path)?.take(8193).read_to_end(&mut bytes)?;
                ensure!(bytes.len() <= 8192, "Subcommand output too large");
                return Ok(String::from_utf8(bytes)?);
            }
            if started.elapsed() >= Duration::from_secs(3) {
                unsafe {
                    libc::kill(-(child.id() as i32), libc::SIGKILL);
                }
                // No blocking wait: even a kernel D-state task cannot block the controller.
                let _ = child.try_wait();
                anyhow::bail!("Subcommand timed out: {args:?}");
            }
            std::thread::sleep(Duration::from_millis(20));
        }
    })();
    let _ = fs::remove_file(path);
    result
}
fn get(key: &str) -> Result<String> {
    crate::utils::getprop(key).context("Property is missing or unreadable")
}
fn set(key: &str, value: &str) -> Result<()> {
    command(&["resetprop", "-n", key, value])?;
    ensure!(get(key)? == value, "Property readback failed: {key}");
    Ok(())
}
#[derive(Debug, Clone, serde::Serialize, serde::Deserialize)]
pub struct PropertyCheck {
    pub key: String,
    pub current: Option<String>,
    pub target: String,
}

pub fn property_plan() -> Result<(Vec<Change>, Vec<PropertyCheck>)> {
    inspect_properties(crate::utils::getprop)
}

fn inspect_properties(
    mut read: impl FnMut(&str) -> Option<String>,
) -> Result<(Vec<Change>, Vec<PropertyCheck>)> {
    let mut changes = Vec::new();
    let mut checks = Vec::new();
    for &(key, after) in PROPERTIES {
        let before = read(key).filter(|value| !value.is_empty());
        if let Some(before) = &before {
            ensure!(
                before.len() <= 128 && !before.contains('\0'),
                "Invalid property value"
            );
            if before != after {
                changes.push(Change::Property {
                    key: key.into(),
                    before: before.clone(),
                    after: after.into(),
                });
            }
        }
        checks.push(PropertyCheck {
            key: key.into(),
            current: before,
            target: after.into(),
        });
    }
    Ok((changes, checks))
}

#[cfg(test)]
mod inspection_tests {
    use super::*;
    #[test]
    fn already_matching_is_not_an_error_or_an_owned_change() {
        let (changes, checks) = inspect_properties(|key| {
            PROPERTIES
                .iter()
                .find(|(k, _)| *k == key)
                .map(|(_, v)| v.to_string())
        })
        .unwrap();
        assert!(changes.is_empty());
        assert!(checks.iter().all(|c| c.current.is_some()));
    }
    #[test]
    fn missing_properties_are_distinct_from_matching_values() {
        let (changes, checks) = inspect_properties(|_| None).unwrap();
        assert!(changes.is_empty());
        assert!(checks.iter().all(|c| c.current.is_none()));
        let (changes, checks) = inspect_properties(|key| {
            if key == PROPERTIES[0].0 {
                Some("unlocked".into())
            } else {
                None
            }
        })
        .unwrap();
        assert_eq!(changes.len(), 1);
        assert_eq!(checks.iter().filter(|c| c.current.is_none()).count(), 3);
    }
}

pub struct Real;
impl Backend for Real {
    fn safe(&mut self) -> Result<bool> {
        if ["persist.sys.safemode", "ro.sys.safemode"]
            .iter()
            .any(|key| crate::utils::getprop(key).as_deref() == Some("1"))
        {
            return Ok(true);
        }
        let result = crate::supercall::sc_su_get_safemode(c"su");
        ensure!(
            result == 0 || result == 1,
            "Kernel safety status unavailable: {result}"
        );
        Ok(result == 1)
    }
    fn validate(&mut self, change: &Change) -> Result<()> {
        match change {
            Change::Property { key, before, after } => {
                ensure!(
                    PROPERTIES.contains(&(key.as_str(), after.as_str())),
                    "Property outside allowlist"
                );
                ensure!(
                    get(key)? == *before,
                    "Property changed since preview: {key}"
                );
                Ok(())
            }
            Change::Mount(plan) => super::mount::validate(plan),
        }
    }
    fn apply(&mut self, change: &Change) -> Result<()> {
        match change {
            Change::Property { key, after, .. } => set(key, after),
            Change::Mount(plan) => super::mount::apply(plan),
        }
    }
    fn restore(&mut self, change: &Change) -> Result<()> {
        match change {
            Change::Property { key, before, after } => {
                ensure!(
                    PROPERTIES.contains(&(key.as_str(), after.as_str())),
                    "Invalid saved property"
                );
                let current = get(key)?;
                if current == *before {
                    return Ok(());
                }
                ensure!(
                    current == *after,
                    "Property changed externally; original retained for manual recovery: {key}"
                );
                set(key, before)
            }
            Change::Mount(plan) => super::mount::restore(plan),
        }
    }
}
