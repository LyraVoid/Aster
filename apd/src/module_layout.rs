//! Upgrade only the partition links created by Aster's former default installer.
//! The imported Magic mount collector itself remains identical to FolkPatch main.
use crate::defs;
use anyhow::{Context, Result};
use std::{fs, os::unix::fs::symlink, path::Path};

pub fn prepare_magic_mount_modules() -> Result<()> {
    for entry in fs::read_dir(defs::MODULE_DIR)? {
        let entry = entry?;
        if !entry.file_type()?.is_dir()
            || entry.path().join(defs::DISABLE_FILE_NAME).exists()
            || entry.path().join(defs::SKIP_MOUNT_FILE_NAME).exists()
        {
            continue;
        }
        normalize_legacy_partitions(&entry.path())?;
    }
    Ok(())
}

fn normalize_legacy_partitions(module: &Path) -> Result<()> {
    for partition in ["vendor", "system_ext", "product"] {
        let original = module.join(partition);
        let destination = module.join("system").join(partition);
        let old_link = format!("../{partition}");
        // Do not reinterpret module-authored symlinks or follow a directory outside the module.
        if fs::read_link(&destination).ok().as_deref() != Some(Path::new(&old_link))
            || !fs::symlink_metadata(&original).is_ok_and(|m| m.is_dir())
            || !fs::symlink_metadata(module.join("system")).is_ok_and(|m| m.is_dir())
        {
            continue;
        }
        fs::remove_file(&destination)?;
        if let Err(error) = fs::rename(&original, &destination) {
            symlink(&old_link, &destination).context("restore legacy partition link")?;
            return Err(error).context("move legacy partition into system");
        }
        if let Err(error) = symlink(format!("./system/{partition}"), &original) {
            fs::rename(&destination, &original).context("roll back legacy partition directory")?;
            symlink(&old_link, &destination).context("restore legacy partition link")?;
            return Err(error).context("create partition alias");
        }
        log::info!("Updated legacy partition layout: {}", destination.display());
    }
    Ok(())
}

#[cfg(test)]
mod tests {
    use super::*;
    use std::sync::atomic::{AtomicUsize, Ordering};
    static NEXT_DIR: AtomicUsize = AtomicUsize::new(0);

    struct Fixture(std::path::PathBuf);
    impl Fixture {
        fn new() -> Self {
            let path = std::env::temp_dir().join(format!(
                "aster-module-layout-{}-{}",
                std::process::id(),
                NEXT_DIR.fetch_add(1, Ordering::Relaxed)
            ));
            fs::create_dir(&path).unwrap();
            fs::create_dir(path.join("system")).unwrap();
            Self(path)
        }
    }
    impl Drop for Fixture {
        fn drop(&mut self) {
            fs::remove_dir_all(&self.0).unwrap();
        }
    }

    #[test]
    fn converts_legacy_partitions_without_losing_content_and_is_idempotent() {
        let fixture = Fixture::new();
        for partition in ["vendor", "system_ext", "product"] {
            fs::create_dir(fixture.0.join(partition)).unwrap();
            fs::write(fixture.0.join(partition).join("config"), "preserved").unwrap();
            symlink(
                format!("../{partition}"),
                fixture.0.join("system").join(partition),
            )
            .unwrap();
        }
        normalize_legacy_partitions(&fixture.0).unwrap();
        normalize_legacy_partitions(&fixture.0).unwrap();
        for partition in ["vendor", "system_ext", "product"] {
            assert!(
                fs::symlink_metadata(fixture.0.join("system").join(partition))
                    .unwrap()
                    .is_dir()
            );
            assert_eq!(
                fs::read_to_string(fixture.0.join(partition).join("config")).unwrap(),
                "preserved"
            );
            assert_eq!(
                fs::read_link(fixture.0.join(partition)).unwrap(),
                Path::new(&format!("./system/{partition}"))
            );
        }
    }

    #[test]
    fn leaves_module_authored_links_and_real_system_directories_unchanged() {
        let fixture = Fixture::new();
        fs::create_dir(fixture.0.join("vendor")).unwrap();
        symlink("../custom_vendor", fixture.0.join("system/vendor")).unwrap();
        fs::create_dir(fixture.0.join("system/product")).unwrap();
        normalize_legacy_partitions(&fixture.0).unwrap();
        assert_eq!(
            fs::read_link(fixture.0.join("system/vendor")).unwrap(),
            Path::new("../custom_vendor")
        );
        assert!(
            fs::symlink_metadata(fixture.0.join("vendor"))
                .unwrap()
                .is_dir()
        );
        assert!(
            fs::symlink_metadata(fixture.0.join("system/product"))
                .unwrap()
                .is_dir()
        );
    }

    #[test]
    fn does_not_follow_a_top_level_partition_link() {
        let fixture = Fixture::new();
        symlink("../external", fixture.0.join("vendor")).unwrap();
        symlink("../vendor", fixture.0.join("system/vendor")).unwrap();
        normalize_legacy_partitions(&fixture.0).unwrap();
        assert_eq!(
            fs::read_link(fixture.0.join("vendor")).unwrap(),
            Path::new("../external")
        );
        assert_eq!(
            fs::read_link(fixture.0.join("system/vendor")).unwrap(),
            Path::new("../vendor")
        );
    }
}
