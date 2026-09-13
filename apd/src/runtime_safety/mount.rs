use anyhow::{Context, Result, ensure};
use serde::{Deserialize, Serialize};
use std::{ffi::CString, fs, os::unix::fs::MetadataExt, path::Path};

#[derive(Debug, Clone, Serialize, Deserialize, PartialEq, Eq)]
pub struct Identity {
    dev: u64,
    ino: u64,
    size: u64,
}
#[derive(Debug, Clone, Serialize, Deserialize, PartialEq, Eq)]
pub struct Plan {
    pub source: String,
    pub target: String,
    pub backup: String,
    pub anchor: String,
    pub mount_line: String,
    pub original: Identity,
    pub underlying: Identity,
}
#[derive(Debug)]
struct Entry {
    id: u64,
    parent: u64,
    path: String,
    options: Vec<String>,
    optional: Vec<String>,
    line: String,
}
fn decode(value: &str) -> Result<String> {
    let mut bytes = Vec::new();
    let mut raw = value.as_bytes().iter().copied();
    while let Some(ch) = raw.next() {
        if ch == b'\\' {
            let digits: Vec<_> = raw.by_ref().take(3).collect();
            ensure!(
                digits.len() == 3 && digits.iter().all(|c| (b'0'..=b'7').contains(c)),
                "Invalid mount escape"
            );
            let decoded = (digits[0] - b'0') as u16 * 64
                + (digits[1] - b'0') as u16 * 8
                + (digits[2] - b'0') as u16;
            ensure!(decoded > 0 && decoded <= 255, "Invalid mount escape");
            bytes.push(decoded as u8);
        } else {
            bytes.push(ch);
        }
    }
    Ok(String::from_utf8(bytes)?)
}
fn parse(text: &str) -> Result<Vec<Entry>> {
    text.lines()
        .map(|line| {
            let (left, _) = line.split_once(" - ").context("Invalid mountinfo")?;
            let fields: Vec<_> = left.split_whitespace().collect();
            ensure!(fields.len() >= 6, "Invalid mountinfo fields");
            Ok(Entry {
                id: fields[0].parse()?,
                parent: fields[1].parse()?,
                path: decode(fields[4])?,
                options: fields[5].split(',').map(str::to_owned).collect(),
                optional: fields[6..].iter().map(|s| (*s).into()).collect(),
                line: line.into(),
            })
        })
        .collect()
}
fn entries() -> Result<Vec<Entry>> {
    parse(&fs::read_to_string("/proc/thread-self/mountinfo")?)
}
fn private(entry: &Entry) -> bool {
    !entry.optional.iter().any(|field| {
        field.starts_with("shared:")
            || field.starts_with("master:")
            || field.starts_with("propagate_from:")
    })
}
fn exact<'a>(entries: &'a [Entry], target: &str) -> Result<&'a Entry> {
    let found: Vec<_> = entries
        .iter()
        .filter(|entry| entry.path == target)
        .collect();
    ensure!(
        found.len() == 1,
        "Expected one mount, refusing absent/stacked mount: {target}"
    );
    Ok(found[0])
}
fn saved_flags(entry: &Entry) -> Result<libc::c_ulong> {
    let mut flags = 0;
    for option in &entry.options {
        flags |= match option.as_str() {
            "ro" => libc::MS_RDONLY,
            "nosuid" => libc::MS_NOSUID,
            "nodev" => libc::MS_NODEV,
            "noexec" => libc::MS_NOEXEC,
            "noatime" => libc::MS_NOATIME,
            "nodiratime" => libc::MS_NODIRATIME,
            "relatime" => libc::MS_RELATIME,
            "strictatime" => libc::MS_STRICTATIME,
            _ => anyhow::bail!("Unsupported mount option: {option}"),
        }
    }
    Ok(flags)
}

