use std::{
    collections::HashMap,
    fs,
    io::Read,
    path::{Component, Path},
};

const MAX_ICON_BYTES: u64 = 1024 * 1024;

// Invalid values must be removed, not forwarded to the manager as untrusted paths.
pub fn resolve(properties: &mut HashMap<String, String>, key: &str, module: &Path) {
    let Some(value) = properties.remove(key) else {
        return;
    };
    if let Some(path) = validated_path(module, value.trim()) {
        properties.insert(key.to_owned(), path);
    }
}

fn validated_path(module: &Path, value: &str) -> Option<String> {
    let relative = Path::new(value);
    if value.is_empty()
        || relative.is_absolute()
        || relative
            .components()
            .any(|c| matches!(c, Component::ParentDir))
    {
        return None;
    }
    // Module directory symlinks could otherwise move the trust boundary outside modules.
    if fs::symlink_metadata(module).ok()?.file_type().is_symlink() {
        return None;
    }
    let root = module.canonicalize().ok()?;
    let path = root.join(relative).canonicalize().ok()?;
    if !path.starts_with(&root) {
        return None;
    }
    // Nonblocking open also prevents a swapped FIFO from hanging module enumeration.
    use std::os::unix::fs::OpenOptionsExt;
    let mut file = fs::OpenOptions::new()
        .read(true)
        .custom_flags(libc::O_NONBLOCK | libc::O_NOFOLLOW)
        .open(&path)
        .ok()?;
    let metadata = file.metadata().ok()?;
    if !metadata.is_file() || metadata.len() == 0 || metadata.len() > MAX_ICON_BYTES {
        return None;
    }
    let mut header = [0; 12];
    let count = file.read(&mut header).ok()?;
    let header = &header[..count];
    let supported = header.starts_with(b"\x89PNG\r\n\x1a\n")
        || header.starts_with(b"\xff\xd8\xff")
        || (header.starts_with(b"RIFF") && header.get(8..12) == Some(b"WEBP"));
    supported
        .then(|| path.to_str().map(str::to_owned))
        .flatten()
}

#[cfg(test)]
mod tests {
    use super::*;
    use std::os::unix::fs::symlink;
    #[test]
    fn validates_module_icon_boundaries() {
        let temp = std::env::temp_dir().join(format!("apd-icons-{}", std::process::id()));
        fs::create_dir_all(temp.join("module/icons")).unwrap();
        let module = temp.join("module");
        let png = b"\x89PNG\r\n\x1a\nxxxx";
        fs::write(module.join("icons/ok.png"), png).unwrap();
        assert!(validated_path(&module, "icons/ok.png").is_some());
        for invalid in [
            "",
            "/etc/passwd",
            "../outside.png",
            "icons/../icons/ok.png",
            "missing",
            "icons",
        ] {
            assert!(validated_path(&module, invalid).is_none(), "{invalid}");
        }
        fs::create_dir_all(temp.join("module-other")).unwrap();
        fs::write(temp.join("module-other/out.png"), png).unwrap();
        symlink(temp.join("module-other/out.png"), module.join("escape.png")).unwrap();
        assert!(validated_path(&module, "escape.png").is_none());
        fs::write(module.join("bad.png"), b"<svg/>").unwrap();
        assert!(validated_path(&module, "bad.png").is_none());
        let large = fs::File::create(module.join("large.png")).unwrap();
        large.set_len(MAX_ICON_BYTES + 1).unwrap();
        assert!(validated_path(&module, "large.png").is_none());
        symlink(&module, temp.join("linked-module")).unwrap();
        assert!(validated_path(&temp.join("linked-module"), "icons/ok.png").is_none());
        let mut properties = HashMap::from([("actionIcon".into(), "../out.png".into())]);
        resolve(&mut properties, "actionIcon", &module);
        assert!(!properties.contains_key("actionIcon"));
        fs::remove_dir_all(temp).unwrap();
    }
}
