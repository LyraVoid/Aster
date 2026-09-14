#[allow(unused_imports)]
use std::fs::{Permissions, set_permissions};
#[cfg(unix)]
use std::os::unix::prelude::PermissionsExt;
use std::{
    ffi::CString,
    fs::{File, OpenOptions, create_dir_all, metadata},
    io::{ErrorKind::AlreadyExists, Write},
    path::Path,
    process::{Command, Stdio},
};

use anyhow::{Context, Error, Ok, Result, bail};
use log::{info, warn};

use crate::{defs, supercall::sc_su_get_safemode};

pub fn ensure_file_exists<T: AsRef<Path>>(file: T) -> Result<()> {
    match File::options().write(true).create_new(true).open(&file) {
        Result::Ok(_) => Ok(()),
        Err(err) => {
            if err.kind() == AlreadyExists && file.as_ref().is_file() {
                Ok(())
            } else {
                Err(Error::from(err))
                    .with_context(|| format!("{} is not a regular file", file.as_ref().display()))
            }
        }
    }
}

pub fn ensure_dir_exists<T: AsRef<Path>>(dir: T) -> Result<()> {
    let result = create_dir_all(&dir).map_err(Error::from);
    if dir.as_ref().is_dir() {
        result
    } else if result.is_ok() {
        bail!("{} is not a regular directory", dir.as_ref().display())
    } else {
        result
    }
}

// todo: ensure
pub fn ensure_binary<T: AsRef<Path>>(path: T) -> Result<()> {
    set_permissions(&path, Permissions::from_mode(0o755))?;
    Ok(())
}

// Android API 26 callback supports long read-only properties without guessing
// a buffer size or transferring ownership of Bionic memory to Rust.
#[cfg(target_os = "android")]
pub fn getprop(prop: &str) -> Option<String> {
    unsafe extern "C" {
        fn __system_property_find(name: *const libc::c_char) -> *const libc::c_void;

    }
    unsafe extern "C" fn receive(
        cookie: *mut libc::c_void,
        _: *const libc::c_char,
        value: *const libc::c_char,
        _: u32,
    ) {
        if !value.is_null() {
            let result = unsafe { &mut *cookie.cast::<Option<String>>() };
            *result = unsafe { std::ffi::CStr::from_ptr(value) }
                .to_str()
                .ok()
                .map(str::to_owned);
        }
    }
    type ReadCallback = unsafe extern "C" fn(
        *const libc::c_void,
        unsafe extern "C" fn(*mut libc::c_void, *const libc::c_char, *const libc::c_char, u32),
        *mut libc::c_void,
    );
    // cargo-ndk may link against API 21; resolve the API 26 function at runtime.
    // The app requires API 26. If absent, fail closed instead of using unsafe legacy code.
    static READER: std::sync::OnceLock<Option<ReadCallback>> = std::sync::OnceLock::new();
    let reader = READER
        .get_or_init(|| unsafe {
            let symbol = libc::dlsym(
                libc::RTLD_DEFAULT,
                c"__system_property_read_callback".as_ptr(),
            );
            if symbol.is_null() {
                None
            } else {
                Some(std::mem::transmute::<*mut libc::c_void, ReadCallback>(
                    symbol,
                ))
            }
        })
        .as_ref()?;
    let key = CString::new(prop).ok()?;
    let info = unsafe { __system_property_find(key.as_ptr()) };
    if info.is_null() {
        return None;
    }
    let mut result: Option<String> = None;
    unsafe {
        reader(info, receive, (&mut result as *mut Option<String>).cast());
    }
    result
}

#[cfg(not(target_os = "android"))]
pub fn getprop(_prop: &str) -> Option<String> {
    None
}