fn eligible(entries: &[Entry], target: &str) -> Result<String> {
    let entry = exact(entries, target)?;
    saved_flags(entry)?;
    ensure!(
        entry.options.iter().any(|s| s == "ro"),
        "Only read-only file mounts are supported"
    );
    ensure!(private(entry), "Shared/slave mounts are not supported");
    let parent = entries
        .iter()
        .find(|e| e.id == entry.parent)
        .context("Missing parent mount")?;
    ensure!(
        private(parent),
        "Shared/slave parent mounts are not supported"
    );
    Ok(entry.line.clone())
}
fn identity(path: &str) -> Result<Identity> {
    let meta = fs::symlink_metadata(path)?;
    ensure!(
        meta.is_file() && meta.len() > 0,
        "Expected a nonempty regular file: {path}"
    );
    ensure!(
        Path::new(path).canonicalize()? == Path::new(path),
        "Symlinks are not supported: {path}"
    );
    Ok(Identity {
        dev: meta.dev(),
        ino: meta.ino(),
        size: meta.len(),
    })
}
fn resource(target: &str) -> bool {
    [
        "/system/media/",
        "/product/media/",
        "/vendor/media/",
        "/system_ext/media/",
        "/odm/media/",
        "/system/fonts/",
        "/product/fonts/",
    ]
    .iter()
    .any(|prefix| target.starts_with(prefix) && target.len() > prefix.len())
        && !target
            .split('/')
            .skip(1)
            .any(|c| c.is_empty() || c == "." || c == "..")
        && !target.chars().any(|c| c.is_control() || c == '\\')
}
pub fn plan(source: &str, token: &str) -> Result<Plan> {
    ensure!(
        source.len() <= 1024 && !source.starts_with('/'),
        "Use module-id/partition/resource path"
    );
    let (module, relative) = source
        .split_once('/')
        .context("Module resource path required")?;
    ensure!(
        !module.is_empty()
            && module
                .bytes()
                .all(|b| b.is_ascii_alphanumeric() || b"._-".contains(&b))
            && module != "."
            && module != "..",
        "Invalid module ID"
    );
    let target = format!("/{relative}");
    ensure!(
        resource(&target),
        "Only module media/font resource files are supported; critical paths are blocked"
    );
    let source = format!("/data/adb/modules/{source}");
    let original = identity(&source)?;
    ensure!(
        identity(&target)? == original,
        "Mount is not backed by this module file"
    );
    let mount_line = eligible(&entries()?, &target)?;
    let backup = format!("{}/mounts/{token}", super::ROOT);
    let underlying: Identity = serde_json::from_str(&super::platform::command(&[
        "runtime-safety",
        "probe",
        &target,
    ])?)?;
    ensure!(
        underlying != original,
        "No distinct underlying file to expose"
    );
    Ok(Plan {
        source,
        target,
        anchor: format!("{backup}.source"),
        backup,
        mount_line,
        original,
        underlying,
    })
}
fn paths(plan: &Plan) -> Result<()> {
    ensure!(
        resource(&plan.target)
            && plan.source.starts_with("/data/adb/modules/")
            && plan.backup.starts_with(&format!("{}/mounts/", super::ROOT)),
        "Invalid saved mount plan"
    );
    ensure!(
        plan.backup
            .rsplit('/')
            .next()
            .is_some_and(|s| s.len() == 32 && s.bytes().all(|b| b.is_ascii_hexdigit())),
        "Invalid backup name"
    );
    ensure!(
        plan.anchor == format!("{}.source", plan.backup),
        "Invalid recovery source"
    );
    Ok(())
}
pub fn validate(plan: &Plan) -> Result<()> {
    paths(plan)?;
    ensure!(
        identity(&plan.source)? == plan.original && identity(&plan.target)? == plan.original,
        "Module resource changed since preview"
    );
    ensure!(
        eligible(&entries()?, &plan.target)? == plan.mount_line,
        "Mount changed since preview"
    );
    let underlying: Identity = serde_json::from_str(&super::platform::command(&[
        "runtime-safety",
        "probe",
        &plan.target,
    ])?)?;
    ensure!(
        underlying == plan.underlying,
        "Underlying resource changed since preview"
    );
    Ok(())
}
fn mount(source: Option<&str>, target: &str, flags: libc::c_ulong) -> Result<()> {
    let source = source.map(CString::new).transpose()?;
    let target_c = CString::new(target)?;
    let rc = unsafe {
        libc::mount(
            source.as_ref().map_or(std::ptr::null(), |s| s.as_ptr()),
            target_c.as_ptr(),
            std::ptr::null(),
            flags,
            std::ptr::null(),
        )
    };
    if rc != 0 {
        return Err(std::io::Error::last_os_error())
            .context(format!("Mount operation failed: {target}"));
    }
    Ok(())
}
fn unmount(target: &str) -> Result<()> {
    let path = CString::new(target)?;
    // Never MNT_DETACH: busy mounts are rejected instead of force-detached.
    if unsafe { libc::umount2(path.as_ptr(), 0) } != 0 {
        return Err(std::io::Error::last_os_error()).context(format!("Unmount failed: {target}"));
    }
    Ok(())
}
pub fn probe(target: &str) -> Result<()> {
    ensure!(resource(target), "Critical path blocked");
    ensure!(
        unsafe { libc::unshare(libc::CLONE_NEWNS) } == 0,
        "Cannot isolate preview namespace"
    );
    mount(None, "/", libc::MS_REC | libc::MS_PRIVATE)?;
    // All effects below are private and disappear when this subprocess exits.
    exact(&entries()?, target)?;
    let original = identity(target)?;
    let recovery = tree_fd(target, true)?;
    unmount(target)?;
    ensure!(
        !entries()?.iter().any(|e| e.path == target),
        "Stacked underlying mount is not supported"
    );
    let underlying = identity(target)?;
    move_fd(&recovery, target)?;
    ensure!(identity(target)? == original, "Recovery rehearsal failed");
    println!("{}", serde_json::to_string(&underlying)?);
    Ok(())
}
fn tree_fd(path: &str, clone: bool) -> Result<std::os::fd::OwnedFd> {
    use std::os::fd::FromRawFd;
    let path = CString::new(path)?;
    let fd = unsafe {
        libc::syscall(
            libc::SYS_open_tree,
            libc::AT_FDCWD,
            path.as_ptr(),
            libc::O_CLOEXEC | if clone { 1 } else { 0 },
        )
    };
    ensure!(
        fd >= 0,
        "Kernel does not support opening recovery mount: {}",
        std::io::Error::last_os_error()
    );
    Ok(unsafe { std::os::fd::OwnedFd::from_raw_fd(fd as i32) })
}
fn move_backup(source: &str, target: &str) -> Result<()> {
    move_fd(&tree_fd(source, false)?, target)
}
fn move_fd(fd: &std::os::fd::OwnedFd, target: &str) -> Result<()> {
    use std::os::fd::AsRawFd;
    let target = CString::new(target)?;
    const MOVE_MOUNT_F_EMPTY_PATH: u32 = 4;
    let rc = unsafe {
        libc::syscall(
            libc::SYS_move_mount,
            fd.as_raw_fd(),
            c"".as_ptr(),
            libc::AT_FDCWD,
            target.as_ptr(),
            MOVE_MOUNT_F_EMPTY_PATH,
        )
    };
    ensure!(
        rc == 0,
        "Cannot move recovery mount: {}",
        std::io::Error::last_os_error()
    );
    Ok(())
}

