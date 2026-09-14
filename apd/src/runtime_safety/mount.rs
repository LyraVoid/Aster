use anyhow::{Context, Result, ensure};
use serde::{Deserialize, Serialize};
use std::{
    ffi::CString,
    fs,
    os::unix::fs::MetadataExt,
    path::{Path, PathBuf},
};

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
/// A mount that receives mounts and unmounts from a master. Its content is
/// controlled by another namespace, so a restore here could be overwritten or
/// duplicated by propagation from that master.
fn slave(entry: &Entry) -> bool {
    entry
        .optional
        .iter()
        .any(|field| field.starts_with("master:") || field.starts_with("propagate_from:"))
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
    // Per-mount flags live in the option list, but a few (notably `nosymfollow`)
    // are reported as optional fields. Both are replayed on restore, so both must
    // be understood; anything else is refused instead of being silently dropped.
    for option in entry.options.iter().chain(&entry.optional) {
        flags |= match option.as_str() {
            // A writable mount is accepted: this code never writes through the
            // target, and the original per-mount flags are replayed on restore.
            "rw" => 0,
            "ro" => libc::MS_RDONLY,
            "nosuid" => libc::MS_NOSUID,
            "nodev" => libc::MS_NODEV,
            "noexec" => libc::MS_NOEXEC,
            "nosymfollow" => libc::MS_NOSYMFOLLOW,
            "noatime" => libc::MS_NOATIME,
            "nodiratime" => libc::MS_NODIRATIME,
            "relatime" => libc::MS_RELATIME,
            "strictatime" => libc::MS_STRICTATIME,
            // Propagation is not a mount flag; `private` rejects it separately.
            option
                if option.starts_with("shared:")
                    || option.starts_with("master:")
                    || option.starts_with("propagate_from:") =>
            {
                0
            }
            _ => anyhow::bail!("Unsupported mount option: {option}"),
        }
    }
    Ok(flags)
}

fn eligible(entries: &[Entry], target: &str) -> Result<String> {
    let entry = exact(entries, target)?;
    // Rejects unsupported per-mount options instead of silently dropping them.
    saved_flags(entry)?;
    ensure!(private(entry), "Shared/slave mounts are not supported");
    let parent = entries
        .iter()
        .find(|e| e.id == entry.parent)
        .context("Missing parent mount")?;
    // The entry itself must stay private: unmounting a mount that has no peers
    // changes nothing outside this namespace. Its parent is a system partition
    // such as `/product`, which is normally shared and must stay that way.
    // Sharing the parent only means that mounts created *there* propagate
    // outward, and the only mount this code creates under it is the recovery
    // backup, which is isolated on a private directory of its own. A parent that
    // receives propagation from a master is still refused: that master can undo
    // or repeat whatever happens here.
    ensure!(!slave(parent), "Slave parent mounts are not supported");
    Ok(entry.line.clone())
}
/// Rejects a symbolic link anywhere in `path`, including the final component.
///
/// `Path::canonicalize` cannot be used for this check. It resolves through
/// libc `realpath`, which on Android reads the path back from `/proc/self/fd`
/// and refuses a path whose mount root was unlinked: the link reports
/// `"<path> (deleted)"` and `realpath` fails with ENOENT, even though the path
/// itself is intact, walkable and readable. That is exactly the state of a
/// target that a previous runtime-safety session restored, so the check has to
/// be independent of the mount's provenance. Walking the components with
/// `symlink_metadata` refuses the same input (any symlink component) without
/// depending on how the mount was created.
fn reject_symlinks(path: &str) -> Result<()> {
    let mut prefix = PathBuf::new();
    for component in Path::new(path).components() {
        prefix.push(component);
        let meta = fs::symlink_metadata(&prefix)?;
        ensure!(
            !meta.file_type().is_symlink(),
            "Symlinks are not supported: {path}"
        );
    }
    Ok(())
}
fn identity(path: &str) -> Result<Identity> {
    let meta = fs::symlink_metadata(path)?;
    ensure!(
        meta.is_file() && meta.len() > 0,
        "Expected a nonempty regular file: {path}"
    );
    reject_symlinks(path)?;
    Ok(Identity {
        dev: meta.dev(),
        ino: meta.ino(),
        size: meta.len(),
    })
}
/// Partitions that Magic mount can hoist out of a module's `system/` directory.
/// Used both to accept the resulting target and to list the candidates that a
/// module file may be visible at (`magic_mount::collect_module_files`).
const HOISTED_PARTITIONS: &[&str] = &["vendor", "system_ext", "product", "odm", "oem"];

/// Resource trees that may be revealed again: presentation data, not code,
/// configuration or boot-critical state. Checked against the *resolved*
/// absolute target, so an input path can never escape it.
const RESOURCE_TREES: &[&str] = &["fonts", "media", "overlay"];