/// Prepare a child to survive an init exec-service ending. Only async-signal-safe
/// calls run after fork; no allocator or property reader is used in pre_exec.
pub fn background_command(command: &mut Command) {
    use std::os::unix::process::CommandExt;
    unsafe {
        command.pre_exec(|| {
            if libc::setsid() < 0 {
                return Err(std::io::Error::last_os_error());
            }
            let pid = libc::getpid() as u32;
            let mut digits = [0u8; 16];
            let mut offset = digits.len();
            let mut value = pid;
            loop {
                offset -= 1;
                digits[offset] = b'0' + (value % 10) as u8;
                value /= 10;
                if value == 0 {
                    break;
                }
            }
            for path in [
                c"/acct/cgroup.procs",
                c"/dev/cg2_bpf/cgroup.procs",
                c"/sys/fs/cgroup/cgroup.procs",
                c"/dev/memcg/apps/cgroup.procs",
            ] {
                let fd = libc::open(path.as_ptr(), libc::O_WRONLY | libc::O_CLOEXEC);
                if fd < 0 {
                    continue;
                }
                let count =
                    libc::write(fd, digits[offset..].as_ptr().cast(), digits.len() - offset);
                let error = std::io::Error::last_os_error();
                libc::close(fd);
                if count != (digits.len() - offset) as isize {
                    return Err(error);
                }
            }
            std::result::Result::Ok(())
        });
    }
}

pub fn run_command(
    command: &str,
    args: &[&str],
    stdout: Option<Stdio>,
) -> Result<std::process::Child> {
    let mut command_builder = Command::new(command);
    command_builder.args(args);
    if let Some(out) = stdout {
        command_builder.stdout(out);
    }
    let child = command_builder.spawn()?;
    Ok(child)
}
pub fn is_safe_mode(superkey: Option<String>) -> bool {
    let safemode = getprop("persist.sys.safemode")
        .filter(|prop| prop == "1")
        .is_some()
        || getprop("ro.sys.safemode")
            .filter(|prop| prop == "1")
            .is_some();
    info!("safemode: {}", safemode);
    if safemode {
        return true;
    }
    let safemode = superkey
        .as_ref()
        .and_then(|key_str| CString::new(key_str.as_str()).ok())
        .map_or_else(
            || {
                warn!("[is_safe_mode] No valid superkey provided, assuming safemode as false.");
                false
            },
            |cstr| sc_su_get_safemode(&cstr) == 1,
        );
    info!("kernel_safemode: {}", safemode);
    safemode
}

#[cfg(any(target_os = "linux", target_os = "android"))]
pub fn switch_mnt_ns(pid: i32) -> Result<()> {
    use std::os::fd::AsRawFd;

    use anyhow::ensure;
    let path = format!("/proc/{pid}/ns/mnt");
    let fd = File::open(path)?;
    let current_dir = std::env::current_dir();
    let ret = unsafe { libc::setns(fd.as_raw_fd(), libc::CLONE_NEWNS) };
    if let Result::Ok(current_dir) = current_dir {
        let _ = std::env::set_current_dir(current_dir);
    }
    ensure!(ret == 0, "switch mnt ns failed");
    Ok(())
}

fn switch_cgroup(grp: &str, pid: u32) {
    let path = Path::new(grp).join("cgroup.procs");
    if !path.exists() {
        return;
    }

    let fp = OpenOptions::new().append(true).open(path);
    if let Result::Ok(mut fp) = fp {
        let _ = write!(fp, "{pid}");
    }
}

pub fn switch_cgroups() {
    let pid = std::process::id();
    switch_cgroup("/acct", pid);
    switch_cgroup("/dev/cg2_bpf", pid);
    switch_cgroup("/sys/fs/cgroup", pid);

    if getprop("ro.config.per_app_memcg")
        .filter(|prop| prop == "false")
        .is_none()
    {
        switch_cgroup("/dev/memcg/apps", pid);
    }
}

/// Detach the current process into a background daemon so it survives the
/// framework being torn down around it (e.g. `stop` during a soft reboot).
/// Redirects stdin/stdout/stderr to /dev/null and double-forks out of the
/// caller's process group / cgroup.
#[cfg(any(target_os = "linux", target_os = "android"))]
pub fn daemonize() -> Result<()> {
    use std::os::fd::AsRawFd;

    let pid = unsafe { libc::fork() };
    if pid < 0 {
        bail!("fork error: {}", std::io::Error::last_os_error());
    }
    if pid > 0 {
        // Parent: wait for the child, then exit so the caller sees success.
        let mut status: i32 = 0;
        loop {
            if unsafe { libc::waitpid(pid, &mut status, 0) } < 0 {
                if std::io::Error::last_os_error().raw_os_error() != Some(libc::EINTR) {
                    std::process::exit(1);
                }
            } else {
                break;
            }
        }
        std::process::exit(0);
    }

    unsafe { libc::setsid() };
    switch_cgroups();

    if let Result::Ok(null) = File::open("/dev/null") {
        let fd = null.as_raw_fd();
        unsafe {
            libc::dup2(fd, 0);
            libc::dup2(fd, 1);
            libc::dup2(fd, 2);
        }
    }

    let pid = unsafe { libc::fork() };
    if pid < 0 {
        bail!("fork error: {}", std::io::Error::last_os_error());
    }
    if pid > 0 {
        unsafe { libc::_exit(0) };
    }

    Ok(())
}