pub fn apply(plan: &Plan) -> Result<()> {
    validate(plan)?;
    apply_validated(plan)
}

fn apply_validated(plan: &Plan) -> Result<()> {
    let dir = Path::new(&plan.backup).parent().unwrap();
    fs::create_dir_all(dir)?;
    ensure!(dir.canonicalize()? == dir, "Invalid backup directory");
    let all = entries()?;
    let covering = all
        .iter()
        .filter(|e| dir.starts_with(&e.path))
        .max_by_key(|e| e.path.len())
        .context("Missing backup parent mount")?;
    ensure!(private(covering), "Backup parent mount must be private");
    fs::OpenOptions::new()
        .write(true)
        .create_new(true)
        .open(&plan.backup)?;
    // Pin a separate dentry, not just the original mount root. This survives
    // unlinking the module source. Cross-filesystem links fail before unmount.
    fs::hard_link(&plan.source, &plan.anchor)
        .context("Cannot retain module source on backup filesystem")?;
    ensure!(
        identity(&plan.anchor)? == plan.original,
        "Pinned source changed"
    );
    mount(Some(&plan.anchor), &plan.backup, libc::MS_BIND)?;
    let original = parse(&plan.mount_line)?;
    mount(
        None,
        &plan.backup,
        libc::MS_BIND | libc::MS_REMOUNT | saved_flags(&original[0])?,
    )?;
    mount(None, &plan.backup, libc::MS_PRIVATE)?;
    ensure!(
        identity(&plan.backup)? == plan.original,
        "Recovery backup verification failed"
    );
    // Require the fd-based mount API before unmounting. Unlike path-based
    // MS_MOVE, it can restore a pinned file after its module source is deleted.
    drop(tree_fd(&plan.backup, false)?);
    // Check again immediately before changing the visible mount.
    ensure!(
        eligible(&entries()?, &plan.target)? == plan.mount_line,
        "Target changed while preparing backup"
    );
    unmount(&plan.target)?;
    ensure!(
        identity(&plan.target)? == plan.underlying,
        "Unexpected underlying resource; restoring"
    );
    Ok(())
}
pub fn restore(plan: &Plan) -> Result<()> {
    paths(plan)?;
    restore_saved(plan)
}