/// A resource target is `<partition>/<tree>/<file>` where the partition is one of
/// `/system`, a hoisted partition, or `/system/<hoisted partition>` on
/// system-as-root devices. Nothing else is accepted, and the file component must
/// be present: a bare directory is never a target.
fn resource(target: &str) -> bool {
    if !target.starts_with('/') || target.contains('\\') || target.chars().any(char::is_control) {
        return false;
    }
    let parts: Vec<&str> = target.split('/').skip(1).collect();
    if parts
        .iter()
        .any(|part| part.is_empty() || *part == "." || *part == "..")
    {
        return false;
    }
    match parts.as_slice() {
        // `/system/<tree>/<file...>`
        ["system", tree, _, ..] if RESOURCE_TREES.contains(tree) => true,
        // `/system/<partition>/<tree>/<file...>`: the pre-hoist layout is still a
        // valid target when Magic mount leaves the partition inside `/system`.
        ["system", partition, tree, _, ..] => {
            HOISTED_PARTITIONS.contains(partition) && RESOURCE_TREES.contains(tree)
        }
        // `/<partition>/<tree>/<file...>` after the partition was hoisted.
        [partition, tree, _, ..] => {
            HOISTED_PARTITIONS.contains(partition) && RESOURCE_TREES.contains(tree)
        }
        _ => false,
    }
}

/// Partitions Magic mount hoists out of a module's `system/` directory when the
/// matching root-level partition exists (`magic_mount::collect_module_files`).
/// Both the hoisted and the non-hoisted target are tried and verified by
/// identity, so the layout on the device decides, not an assumption.
const MODULE_ROOT: &str = "/data/adb/modules/";
fn module_id(module: &str) -> Result<()> {
    ensure!(
        !module.is_empty()
            && module
                .bytes()
                .all(|b| b.is_ascii_alphanumeric() || b"._-".contains(&b))
            && module != "."
            && module != "..",
        "Invalid module ID"
    );
    Ok(())
}
fn module_ids() -> Result<Vec<String>> {
    let mut found = Vec::new();
    for entry in fs::read_dir(MODULE_ROOT)? {
        let entry = entry?;
        if entry.file_type()?.is_dir()
            && let Some(name) = entry.file_name().to_str()
        {
            found.push(name.to_owned());
        }
    }
    found.sort();
    Ok(found)
}

/// Where a module file below `<module>/system/` can be visible. Magic mount maps
/// a module's `system/` onto `/system`, then hoists the built-in partitions.
fn targets_for_module_file(module: &str, file: &str) -> Result<Vec<String>> {
    let prefix = format!("{MODULE_ROOT}{module}/system/");
    let rest = file
        .strip_prefix(&prefix)
        .context("Module file is not under the module's system/ directory")?;
    ensure!(
        !rest.is_empty() && !rest.contains("//"),
        "Invalid module resource path"
    );
    let mut targets = vec![format!("/system/{rest}")];
    if let Some((partition, _tail)) = rest.split_once('/')
        && HOISTED_PARTITIONS.contains(&partition)
    {
        let hoisted = format!("/{rest}");
        if hoisted != targets[0] {
            targets.push(hoisted);
        }
    }
    Ok(targets)
}

/// Where an absolute target is stored inside a module. Magic mount maps
/// `<module>/system/<rest>` onto `/system/<rest>` and hoists a partition that
/// lives directly under `/system`, so `/system/media/x` comes from
/// `<module>/system/media/x` and `/product/fonts/x` from
/// `<module>/system/product/fonts/x`.
fn candidate_files(module: &str, target: &str) -> Vec<String> {
    let rest = target.strip_prefix("/system").unwrap_or(target);
    vec![format!("{MODULE_ROOT}{module}/system{rest}")]
}

/// Pick the single existing module file among `candidates`.
fn unique_file(candidates: &[String]) -> Result<String> {
    let mut found = Vec::new();
    for candidate in candidates {
        if !found.contains(candidate) && identity(candidate).is_ok() {
            found.push(candidate.clone());
        }
    }
    ensure!(
        found.len() == 1,
        "Expected exactly one module file, found {}",
        found.len()
    );
    Ok(found.remove(0))
}

/// Pick the single candidate whose identity matches the file actually mounted.
fn unique_identity(candidates: &[String], observed: &Identity) -> Result<String> {
    let matched: Vec<&String> = candidates
        .iter()
        .filter(|candidate| identity(candidate).ok().as_ref() == Some(observed))
        .collect();
    ensure!(
        matched.len() == 1,
        "Expected exactly one matching mount, found {}",
        matched.len()
    );
    Ok(matched[0].clone())
}

