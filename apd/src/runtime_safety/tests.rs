use super::*;
use anyhow::bail;

#[derive(Default)]
struct Mock {
    values: Vec<String>,
    count: usize,
    fail_at: Option<usize>,
    safe_after: Option<usize>,
    safety_fails: bool,
    recovery_fails: bool,
    restored: usize,
    restored_order: Vec<String>,
}
impl Backend for Mock {
    fn safe(&mut self) -> Result<bool> {
        if self.safety_fails {
            bail!("Safety interface unavailable");
        }
        Ok(self.safe_after.is_some_and(|n| self.count >= n))
    }
    fn validate(&mut self, _: &Change) -> Result<()> {
        Ok(())
    }
    fn apply(&mut self, _: &Change) -> Result<()> {
        self.values.push("changed".into());
        self.count += 1;
        if self.fail_at == Some(self.count) {
            bail!("simulated partial failure");
        }
        Ok(())
    }
    fn restore(&mut self, change: &Change) -> Result<()> {
        if let Change::Property { key, .. } = change {
            self.restored_order.push(key.clone());
        }
        self.restored += 1;
        if self.recovery_fails {
            bail!("simulated recovery failure");
        }
        self.values.pop();
        Ok(())
    }
}
#[derive(Default)]
struct Memory {
    saves: Vec<State>,
    fail_save: bool,
    fail_log: bool,
}
impl Journal for Memory {
    fn save(&mut self, state: &State) -> Result<()> {
        if self.fail_save {
            bail!("disk full");
        }
        self.saves.push(state.clone());
        Ok(())
    }
    fn event(&mut self, _: &str) -> Result<()> {
        if self.fail_log {
            bail!("log write failed");
        }
        Ok(())
    }
}
fn state() -> State {
    State {
        token: "test".into(),
        boot: "boot".into(),
        kind: "hide".into(),
        phase: "queued".into(),
        changes: vec![
            Change::Property {
                key: "one".into(),
                before: "before".into(),
                after: "after".into(),
            },
            Change::Property {
                key: "two".into(),
                before: "before".into(),
                after: "after".into(),
            },
        ],
        attempted: 0,
        deadline: 0,
        owner: None,
        error: None,
        notice: None,
        property_checks: Vec::new(),
    }
}
#[test]
fn records_intent_and_rolls_back_partial_failure() {
    let mut state = state();
    let mut backend = Mock {
        fail_at: Some(2),
        ..Default::default()
    };
    let mut journal = Memory::default();
    assert!(apply(&mut state, &mut backend, &mut journal).is_err());
    assert!(
        journal
            .saves
            .iter()
            .any(|s| s.phase == "applying" && s.attempted == 2)
    );
    assert!(backend.values.is_empty());
    assert_eq!(backend.restored, 2);
    assert_eq!(backend.restored_order, vec!["two", "one"]);
    assert_eq!(state.phase, "off");
}
#[test]
fn safe_mode_blocks_before_apply_and_restores_mid_apply() {
    for limit in [0, 1] {
        let mut state = state();
        let mut backend = Mock {
            safe_after: Some(limit),
            ..Default::default()
        };
        let mut journal = Memory::default();
        assert!(apply(&mut state, &mut backend, &mut journal).is_err());
        assert_eq!(backend.count, limit);
        assert!(backend.values.is_empty());
        assert_eq!(state.phase, "off");
    }
}
#[test]
fn no_mutations_without_durable_record_or_audit() {
    for (fail_save, fail_log) in [(true, false), (false, true)] {
        let mut state = state();
        let mut backend = Mock::default();
        let mut journal = Memory {
            fail_save,
            fail_log,
            ..Default::default()
        };
        assert!(apply(&mut state, &mut backend, &mut journal).is_err());
        assert_eq!(backend.count, 0);
    }
}
#[test]
fn failed_recovery_retains_originals_and_does_not_claim_off() {
    let mut state = state();
    let mut backend = Mock {
        fail_at: Some(1),
        recovery_fails: true,
        ..Default::default()
    };
    let mut journal = Memory::default();
    assert!(apply(&mut state, &mut backend, &mut journal).is_err());
    assert_eq!(state.phase, "recovery_failed");
    assert_eq!(state.attempted, 1);
    assert_eq!(state.changes.len(), 2);
    backend.recovery_fails = false;
    rollback(&mut state, &mut backend, &mut journal).unwrap();
    assert_eq!(state.phase, "off");
}
#[test]
fn expiry_or_dead_supervisor_restores_but_new_boot_never_replays() {
    let mut state = state();
    state.phase = "trial".into();
    state.attempted = 2;
    state.deadline = 1;
    let mut backend = Mock {
        values: vec!["changed".into(); 2],
        ..Default::default()
    };
    let mut journal = Memory::default();
    reconcile(&mut state, &mut backend, &mut journal, "boot", false, 2).unwrap();
    assert_eq!(state.phase, "off");
    assert_eq!(backend.restored, 2);
    state.phase = "active".into();
    state.attempted = 2;
    reconcile(&mut state, &mut backend, &mut journal, "new-boot", false, 2).unwrap();
    assert_eq!(state.phase, "off");
    assert_eq!(backend.restored, 2);
}
#[test]
fn safe_mode_disables_confirmed_session_and_does_not_reenable() {
    let mut state = state();
    state.phase = "active".into();
    state.attempted = 2;
    state.owner = Some((
        std::process::id(),
        platform::process_start(std::process::id()).unwrap(),
    ));
    let mut backend = Mock::default();
    let mut journal = Memory::default();
    reconcile(&mut state, &mut backend, &mut journal, "boot", true, 0).unwrap();
    assert_eq!(state.phase, "off");
    assert_eq!(backend.restored, 2);
    reconcile(&mut state, &mut backend, &mut journal, "boot", false, 0).unwrap();
    assert_eq!(state.phase, "off");
    assert_eq!(backend.count, 0);
}
#[test]
fn allowlist_never_changes_adb_debug_log_or_bootmode() {
    for (key, _) in platform::PROPERTIES {
        assert!(
            !key.contains("adb")
                && !key.contains("debug")
                && !key.contains("log")
                && !key.contains("bootmode")
                && !key.starts_with("persist.")
        );
    }
}