fn restore_saved(plan: &Plan) -> Result<()> {
    let all = entries()?;
    let at_target: Vec<_> = all.iter().filter(|e| e.path == plan.target).collect();
    if !at_target.is_empty() {
        ensure!(
            at_target.len() == 1 && identity(&plan.target)? == plan.original,
            "Target changed externally; refusing to overwrite another mount"
        );
    } else {
        ensure!(
            identity(&plan.target)? == plan.underlying,
            "Underlying file changed; backup retained"
        );
        let original = parse(&plan.mount_line)?;
        let parent = all
            .iter()
            .find(|e| e.id == original[0].parent)
            .context("Original parent mount no longer exists")?;
        ensure!(
            private(parent),
            "Parent propagation changed; backup retained"
        );
        ensure!(
            identity(&plan.backup)? == plan.original && private(exact(&all, &plan.backup)?),
            "Recovery backup missing or changed"
        );
        // Move the saved mount back, preserving its original per-mount flags.
        move_backup(&plan.backup, &plan.target)?;
        ensure!(
            identity(&plan.target)? == plan.original,
            "Restored mount verification failed"
        );
    }
    let all = entries()?;
    if all.iter().any(|e| e.path == plan.backup) {
        ensure!(
            identity(&plan.backup)? == plan.original,
            "Unexpected backup mount"
        );
        unmount(&plan.backup)?;
    }
    if Path::new(&plan.backup).exists() {
        fs::remove_file(&plan.backup)?;
    }
    if Path::new(&plan.anchor).exists() {
        fs::remove_file(&plan.anchor)?;
    }
    Ok(())
}

pub fn discard_old_boot(plan: &Plan) -> Result<()> {
    paths(plan)?;
    let all = entries()?;
    for path in [&plan.backup, &plan.anchor] {
        ensure!(
            !all.iter().any(|entry| &entry.path == path),
            "Unexpected mount at old backup; retained"
        );
        if Path::new(path).try_exists()? {
            ensure!(
                fs::symlink_metadata(path)?.is_file(),
                "Unexpected old backup type"
            );
            fs::remove_file(path)?;
        }
    }
    Ok(())
}

