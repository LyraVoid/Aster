//! Read-only boot probe.
//!
//! Records what the boot-stage process actually observed, so a boot event can be
//! confirmed without opening the manager and without changing any state. The
//! only write is one appended line in the probe log; every other value is read
//! from `/proc` and `/sys`. It never opens the runtime-safety store lock, so it
//! can never block or disturb a running session.

use super::platform;
use anyhow::Result;
use serde_json::{Value, json};
use std::{
    fs,
    path::{Path, PathBuf},
};

/// Where the magic-mount modules live, used to count module-backed mounts
/// without following or touching them.
const MODULES: &str = crate::defs::MODULE_DIR;

fn read(path: impl AsRef<Path>) -> Option<String> {
    fs::read_to_string(path)
        .ok()
        .map(|value| value.trim().to_owned())
}

fn link(path: impl AsRef<Path>) -> Option<String> {
    fs::read_link(path)
        .ok()
        .map(|value| value.to_string_lossy().into_owned())
}

/// Number of modules that would be considered enabled for this boot.
fn module_count() -> usize {
    fs::read_dir(MODULES)
        .map(|entries| {
            entries
                .flatten()
                .filter(|entry| entry.path().is_dir())
                .filter(|entry| !entry.path().join(crate::defs::DISABLE_FILE_NAME).exists())
                .count()
        })
        .unwrap_or(0)
}

/// Mounts whose source lives under the module directory. This is the observation
/// that distinguishes "magic mount ran" from "magic mount was skipped".
fn module_mounts() -> usize {
    read("/proc/self/mountinfo")
        .map(|info| {
            info.lines()
                .filter(|line| line.split(' ').any(|field| field.starts_with(MODULES)))
                .count()
        })
        .unwrap_or(0)
}

fn safemode() -> Value {
    let props: Vec<Value> = ["persist.sys.safemode", "ro.sys.safemode"]
        .into_iter()
        .map(|key| json!({"key": key, "value": crate::utils::getprop(key)}))
        .collect();
    // Raw kernel answer, unvalidated: the probe reports, it does not decide.
    let kernel = crate::supercall::sc_su_get_safemode(c"su");
    json!({"properties": props, "kernel_supercall": kernel})
}

fn adb() -> Value {
    let props: Vec<Value> = ["service.adb.root", "service.adb.tcp.port"]
        .into_iter()
        .map(|key| json!({"key": key, "value": crate::utils::getprop(key)}))
        .collect();
    json!({"properties": props})
}

/// Build one single-line JSON record for `stage` on `boot`.
pub fn record(stage: &str, boot: &str) -> Result<String> {
    let self_ns = link("/proc/self/ns/mnt");
    let init_ns = link("/proc/1/ns/mnt");
    let report = json!({
        "uptime": platform::uptime()?,
        "stage": stage,
        "boot_id": boot,
        "pid": std::process::id(),
        "ppid": std::os::unix::process::parent_id(),
        "euid": unsafe { libc::geteuid() },
        "egid": unsafe { libc::getegid() },
        "selinux_context": read("/proc/self/attr/current"),
        "selinux_enforce": read("/sys/fs/selinux/enforce"),
        "init_context": read("/proc/1/attr/current"),
        "mnt_ns_self": self_ns,
        "mnt_ns_init": init_ns,
        "mnt_ns_matches_init": self_ns.is_some() && self_ns == init_ns,
        "jailbreak_mode": PathBuf::from(crate::defs::ADB_DIR).join("jailbreak").exists(),
        "kernelpatch_module": Path::new("/sys/module/kernelpatch").exists(),
        "kernelpatch_version": read("/sys/module/kernelpatch/version"),
        "magic_mount_enabled": Path::new(crate::defs::MAGIC_MOUNT_FILE).exists(),
        "modules_enabled": module_count(),
        "module_mounts": module_mounts(),
        "safemode": safemode(),
        "adb": adb(),
        "safety_dir": super::ROOT,
    });
    Ok(serde_json::to_string(&report)?)
}