/// A resolved pair: the module file that backs the target, and the absolute
/// target path it is visible at.
pub struct Resolved {
    pub source: String,
    pub target: String,
}

fn resolve_module_input(module: &str, sub: &str) -> Result<Resolved> {
    // Accept both the on-disk form used by Magic mount (`system/...`) and the
    // older target-relative form (`media/...`, `product/fonts/...`).
    let mut candidates = Vec::new();
    if sub.starts_with("system/") {
        candidates.push(format!("{MODULE_ROOT}{module}/{sub}"));
    }
    let normalized = format!("{MODULE_ROOT}{module}/system/{sub}");
    if !candidates.contains(&normalized) {
        candidates.push(normalized);
    }
    let source = unique_file(&candidates)
        .with_context(|| format!("No module file for {module}/{sub}"))?;
    let original = identity(&source)?;
    let target = unique_identity(&targets_for_module_file(module, &source)?, &original)
        .context("Module resource is not currently mounted")?;
    Ok(Resolved { source, target })
}

fn resolve_target_input(target: &str) -> Result<Resolved> {
    let observed = identity(target).context("Target is not a regular mounted file")?;
    let mut candidates = Vec::new();
    for module in module_ids()? {
        candidates.extend(candidate_files(&module, target));
    }
    let source = unique_identity(&candidates, &observed).with_context(|| {
        format!("No single module provides a file identical to {target}")
    })?;
    Ok(Resolved {
        source,
        target: target.to_owned(),
    })
}

enum Form<'a> {
    Modules { module: &'a str, sub: &'a str },
    Target(&'a str),
}

fn form(input: &str) -> Result<Form<'_>> {
    ensure!(
        !input.is_empty()
            && input.len() <= 1024
            && !input.contains(['\n', '\r', '\0'])
            && !input.chars().any(char::is_control),
        "Invalid resource path"
    );
    if let Some(rest) = input.strip_prefix(MODULE_ROOT) {
        let (module, sub) = rest.split_once('/').context("Module resource path required")?;
        return Ok(Form::Modules { module, sub });
    }
    if input.starts_with('/') {
        return Ok(Form::Target(input));
    }
    let (module, sub) = input.split_once('/').context("Module resource path required")?;
    Ok(Form::Modules { module, sub })
}

/// Targets an input can resolve to, without touching the filesystem.
fn possible_targets(sub: &str) -> Vec<String> {
    let rest = sub.strip_prefix("system/").unwrap_or(sub);
    let mut targets = vec![format!("/system/{rest}")];
    if let Some((partition, _tail)) = rest.split_once('/')
        && HOISTED_PARTITIONS.contains(&partition)
    {
        let hoisted = format!("/{rest}");
        if hoisted != targets[0] {
            targets.push(hoisted);
        }
    }
    targets
}

/// Syntax and policy check that needs no live mounts. This is what a saved
/// configuration is validated against; the full preflight still runs later.
pub fn syntax(input: &str) -> Result<()> {
    match form(input)? {
        Form::Target(target) => ensure!(
            resource(target),
            "Only module media/font/overlay resource files are supported; critical paths are blocked"
        ),
        Form::Modules { module, sub } => {
            module_id(module)?;
            ensure!(!sub.is_empty(), "Module resource path required");
            ensure!(
                possible_targets(sub).iter().any(|target| resource(target)),
                "Only module media/font/overlay resource files are supported; critical paths are blocked"
            );
        }
    }
    Ok(())
}

/// Accepts `module-id/<path>`, `/data/adb/modules/module-id/<path>` and any
/// absolute target such as `/system/media/bootanimation.zip`. The module that
/// provides an absolute target is detected by file identity, never guessed.
pub fn resolve(input: &str) -> Result<Resolved> {
    syntax(input)?;
    let resolved = match form(input)? {
        Form::Modules { module, sub } => resolve_module_input(module, sub)?,
        Form::Target(target) => resolve_target_input(target)?,
    };
    ensure!(
        resource(&resolved.target),
        "Only module media/font/overlay resource files are supported; critical paths are blocked"
    );
    Ok(resolved)
}

pub(super) fn source_lines(input: &str) -> Result<Vec<(usize, String)>> {
    ensure!(input.len() <= 16 * 1024, "Resource list is too large");
    let mut seen = std::collections::HashSet::new();
    let mut lines = Vec::new();
    for (index, raw) in input.lines().enumerate() {
        let source = raw.trim();
        if source.is_empty() {
            continue;
        }
        // Syntax only: the live preflight still has to pass before anything runs.
        syntax(source).with_context(|| format!("Line {}", index + 1))?;
        if seen.insert(source.to_owned()) {
            lines.push((index + 1, source.to_owned()));
        }
    }
    ensure!(
        !lines.is_empty() && lines.len() <= 16,
        "Enter 1 to 16 distinct module resource paths"
    );
    Ok(lines)
}