#[cfg(test)]
mod tests {
    use super::*;
    #[test]
    #[ignore = "Run explicitly in an isolated user/mount namespace"]
    fn real_mount_backup_unmount_and_restore_preserves_readonly() {
        assert_eq!(unsafe { libc::geteuid() }, 0);
        let inherited = fs::read_link("/proc/thread-self/ns/mnt").unwrap();
        assert_eq!(unsafe { libc::unshare(libc::CLONE_NEWNS) }, 0);
        assert_ne!(
            fs::read_link("/proc/thread-self/ns/mnt").unwrap(),
            inherited
        );
        mount(None, "/", libc::MS_REC | libc::MS_PRIVATE).unwrap();
        let root = std::env::temp_dir().join(format!("aster-mount-fixture-{}", std::process::id()));
        fs::create_dir_all(&root).unwrap();
        let source = root.join("module-resource").to_str().unwrap().to_owned();
        let target = root.join("system-resource").to_str().unwrap().to_owned();
        let backup = root.join("backups/saved").to_str().unwrap().to_owned();
        fs::write(&source, b"module resource").unwrap();
        fs::write(&target, b"system resource").unwrap();
        let underlying = identity(&target).unwrap();
        mount(Some(&source), &target, libc::MS_BIND).unwrap();
        mount(
            None,
            &target,
            libc::MS_BIND | libc::MS_REMOUNT | libc::MS_RDONLY,
        )
        .unwrap();
        // Exercise the fd-based restoration rehearsal before the real trial.
        let rehearsal = tree_fd(&target, true).unwrap();
        unmount(&target).unwrap();
        assert_eq!(identity(&target).unwrap(), underlying);
        move_fd(&rehearsal, &target).unwrap();
        drop(rehearsal);
        let plan = Plan {
            original: identity(&target).unwrap(),
            underlying,
            mount_line: eligible(&entries().unwrap(), &target).unwrap(),
            source,
            target,
            anchor: format!("{backup}.source"),
            backup,
        };
        apply_validated(&plan).unwrap();
        assert_eq!(fs::read(&plan.target).unwrap(), b"system resource");
        assert_eq!(fs::read(&plan.backup).unwrap(), b"module resource");
        // A new third-party mount must not be overwritten during recovery.
        let foreign = root.join("foreign").to_str().unwrap().to_owned();
        fs::write(&foreign, b"foreign resource").unwrap();
        mount(Some(&foreign), &plan.target, libc::MS_BIND).unwrap();
        assert!(restore_saved(&plan).is_err());
        assert!(Path::new(&plan.backup).exists());
        unmount(&plan.target).unwrap();
        // Restore even if the module's original pathname was removed meanwhile.
        fs::remove_file(&plan.source).unwrap();
        restore_saved(&plan).unwrap();
        assert_eq!(fs::read(&plan.target).unwrap(), b"module resource");
        assert!(
            exact(&entries().unwrap(), &plan.target)
                .unwrap()
                .options
                .iter()
                .any(|s| s == "ro")
        );
        restore_saved(&plan).unwrap();
        assert!(!Path::new(&plan.backup).exists());
        unmount(&plan.target).unwrap();
        assert_eq!(fs::read(&plan.target).unwrap(), b"system resource");
        fs::remove_dir_all(root).unwrap();
    }

    #[test]
    fn rejects_critical_and_escaped_paths() {
        for p in [
            "/",
            "/data",
            "/system",
            "/system/bin/sh",
            "/system/etc/fstab",
            "/system/media/../bin/sh",
            "/system/media//a",
            "/system/media/a\n",
        ] {
            assert!(!resource(p), "{p}");
        }
        assert!(resource("/system/media/bootanimation.zip"));
        assert!(resource("/product/fonts/test.ttf"));
    }
    #[test]
    fn rejects_stacked_writable_and_shared_mounts() {
        let parent = "1 0 1:1 / /system rw - ext4 /dev/test rw\n";
        let file = "2 1 1:1 /adb/modules/a /system/media/a ro - ext4 /dev/test rw\n";
        assert!(
            eligible(
                &parse(&format!("{parent}{file}")).unwrap(),
                "/system/media/a"
            )
            .is_ok()
        );
        for text in [
            format!("{parent}{file}{file}"),
            format!("{parent}{}", file.replace("a ro -", "a rw -")),
            format!("{parent}{}", file.replace("ro -", "ro shared:1 -")),
            format!("{}{file}", parent.replace("rw -", "rw shared:1 -")),
        ] {
            assert!(eligible(&parse(&text).unwrap(), "/system/media/a").is_err());
        }
    }
    #[test]
    fn parses_mount_escapes_and_rejects_malformed_input() {
        assert_eq!(decode("/a\\040b").unwrap(), "/a b");
        assert!(decode("/a\\999").is_err());
        assert!(parse("bad input").is_err());
    }
}