#[cfg(not(any(target_os = "linux", target_os = "android")))]
pub fn daemonize() -> Result<()> {
    Ok(())
}

#[cfg(any(target_os = "linux", target_os = "android"))]
pub fn umask(mask: u32) {
    unsafe { libc::umask(mask) };
}

#[cfg(not(any(target_os = "linux", target_os = "android")))]
pub fn umask(_mask: u32) {
    unimplemented!("umask is not supported on this platform")
}

pub fn has_magisk() -> bool {
    which::which("magisk").is_ok()
}
pub fn get_tmp_path() -> &'static str {
    if metadata(defs::TEMP_DIR_LEGACY).is_ok() {
        return defs::TEMP_DIR_LEGACY;
    }
    if metadata(defs::TEMP_DIR).is_ok() {
        return defs::TEMP_DIR;
    }
    ""
}

pub fn get_magic_mount_work_dir() -> String {
    format!("{}/workdir/", get_tmp_path())
}

/// A consumer may close its pipe before the complete module list is written.
pub fn write_stdout_line(line: &str) -> Result<()> {
    write_output_line(&mut std::io::stdout().lock(), line).map_err(Error::from)
}

fn write_output_line(writer: &mut impl Write, line: &str) -> std::io::Result<()> {
    match writeln!(writer, "{line}") {
        Err(err) if err.kind() == std::io::ErrorKind::BrokenPipe => std::result::Result::Ok(()),
        result => result,
    }
}

#[cfg(test)]
mod output_tests {
    use super::*;
    #[test]
    fn writes_complete_line() {
        let mut output = Vec::new();
        write_output_line(&mut output, "[]").unwrap();
        assert_eq!(output, b"[]\n");
    }
    struct FailedWriter(std::io::ErrorKind);
    impl Write for FailedWriter {
        fn write(&mut self, _: &[u8]) -> std::io::Result<usize> {
            Err(self.0.into())
        }
        fn flush(&mut self) -> std::io::Result<()> {
            std::result::Result::Ok(())
        }
    }
    #[test]
    fn suppresses_only_broken_pipe() {
        use std::io::ErrorKind;
        assert!(write_output_line(&mut FailedWriter(ErrorKind::BrokenPipe), "[]").is_ok());
        assert_eq!(
            write_output_line(&mut FailedWriter(ErrorKind::PermissionDenied), "[]")
                .unwrap_err()
                .kind(),
            ErrorKind::PermissionDenied
        );
    }
}

#[cfg(all(test, target_os = "android"))]
mod property_read_tests {
    #[test]
    fn bionic_properties_match_getprop_repeatedly_without_writes() {
        let keys = [
            "ro.boot.veritymode",
            "ro.build.fingerprint",
            "ro.product.model",
            "ro.build.version.incremental",
            "sys.boot_completed",
        ];
        let mut saw_long_value = false;
        for key in keys {
            let output = std::process::Command::new("/system/bin/getprop")
                .arg(key)
                .output()
                .unwrap();
            assert!(output.status.success());
            let expected = String::from_utf8(output.stdout).unwrap();
            let expected = expected.trim_end_matches('\n');
            if expected.is_empty() {
                continue;
            }
            saw_long_value |= expected.len() >= 8;
            for _ in 0..200 {
                assert_eq!(
                    super::getprop(key).as_deref(),
                    Some(expected),
                    "property read differs"
                );
            }
        }
        assert!(
            saw_long_value,
            "test requires a property of at least eight bytes"
        );
        assert!(super::getprop("aster.nonexistent.property.for.test").is_none());
        assert!(super::getprop("invalid\0key").is_none());
    }
}