pub fn batch_plan(input: &str) -> Result<Vec<super::Change>> {
    let mut changes = Vec::new();
    let mut targets = std::collections::HashSet::new();
    for (line, source) in source_lines(input)? {
        // Every item gets its own recovery files. Nothing is persisted/applied
        // until the entire batch passes preview.
        let plan = plan(&source, &super::platform::token()?)
            .with_context(|| format!("Line {line}: {source}"))?;
        ensure!(
            targets.insert(plan.target.clone()),
            "Line {line}: duplicate target {}",
            plan.target
        );
        changes.push(super::Change::Mount(plan));
    }
    Ok(changes)
}

pub fn plan(input: &str, token: &str) -> Result<Plan> {
    let Resolved { source, target } = resolve(input)?;
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
/// Put a saved mount back in place and keep it private. A mount moved into a
/// shared parent becomes a peer of that parent, which would let the restored
/// shadow propagate outward and would stop the target from being a valid one for
/// a later session. Best effort: the visible mount is already restored, and a
/// reboot recreates it.
fn move_back_private(source: &str, target: &str) -> Result<()> {
    move_backup(source, target)?;
    if let Err(error) = mount(None, target, libc::MS_PRIVATE) {
        log::warn!("runtime-safety: restored mount not made private: {error}");
    }
    Ok(())
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

/// The recovery backup is a mount of its own. Creating it below a shared parent
/// would propagate it into every peer of that parent in other namespaces, so the
/// directory holding it is bound onto itself and made private first. Only that
/// bind can propagate (`/data` is normally shared); everything created below it
/// afterwards stays local to this namespace.
fn isolate_backup_dir(dir: &str) -> Result<()> {
    let all = entries()?;
    let covering = all
        .iter()
        .filter(|entry| dir.starts_with(&entry.path))
        .max_by_key(|entry| entry.path.len())
        .context("Missing backup parent mount")?;
    if private(covering) {
        return Ok(());
    }
    mount(Some(dir), dir, libc::MS_BIND).context("Cannot isolate backup directory")?;
    mount(None, dir, libc::MS_PRIVATE).context("Cannot make the backup directory private")?;
    ensure!(
        private(exact(&entries()?, dir)?),
        "Backup directory could not be isolated"
    );
    Ok(())
}

/// Drop the private backup container once nothing is left inside it, so no stray
/// mount outlives the session. Never fails the session: the visible mount was
/// restored already, and a leftover container is gone after the next reboot.
fn release_backup_dir(dir: &str) {
    let released = (|| -> Result<()> {
        let all = entries()?;
        if !all.iter().any(|entry| entry.path == dir) {
            return Ok(());
        }
        if all
            .iter()
            .any(|entry| entry.path.starts_with(&format!("{dir}/")))
        {
            return Ok(());
        }
        unmount(dir)?;
        Ok(())
    })();
    if let Err(error) = released {
        log::warn!("runtime-safety: backup directory not released: {error:#}");
    }
}

fn apply_validated(plan: &Plan) -> Result<()> {
    let dir = Path::new(&plan.backup).parent().unwrap();
    fs::create_dir_all(dir)?;
    ensure!(dir.canonicalize()? == dir, "Invalid backup directory");
    fs::OpenOptions::new()
        .write(true)
        .create_new(true)
        .open(&plan.backup)?;
    // Pin a separate dentry, not just the original mount root. This survives
    // unlinking the module source. Cross-filesystem links fail before unmount.
    // The link is created first: it must live on the source's own mount, and the
    // backup container below is a mount of its own.
    fs::hard_link(&plan.source, &plan.anchor)
        .context("Cannot retain module source on backup filesystem")?;
    ensure!(
        identity(&plan.anchor)? == plan.original,
        "Pinned source changed"
    );
    isolate_backup_dir(dir.to_str().context("Invalid backup directory")?)?;
    let all = entries()?;
    let covering = all
        .iter()
        .filter(|e| dir.starts_with(&e.path))
        .max_by_key(|e| e.path.len())
        .context("Missing backup parent mount")?;
    ensure!(private(covering), "Backup parent mount must be private");
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
    // The saved mount is moved back only when the target is free. Its root is
    // the pinned anchor file, and that file must then stay linked: a mount whose
    // root dentry was unlinked reports `"(deleted)"` and can neither be resolved
    // (`realpath`, and therefore `identity`) nor moved again (`move_mount`
    // returns ENOENT), which would make the target unusable for the rest of the
    // boot. The anchor is dropped by the new-boot sweep instead.
    let moved_back = at_target.is_empty();
    if !moved_back {
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
            !slave(parent),
            "Parent propagation changed; backup retained"
        );
        ensure!(
            identity(&plan.backup)? == plan.original && private(exact(&all, &plan.backup)?),
            "Recovery backup missing or changed"
        );
        // Move the saved mount back, preserving its original per-mount flags.
        move_back_private(&plan.backup, &plan.target)?;
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
    if !moved_back && Path::new(&plan.anchor).exists() {
        fs::remove_file(&plan.anchor)?;
    }
    if let Some(dir) = Path::new(&plan.backup).parent().and_then(Path::to_str) {
        release_backup_dir(dir);
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

fn backups_dir() -> String {
    format!("{}/mounts", super::ROOT)
}

/// Drop files that no mount can reference any more.
///
/// Two cases leave files behind on purpose: a restored shadow keeps its anchor
/// linked (see `restore_saved`), and re-arming the same target twice in one boot
/// leaves the earlier anchor behind once its mount is gone. Both are only
/// removable when this feature holds no mount at all, which is exactly the state
/// the new-boot path runs in. Fail-closed: if anything is still mounted there,
/// or an entry is not a plain file or an empty directory, it is kept.
pub fn sweep_backups() -> Result<()> {
    let dir = backups_dir();
    let all = entries()?;
    ensure!(
        !all
            .iter()
            .any(|entry| entry.path == dir || entry.path.starts_with(&format!("{dir}/"))),
        "Backups are still mounted"
    );
    let removed = sweep_files_in(&dir);
    if removed > 0 {
        log::info!("runtime-safety: removed {removed} stale backup file(s)");
    }
    Ok(())
}

/// Remove plain files and empty directories below `dir`. Never followed,
/// never recursed into a non-empty directory.
fn sweep_files_in(dir: &str) -> usize {
    let Ok(entries) = fs::read_dir(dir) else {
        return 0;
    };
    let mut removed = 0;
    for entry in entries.flatten() {
        let path = entry.path();
        let Some(text) = path.to_str() else {
            continue;
        };
        let result = match fs::symlink_metadata(&path) {
            Ok(meta) if meta.is_file() => fs::remove_file(&path),
            Ok(meta) if meta.is_dir() => fs::remove_dir(&path),
            Ok(_) => {
                log::warn!("runtime-safety: leaving unexpected backup entry: {text}");
                continue;
            }
            Err(error) => Err(error),
        };
        match result {
            Ok(()) => removed += 1,
            Err(error) => log::warn!("runtime-safety: backup entry not removed: {text}: {error}"),
        }
    }
    removed
}

#[cfg(test)]
mod tests {
    use super::*;
    #[test]
    fn multiline_input_deduplicates_and_preserves_error_line_numbers() {
        assert_eq!(
            source_lines("\n a/system/media/x\r\n\n b/product/fonts/y\n a/system/media/x ")
                .unwrap(),
            vec![
                (2, "a/system/media/x".into()),
                (4, "b/product/fonts/y".into())
            ]
        );
        assert!(source_lines(" \n").is_err());
        assert!(
            source_lines(
                &(0..17)
                    .map(|i| format!("m/system/media/{i}\n"))
                    .collect::<String>()
            )
            .is_err()
        );
        // Syntax problems are reported per line before any mount is touched.
        let error = source_lines("a/system/media/x\nb; false").unwrap_err();
        assert!(format!("{error:#}").contains("Line 2"), "{error:#}");
        assert!(source_lines("/system/bin/sh").is_err());
        assert!(source_lines("/data/adb/modules/../etc/passwd").is_err());
    }

    #[test]
    fn accepts_every_supported_input_form_and_rejects_escapes() {
        for input in [
            "mod/system/media/bootanimation.zip",
            "mod/media/bootanimation.zip",
            "/data/adb/modules/mod/system/media/bootanimation.zip",
            "/system/media/bootanimation.zip",
            "/product/fonts/example.ttf",
            "/system/product/fonts/example.ttf",
            "/system/overlay/Foo/Foo.apk",
            "/vendor/media/audio/x.ogg",
        ] {
            syntax(input).unwrap_or_else(|error| panic!("{input}: {error:#}"));
        }
        for input in [
            "",
            "/",
            "/system/bin/sh",
            "/system/etc/hosts",
            "/data/media/0/x",
            "/system/media",
            "/system/media/../bin/sh",
            "/system/media//x",
            "/system/media/x\u{7}",
            "mod",
            "./system/media/x",
            "mod/system/media/x\n",
        ] {
            assert!(syntax(input).is_err(), "{input} should be rejected");
        }
    }

    #[test]
    fn possible_targets_follows_partition_hoisting_without_assuming_layout() {
        assert_eq!(
            possible_targets("system/media/x"),
            vec!["/system/media/x".to_owned()]
        );
        assert_eq!(
            possible_targets("media/x"),
            vec!["/system/media/x".to_owned()]
        );
        // A hoisted partition can be visible at either place; identity decides.
        assert_eq!(
            possible_targets("product/fonts/x"),
            vec![
                "/system/product/fonts/x".to_owned(),
                "/product/fonts/x".to_owned()
            ]
        );
        assert_eq!(
            possible_targets("system/product/fonts/x"),
            possible_targets("product/fonts/x")
        );
        assert_eq!(
            possible_targets("system/system/priv-app/x/x.apk"),
            vec!["/system/system/priv-app/x/x.apk".to_owned()]
        );
    }

    #[test]
    fn sweep_removes_only_plain_files_and_empty_directories() {
        let root = fs::canonicalize(std::env::temp_dir())
            .unwrap()
            .join("apd-sweep-test");
        let _ = fs::remove_dir_all(&root);
        fs::create_dir_all(root.join("empty-dir")).unwrap();
        fs::create_dir_all(root.join("kept-dir")).unwrap();
        fs::write(root.join("stale.source"), b"x").unwrap();
        fs::write(root.join("kept-dir/inner"), b"y").unwrap();
        std::os::unix::fs::symlink("stale.source", root.join("link")).unwrap();

        assert_eq!(sweep_files_in(root.to_str().unwrap()), 2);
        assert!(!root.join("stale.source").exists());
        assert!(!root.join("empty-dir").exists());
        assert!(root.join("kept-dir/inner").exists(), "non-empty dirs stay");
        assert!(
            fs::symlink_metadata(root.join("link")).is_ok(),
            "links are never followed or removed"
        );
        // A missing directory is not an error: the feature never uses one twice.
        assert_eq!(sweep_files_in("/nonexistent/apd-sweep"), 0);
        let _ = fs::remove_dir_all(&root);
    }

    #[test]
    fn identity_rejects_symlinks_without_resolving_the_whole_path() {
        let root = fs::canonicalize(std::env::temp_dir())
            .unwrap()
            .join("apd-identity-test");
        let _ = fs::remove_dir_all(&root);
        fs::create_dir_all(root.join("dir")).unwrap();
        fs::write(root.join("dir/real"), b"x").unwrap();
        std::os::unix::fs::symlink("real", root.join("dir/link")).unwrap();
        std::os::unix::fs::symlink("dir", root.join("dirlink")).unwrap();

        let file = root.join("dir/real");
        assert_eq!(
            identity(file.to_str().unwrap()).unwrap().size,
            1,
            "plain file must resolve"
        );
        // A symlinked component is refused instead of being followed, so the
        // target can never be redirected outside the checked path.
        let error = identity(root.join("dirlink/real").to_str().unwrap()).unwrap_err();
        assert!(
            format!("{error:#}").contains("Symlinks are not supported"),
            "{error:#}"
        );
        // The final component is refused by the regular-file check before that.
        assert!(identity(root.join("dir/link").to_str().unwrap()).is_err());
        // Directories are never identities, symlinked or not.
        assert!(identity(root.join("dir").to_str().unwrap()).is_err());
        let _ = fs::remove_dir_all(&root);
    }

    #[test]
    fn module_file_targets_cover_hoisted_and_non_hoisted_layouts() {
        assert_eq!(
            targets_for_module_file("m", "/data/adb/modules/m/system/media/a.zip").unwrap(),
            vec!["/system/media/a.zip".to_owned()]
        );
        // The inverse direction must agree: a target is backed by the same file.
        assert_eq!(
            candidate_files("m", "/system/media/a.zip"),
            vec!["/data/adb/modules/m/system/media/a.zip".to_owned()]
        );
        assert_eq!(
            candidate_files("m", "/product/fonts/a.ttf"),
            vec!["/data/adb/modules/m/system/product/fonts/a.ttf".to_owned()]
        );
        for target in [
            "/system/media/a.zip",
            "/product/fonts/a.ttf",
            "/system/product/fonts/a.ttf",
        ] {
            for candidate in candidate_files("m", target) {
                assert!(
                    targets_for_module_file("m", &candidate)
                        .unwrap()
                        .contains(&target.to_owned()),
                    "{target} must be reachable from {candidate}"
                );
            }
        }
        assert_eq!(
            targets_for_module_file("m", "/data/adb/modules/m/system/product/fonts/a.ttf").unwrap(),
            vec![
                "/system/product/fonts/a.ttf".to_owned(),
                "/product/fonts/a.ttf".to_owned()
            ]
        );
        assert!(targets_for_module_file("m", "/data/adb/modules/m/vendor/x").is_err());
        assert!(targets_for_module_file("m", "/data/adb/modules/other/system/x").is_err());
    }

    #[test]
    #[ignore = "Run explicitly in an isolated user/mount namespace"]
    fn real_mount_backup_unmount_and_restore_preserves_original_flags() {
        assert_eq!(unsafe { libc::geteuid() }, 0);
        // `shared_root` reproduces a real device: the shadow mount itself is
        // private while the partition above it stays shared. It is what exercises
        // the on-demand isolation of the backup directory.
        for (readonly, shared_root) in [(true, false), (false, false), (true, true)] {
            let inherited = fs::read_link("/proc/thread-self/ns/mnt").unwrap();
            assert_eq!(unsafe { libc::unshare(libc::CLONE_NEWNS) }, 0);
            assert_ne!(
                fs::read_link("/proc/thread-self/ns/mnt").unwrap(),
                inherited
            );
            mount(None, "/", libc::MS_REC | libc::MS_PRIVATE).unwrap();
            let root = std::env::temp_dir().join(format!(
                "aster-mount-fixture-{}-{readonly}-{shared_root}",
                std::process::id()
            ));
            fs::create_dir_all(&root).unwrap();
            if shared_root {
                // Share the mount that covers the fixture, which is what a system
                // partition looks like on a device.
                let root_text = root.to_str().unwrap().to_owned();
                let all = entries().unwrap();
                let covering = all
                    .iter()
                    .filter(|entry| root_text.starts_with(&entry.path))
                    .max_by_key(|entry| entry.path.len())
                    .unwrap()
                    .path
                    .clone();
                mount(None, &covering, libc::MS_SHARED).unwrap();
            }
            let source = root.join("module-resource").to_str().unwrap().to_owned();
            let target = root.join("system-resource").to_str().unwrap().to_owned();
            let backup = root.join("backups/saved").to_str().unwrap().to_owned();
            fs::write(&source, b"module resource").unwrap();
            fs::write(&target, b"system resource").unwrap();
            let underlying = identity(&target).unwrap();
            mount(Some(&source), &target, libc::MS_BIND).unwrap();
            if shared_root {
                // A shadow mount is made private right after it is created (as
                // `magic_mount` does), so only the parent stays shared here.
                mount(None, &target, libc::MS_PRIVATE).unwrap();
                let all = entries().unwrap();
                let parent = all
                    .iter()
                    .find(|entry| entry.id == exact(&all, &target).unwrap().parent)
                    .unwrap();
                assert!(!private(parent), "the fixture must keep a shared parent");
            }
            if readonly {
                mount(
                    None,
                    &target,
                    libc::MS_BIND | libc::MS_REMOUNT | libc::MS_RDONLY,
                )
                .unwrap();
            }
            // Exercise the fd-based restoration rehearsal before the real trial.
            let rehearsal = tree_fd(&target, true).unwrap();
            unmount(&target).unwrap();
            assert_eq!(identity(&target).unwrap(), underlying);
            move_fd(&rehearsal, &target).unwrap();
            if shared_root {
                // A mount moved under a shared parent becomes a peer of it; the
                // production restore re-privatizes it for the same reason.
                mount(None, &target, libc::MS_PRIVATE).unwrap();
            }
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
            let backup_dir = Path::new(&plan.backup)
                .parent()
                .unwrap()
                .to_str()
                .unwrap()
                .to_owned();
            apply_validated(&plan).unwrap();
            if shared_root {
                // The backup is a mount of its own: below a shared parent it must
                // have been isolated, or it would propagate to that parent's peers.
                assert!(private(exact(&entries().unwrap(), &backup_dir).unwrap()));
            }
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
            // F6 regression: the restored mount's root is the pinned anchor, so
            // that file must stay linked. An unlinked root reads as "(deleted)",
            // which breaks `realpath` and any later `move_mount` of this mount.
            assert!(
                Path::new(&plan.anchor).exists(),
                "the anchor of a restored mount must stay linked"
            );
            assert!(
                !exact(&entries().unwrap(), &plan.target)
                    .unwrap()
                    .line
                    .contains("//deleted"),
                "a restored mount must not report a deleted root"
            );
            let restored_entries = entries().unwrap();
            let restored = exact(&restored_entries, &plan.target).unwrap();
            assert_eq!(
                restored.options.iter().any(|s| s == "ro"),
                readonly,
                "restored per-mount flags must match the original"
            );
            if shared_root {
                assert!(
                    private(restored),
                    "a restored shadow must not join the shared parent's peer group"
                );
            }
            restore_saved(&plan).unwrap();
            assert!(!Path::new(&plan.backup).exists());
            if shared_root {
                assert!(
                    !entries()
                        .unwrap()
                        .iter()
                        .any(|entry| entry.path == backup_dir),
                    "the private backup container must not outlive the session"
                );
            }
            unmount(&plan.target).unwrap();
            assert_eq!(fs::read(&plan.target).unwrap(), b"system resource");
            fs::remove_dir_all(root).unwrap();
        }
    }

    #[test]
    fn rejects_critical_and_escaped_paths() {
        for p in [
            "/",
            "/data",
            "/system",
            "/system/bin/sh",
            "/system/etc/fstab",
            "/system/etc/init/hw/init.rc",
            "/system/lib64/libc.so",
            "/system/framework/framework.jar",
            "/system/media/../bin/sh",
            "/system/media//a",
            "/system/media/a\n",
            "/system/media/",
            "/system/priv-app/x/x.apk",
        ] {
            assert!(!resource(p), "{p}");
        }
        for p in [
            "/system/media/bootanimation.zip",
            "/product/fonts/test.ttf",
            "/system/product/fonts/test.ttf",
            "/vendor/media/audio/a.ogg",
            "/system/overlay/App/App.apk",
            "/odm/media/x",
        ] {
            assert!(resource(p), "{p}");
        }
    }
    #[test]
    fn accepts_readonly_and_writable_file_mounts_but_rejects_stacked_and_shared() {
        let parent = "1 0 1:1 / /system rw - ext4 /dev/test rw\n";
        let file = "2 1 1:1 /adb/modules/a /system/media/a ro - ext4 /dev/test rw\n";
        assert!(
            eligible(
                &parse(&format!("{parent}{file}")).unwrap(),
                "/system/media/a"
            )
            .is_ok()
        );
        // Writable file mounts are supported; the backup replays their flags.
        let writable = file.replace("a ro -", "a rw -");
        assert!(
            eligible(
                &parse(&format!("{parent}{writable}")).unwrap(),
                "/system/media/a"
            )
            .is_ok()
        );
        assert_eq!(
            saved_flags(&parse(&format!("{parent}{writable}")).unwrap()[1]).unwrap(),
            0
        );
        assert_eq!(
            saved_flags(&parse(&format!("{parent}{file}")).unwrap()[1]).unwrap(),
            libc::MS_RDONLY
        );
        // `nosymfollow` is reported as an optional field but is a replayable mount
        // flag, so it must be preserved rather than dropped.
        let nosymfollow = file.replace("ro -", "ro nosymfollow -");
        assert!(
            eligible(&parse(&format!("{parent}{nosymfollow}")).unwrap(), "/system/media/a").is_ok()
        );
        assert_eq!(
            saved_flags(&parse(&format!("{parent}{nosymfollow}")).unwrap()[1]).unwrap(),
            libc::MS_RDONLY | libc::MS_NOSYMFOLLOW
        );
        // A shared parent (a system partition such as `/product`) is accepted:
        // the entry itself is private, so unmounting it stays in this namespace.
        assert!(
            eligible(
                &parse(&format!("{}{file}", parent.replace("rw -", "rw shared:1 -"))).unwrap(),
                "/system/media/a"
            )
            .is_ok()
        );
        // A parent that receives propagation from a master is still refused: that
        // master can undo or repeat whatever happens under it.
        for text in [
            format!("{parent}{file}{file}"),
            format!("{parent}{}", file.replace("ro -", "ro shared:1 -")),
            format!("{parent}{}", file.replace("ro -", "ro master:1 -")),
            format!("{parent}{}", file.replace("ro -", "ro propagate_from:1 -")),
            format!("{}{file}", parent.replace("rw -", "rw master:1 -")),
            format!(
                "{}{file}",
                parent.replace("rw -", "rw propagate_from:1 -")
            ),
            format!("{parent}{}", file.replace("ro -", "ro idmapped -")),
        ] {
            assert!(eligible(&parse(&text).unwrap(), "/system/media/a").is_err());
        }
    }

    #[test]
    fn only_mounts_that_receive_propagation_are_slaves() {
        let entry = |optional: &str| -> Entry {
            let mut entries = parse(&format!(
                "2 1 1:1 /adb/modules/a /system/media/a ro{optional} - ext4 /dev/test rw\n"
            ))
            .unwrap();
            entries.remove(0)
        };
        assert!(slave(&entry(" master:1")));
        assert!(slave(&entry(" propagate_from:1")));
        assert!(!slave(&entry(" shared:1")));
        assert!(!slave(&entry("")));
        assert!(private(&entry("")));
        assert!(!private(&entry(" shared:1")));
    }
    #[test]
    fn parses_mount_escapes_and_rejects_malformed_input() {
        assert_eq!(decode("/a\\040b").unwrap(), "/a b");
        assert!(decode("/a\\999").is_err());
        assert!(parse("bad input").is_err());
    }
}
