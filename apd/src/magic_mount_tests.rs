use super::*;
use std::sync::atomic::{AtomicUsize, Ordering};

static NEXT_DIR: AtomicUsize = AtomicUsize::new(0);

struct Fixture(PathBuf);
impl Fixture {
    fn new() -> Self {
        let path = std::env::temp_dir().join(format!(
            "aster-magic-mount-{}-{}",
            std::process::id(),
            NEXT_DIR.fetch_add(1, Ordering::Relaxed)
        ));
        fs::create_dir(&path).unwrap();
        Self(path)
    }
}
impl Drop for Fixture {
    fn drop(&mut self) {
        fs::remove_dir_all(&self.0).unwrap();
    }
}

#[test]
fn collects_files_directories_and_symlinks_without_following_links() {
    let fixture = Fixture::new();
    fs::create_dir(fixture.0.join("etc")).unwrap();
    fs::write(fixture.0.join("etc/config"), "module").unwrap();
    symlink("missing-target", fixture.0.join("link")).unwrap();
    let mut root = Node::new_root("system");
    assert!(root.collect_module_files(&fixture.0).unwrap());
    assert_eq!(root.children["etc"].file_type, Directory);
    assert_eq!(
        root.children["etc"].children["config"].file_type,
        RegularFile
    );
    assert_eq!(root.children["link"].file_type, Symlink);
}

#[test]
fn merging_modules_preserves_first_file_and_collects_additions() {
    let first = Fixture::new();
    let second = Fixture::new();
    for dir in [&first.0, &second.0] {
        fs::create_dir(dir.join("etc")).unwrap();
        fs::write(dir.join("etc/shared"), "module").unwrap();
    }
    fs::write(second.0.join("etc/addition"), "new").unwrap();
    let mut root = Node::new_root("system");
    root.collect_module_files(&first.0).unwrap();
    root.collect_module_files(&second.0).unwrap();
    let children = &root.children["etc"].children;
    assert_eq!(
        children["shared"].module_path.as_ref().unwrap(),
        &first.0.join("etc/shared")
    );
    assert_eq!(
        children["addition"].module_path.as_ref().unwrap(),
        &second.0.join("etc/addition")
    );
}

#[test]
fn tmpfs_is_required_for_new_files_type_changes_and_symlinks() {
    let fixture = Fixture::new();
    let file = fixture.0.join("file");
    fs::write(&file, "original").unwrap();
    assert!(!RegularFile.needs_tmpfs_vs_real(&file));
    assert!(Directory.needs_tmpfs_vs_real(&file));
    assert!(RegularFile.needs_tmpfs_vs_real(&fixture.0.join("new")));
    assert!(Symlink.needs_tmpfs_vs_real(&file));
    assert!(Whiteout.needs_tmpfs_vs_real(&file));
    assert!(!Whiteout.needs_tmpfs_vs_real(&fixture.0.join("new")));
}

#[test]
fn replacing_a_directory_requires_only_one_tmpfs_layer() {
    let fixture = Fixture::new();
    let mut node = Node::new_root("etc");
    node.module_path = Some(fixture.0.clone());
    node.replace = true;
    assert!(should_create_tmpfs(&fixture.0, &mut node, false));
    assert!(!should_create_tmpfs(&fixture.0, &mut node, true));
}

#[test]
fn missing_child_of_a_root_node_is_skipped_instead_of_overlaying_root() {
    let fixture = Fixture::new();
    let mut root = Node::new_root("");
    root.children
        .insert("missing".into(), Node::new_root("missing"));
    assert!(!should_create_tmpfs(&fixture.0, &mut root, false));
    assert!(root.children["missing"].skip);
}

#[test]
fn empty_module_tree_does_not_request_a_mount() {
    let fixture = Fixture::new();
    let mut root = Node::new_root("system");
    assert!(!root.collect_module_files(&fixture.0).unwrap());
}
