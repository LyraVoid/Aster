use super::*;

fn config(pkg: &str, uid: i32) -> PackageConfig {
    PackageConfig {
        pkg: pkg.into(),
        uid,
        to_uid: 0,
        allow: 1,
        exclude: 0,
        sctx: "u:r:magisk:s0".into(),
    }
}

#[test]
fn distinguishes_empty_config_from_missing_or_invalid_data() {
    assert!(
        parse_config(b"pkg,exclude,allow,uid,to_uid,sctx\n")
            .unwrap()
            .is_empty()
    );
    for bytes in [
        b"".as_slice(),
        b"wrong,header\n",
        b"pkg,exclude,allow,uid,to_uid,sctx\na,0,1,broken,0,u:r:magisk:s0\n",
        b"pkg,exclude,allow,uid,to_uid,sctx\na,0,1,123\n",
    ] {
        assert!(parse_config(bytes).is_err());
    }
}

#[test]
fn invalid_profiles_and_conflicting_shared_uids_fail_closed() {
    let base = config("a", 10123);
    let mut bad = base.clone();
    bad.uid = -1;
    assert!(validate_configs(&[bad]).is_err());
    let mut bad = base.clone();
    bad.sctx = "x".repeat(96);
    assert!(validate_configs(&[bad]).is_err());
    let mut bad = base.clone();
    bad.exclude = 1;
    assert!(validate_configs(&[bad]).is_err());
    let mut shared = base.clone();
    shared.pkg = "b".into();
    assert!(validate_configs(&[base.clone(), shared.clone()]).is_ok());
    shared.to_uid = 2000;
    assert!(validate_configs(&[base, shared]).is_err());
}

#[test]
fn preserves_multiuser_uid_and_shared_uid_and_remaps_reinstall() {
    let packages = parse_packages(b"a 10456 0 /data/a\nb 10456 0 /data/b\n").unwrap();
    let updated = reconcile(
        &[
            config("a", 10123),
            config("a", 110123),
            config("b", 10456),
            config("uninstalled", 10999),
        ],
        &packages,
    )
    .unwrap();
    assert_eq!(
        updated.iter().map(|c| c.uid).collect::<Vec<_>>(),
        vec![10456, 110456, 10456]
    );
    assert_eq!(updated.len(), 3);
}

#[test]
fn rejects_incomplete_or_invalid_system_package_list() {
    for bytes in [
        b"".as_slice(),
        b"a invalid\n",
        b"a\n",
        b"a -1\n",
        b"a 123\na 456\n",
        b"a 123\nb\n",
    ] {
        assert!(parse_packages(bytes).is_err());
    }
}

#[test]
fn transaction_never_applies_bad_snapshots_and_writes_valid_empty_config() {
    let root = std::env::temp_dir().join(format!("apd-package-test-{}", std::process::id()));
    fs::create_dir_all(&root).unwrap();
    let path = root.join("package_config");
    let packages = root.join("packages.list");
    fs::write(&packages, "installed 123 0 /data/installed\n").unwrap();
    assert!(synchronize_at(&path, &packages, |_| panic!("missing config applied")).is_err());
    fs::write(&path, b"invalid").unwrap();
    assert!(synchronize_at(&path, &packages, |_| panic!("invalid config applied")).is_err());
    assert_eq!(fs::read(&path).unwrap(), b"invalid");
    write_config(&path, &[config("installed", 123)]).unwrap();
    fs::write(&packages, b"installed 123\nbad\n").unwrap();
    let original = fs::read(&path).unwrap();
    assert!(synchronize_at(&path, &packages, |_| panic!("partial package list applied")).is_err());
    assert_eq!(fs::read(&path).unwrap(), original);
    fs::write(&packages, b"other 456\n").unwrap();
    let mut called = false;
    synchronize_at(&path, &packages, |configs| {
        called = true;
        assert!(configs.is_empty());
    })
    .unwrap();
    assert!(called);
    assert!(parse_config(&fs::read(&path).unwrap()).unwrap().is_empty());
    // Failed write must not reach the kernel or overwrite the previous CSV.
    write_config(&path, &[config("old", 123)]).unwrap();
    fs::create_dir(path.with_extension("apd.tmp")).unwrap();
    assert!(synchronize_at(&path, &packages, |_| panic!("failed write applied")).is_err());
    assert_eq!(
        parse_config(&fs::read(&path).unwrap()).unwrap(),
        vec![config("old", 123)]
    );
    fs::remove_dir_all(root).unwrap();
}

// Separate process: POSIX locks are process-scoped, so another thread would not
// test contention. This runs only against a temporary fixture, never real config.
#[test]
fn lock_probe_subprocess() {
    let Some(path) = std::env::var_os("APD_TEST_CONFIG_LOCK") else {
        return;
    };
    let file = OpenOptions::new()
        .read(true)
        .write(true)
        .open(path)
        .unwrap();
    let mut lock: libc::flock = unsafe { std::mem::zeroed() };
    lock.l_type = libc::F_WRLCK as _;
    lock.l_whence = libc::SEEK_SET as _;
    lock.l_len = i64::MAX as _;
    let rc = unsafe { libc::fcntl(file.as_raw_fd(), libc::F_SETLK, &lock) };
    if std::env::var("APD_TEST_LOCK_HELD").unwrap() == "yes" {
        assert_eq!(rc, -1);
        assert!(matches!(
            io::Error::last_os_error().raw_os_error(),
            Some(libc::EAGAIN | libc::EACCES)
        ));
    } else {
        assert_eq!(rc, 0);
    }
}

#[test]
fn config_lock_excludes_other_processes_and_releases_on_drop() {
    let path = std::env::temp_dir().join(format!("apd-record-lock-{}", std::process::id()));
    let lock = lock_config(&path).unwrap();
    let probe = |held: &str| {
        let output = std::process::Command::new(std::env::current_exe().unwrap())
            .args([
                "--exact",
                "package::tests::lock_probe_subprocess",
                "--nocapture",
            ])
            .env("APD_TEST_CONFIG_LOCK", path.with_extension("lock"))
            .env("APD_TEST_LOCK_HELD", held)
            .output()
            .unwrap();
        assert!(
            output.status.success(),
            "{} {}",
            String::from_utf8_lossy(&output.stdout),
            String::from_utf8_lossy(&output.stderr)
        );
    };
    probe("yes");
    drop(lock);
    probe("no");
    fs::remove_file(path.with_extension("lock")).unwrap();
}