#[test]
fn journal_is_atomic_and_logs_rotate_without_losing_recovery_state() {
    let dir = std::env::temp_dir().join(format!("aster-safety-store-{}", std::process::id()));
    fs::create_dir_all(&dir).unwrap();
    let mut store = Store(dir.clone());
    let state = state();
    store.save(&state).unwrap();
    assert_eq!(store.load().unwrap().unwrap().token, state.token);
    fs::write(dir.join("audit.log"), vec![b'x'; LIMIT as usize]).unwrap();
    store.event("operation\nfailed").unwrap();
    assert!(dir.join("audit.previous.log").exists());
    assert!(
        String::from_utf8(bounded_read(&dir.join("audit.log")).unwrap())
            .unwrap()
            .contains("operation failed")
    );
    fs::write(dir.join("state.tmp"), b"interrupted replacement").unwrap();
    assert_eq!(store.load().unwrap().unwrap().token, state.token);
    fs::write(dir.join("state.json"), b"corrupt").unwrap();
    assert!(store.load().is_err());
    fs::remove_dir_all(dir).unwrap();
}

#[test]
fn unavailable_safety_interface_never_applies_changes() {
    let mut state = state();
    let mut backend = Mock {
        safety_fails: true,
        ..Default::default()
    };
    let mut journal = Memory::default();
    assert!(apply(&mut state, &mut backend, &mut journal).is_err());
    assert_eq!(backend.count, 0);
    assert_eq!(state.phase, "off");
}
