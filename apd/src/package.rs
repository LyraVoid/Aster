use std::{
    collections::HashMap,
    fs::{self, File, OpenOptions},
    io::{self, Read},
    os::fd::AsRawFd,
    os::unix::fs::OpenOptionsExt,
    path::Path,
};

use serde::{Deserialize, Serialize};

const CONFIG_PATH: &str = "/data/adb/ap/package_config";
const PACKAGES_PATH: &str = "/data/system/packages.list";
const HEADER: [&str; 6] = ["pkg", "exclude", "allow", "uid", "to_uid", "sctx"];
const MAX_SNAPSHOT_BYTES: u64 = 16 * 1024 * 1024;

#[derive(Debug, Deserialize, Serialize, Clone, PartialEq, Eq)]
pub struct PackageConfig {
    pub pkg: String,
    pub exclude: i32,
    pub allow: i32,
    pub uid: i32,
    pub to_uid: i32,
    pub sctx: String,
}

fn invalid(message: &str) -> io::Error {
    io::Error::new(io::ErrorKind::InvalidData, message)
}

fn read_snapshot(path: &Path) -> io::Result<Vec<u8>> {
    let mut bytes = Vec::new();
    File::open(path)?
        .take(MAX_SNAPSHOT_BYTES + 1)
        .read_to_end(&mut bytes)?;
    if bytes.len() as u64 > MAX_SNAPSHOT_BYTES {
        return Err(invalid("Package snapshot exceeds size limit"));
    }
    Ok(bytes)
}

fn parse_config(bytes: &[u8]) -> io::Result<Vec<PackageConfig>> {
    if !bytes.ends_with(b"\n") {
        return Err(invalid("Incomplete package_config snapshot"));
    }
    let mut reader = csv::Reader::from_reader(bytes);
    // A zero-byte or headerless file is not an intentional empty configuration.
    if reader.headers()?.iter().collect::<Vec<_>>() != HEADER {
        return Err(invalid("Invalid package_config header"));
    }
    let configs: Vec<PackageConfig> = reader.deserialize().collect::<Result<_, _>>()?;
    validate_configs(&configs)?;
    Ok(configs)
}

fn validate_configs(configs: &[PackageConfig]) -> io::Result<()> {
    let mut profiles = HashMap::new();
    for config in configs {
        if config.pkg.is_empty()
            || config.pkg.chars().any(|c| c.is_whitespace() || c == '\0')
            || !matches!(config.allow, 0 | 1)
            || !matches!(config.exclude, 0 | 1)
            || config.allow + config.exclude > 1
            || config.uid < 0
            || config.to_uid < 0
            || config.sctx.is_empty()
            || config.sctx.len() >= 96
            || config.sctx.contains('\0')
        {
            return Err(invalid("Invalid package authorization profile"));
        }
        let profile = (
            config.allow,
            config.exclude,
            config.to_uid,
            config.sctx.as_str(),
        );
        if let Some(previous) = profiles.insert(config.uid, profile)
            && previous != profile
        {
            return Err(invalid("Conflicting profiles for shared UID"));
        }
    }
    Ok(())
}

fn parse_packages(bytes: &[u8]) -> io::Result<HashMap<String, i32>> {
    if !bytes.ends_with(b"\n") {
        return Err(invalid("Incomplete packages.list snapshot"));
    }
    let text = std::str::from_utf8(bytes).map_err(|_| invalid("Invalid packages.list encoding"))?;
    let mut packages = HashMap::new();
    for line in text.lines().filter(|line| !line.trim().is_empty()) {
        let mut words = line.split_whitespace();
        let pkg = words
            .next()
            .ok_or_else(|| invalid("Missing package name"))?;
        let uid = words
            .next()
            .and_then(|word| word.parse::<i32>().ok())
            .filter(|uid| *uid >= 0)
            .ok_or_else(|| invalid("Invalid package UID"))?;
        if packages.insert(pkg.to_owned(), uid).is_some() {
            return Err(invalid("Duplicate package in packages.list"));
        }
    }
    if packages.is_empty() {
        return Err(invalid("Empty packages.list"));
    }
    Ok(packages)
}

fn reconcile(
    configs: &[PackageConfig],
    packages: &HashMap<String, i32>,
) -> io::Result<Vec<PackageConfig>> {
    let mut result = Vec::new();
    for config in configs {
        if let Some(uid) = packages.get(&config.pkg) {
            let mut config = config.clone();
            config.uid = (config.uid / 100000 * 100000)
                .checked_add(uid % 100000)
                .ok_or_else(|| invalid("UID overflow"))?;
            result.push(config);
        }
    }
    validate_configs(&result)?;
    Ok(result)
}

// Java FileChannel.lock uses POSIX record locks, not flock. Keep the lock file
// separate from the atomically replaced CSV and hold it through kernel refresh.
fn lock_config(path: &Path) -> io::Result<File> {
    let file = OpenOptions::new()
        .read(true)
        .write(true)
        .create(true)
        .truncate(false)
        .mode(0o600)
        .open(path.with_extension("lock"))?;
    let mut lock: libc::flock = unsafe { std::mem::zeroed() };
    lock.l_type = libc::F_WRLCK as _;
    lock.l_whence = libc::SEEK_SET as _;
    // Java locks [0, Long.MAX_VALUE); lock the same range.
    lock.l_len = i64::MAX as _;
    loop {
        if unsafe { libc::fcntl(file.as_raw_fd(), libc::F_SETLKW, &lock) } != -1 {
            return Ok(file);
        }
        let error = io::Error::last_os_error();
        if error.kind() != io::ErrorKind::Interrupted {
            return Err(error);
        }
    }
}

fn write_config(path: &Path, configs: &[PackageConfig]) -> io::Result<()> {
    let temp = path.with_extension("apd.tmp");
    let result = (|| {
        let file = OpenOptions::new()
            .write(true)
            .create_new(true)
            .mode(0o600)
            .open(&temp)?;
        let mut writer = csv::WriterBuilder::new()
            .has_headers(false)
            .from_writer(file);
        writer.write_record(HEADER)?;
        for config in configs {
            writer.serialize(config)?;
        }
        writer.flush()?;
        writer.get_ref().sync_all()?;
        fs::rename(&temp, path)
    })();
    // A stale temporary file is safe to remove while holding the config lock.
    if result.is_err() {
        let _ = fs::remove_file(&temp);
    }
    result
}

pub fn with_synchronized_package_config(apply: impl FnOnce(&[PackageConfig])) -> io::Result<()> {
    synchronize_at(Path::new(CONFIG_PATH), Path::new(PACKAGES_PATH), apply)
}

fn synchronize_at(
    config_path: &Path,
    packages_path: &Path,
    apply: impl FnOnce(&[PackageConfig]),
) -> io::Result<()> {
    let _lock = lock_config(config_path)?;
    let original = read_snapshot(config_path)?;
    let configs = parse_config(&original)?;
    let packages_bytes = read_snapshot(packages_path)?;
    let packages = parse_packages(&packages_bytes)?;
    let updated = reconcile(&configs, &packages)?;
    // Also fail closed for legacy writers which do not participate in the lock.
    if read_snapshot(config_path)? != original || read_snapshot(packages_path)? != packages_bytes {
        return Err(io::Error::new(
            io::ErrorKind::WouldBlock,
            "Package snapshot changed during refresh",
        ));
    }
    if updated != configs {
        write_config(config_path, &updated)?;
    }
    apply(&updated);
    Ok(())
}

#[cfg(test)]
#[path = "package_tests.rs"]
mod tests;
