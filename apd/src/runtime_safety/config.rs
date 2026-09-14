//! Persisted user configuration and the per-boot automatic-activation record.
//!
//! Nothing in this module executes a runtime operation. Writing the config only
//! stores what the user asked for; activation always re-runs a fresh preflight
//! against the *current* boot.

use anyhow::Result;
use serde::{Deserialize, Serialize};

/// How long an automatically applied session must stay up, after the framework
/// reports boot completed, before the boot counts as stable. A reboot inside
/// this window is treated as an interruption: the automatic option is turned
/// off instead of being replayed on the next boot.
pub const STABLE_SECONDS: u64 = 90;

pub const KINDS: [&str; 2] = ["hide", "umount"];

#[derive(Debug, Clone, Default, Serialize, Deserialize, PartialEq, Eq)]
#[serde(default)]
pub struct Config {
    /// Raw multi-line input, kept verbatim so the UI round-trips exactly.
    pub umount_paths: String,
    pub hide_auto: bool,
    pub umount_auto: bool,
    /// Read-only boot probe. Records observations only; never changes state.
    pub boot_probe: bool,
}

impl Config {
    pub fn auto_enabled(&self, kind: &str) -> bool {
        match kind {
            "hide" => self.hide_auto,
            "umount" => self.umount_auto,
            _ => false,
        }
    }

    /// Turn one automatic option off, keeping the configured paths and logs.
    pub fn disable_auto(&mut self, kind: &str) -> bool {
        let flag = match kind {
            "hide" => &mut self.hide_auto,
            "umount" => &mut self.umount_auto,
            _ => return false,
        };
        std::mem::replace(flag, false)
    }

    pub fn enabled_kinds(&self) -> Vec<String> {
        KINDS
            .iter()
            .filter(|kind| self.auto_enabled(kind))
            .map(|kind| (*kind).to_owned())
            .collect()
    }

    /// Validate what can be validated without touching live mounts: the stored
    /// text must parse into 1..=16 distinct, syntactically valid entries.
    pub fn validated_umount_paths(&self) -> Result<Vec<String>> {
        if self.umount_paths.trim().is_empty() {
            return Ok(Vec::new());
        }
        Ok(super::mount::source_lines(&self.umount_paths)?
            .into_iter()
            .map(|(_, source)| source)
            .collect())
    }
}

/// Boot-scoped bookkeeping for automatic activation.
#[derive(Debug, Clone, Default, Serialize, Deserialize, PartialEq, Eq)]
#[serde(default)]
pub struct AutoState {
    /// Boot this record describes.
    pub boot: String,
    /// An automatic attempt was started for this boot; never repeat it.
    pub attempted: bool,
    /// Kinds that were applied but have not yet been observed stable.
    pub pending: Vec<String>,
    pub pending_since: u64,
    /// Whether this boot's automatic handling finished cleanly: a pending
    /// session reached the stability window, or the boot needed no change at
    /// all. Reset for every new boot.
    pub healthy: bool,
    pub last_result: Option<String>,
    pub last_error: Option<String>,
    /// Set when an earlier boot was interrupted, so the manager can explain why
    /// an option turned itself off. Cleared when the option is enabled again.
    pub interruption: Option<String>,
}

impl AutoState {
    /// Kinds that must be disabled because the previous boot did not complete a
    /// session cleanly. Returns an empty list for a clean previous boot.
    pub fn interrupted(&self) -> Vec<String> {
        if !self.pending.is_empty() {
            // Applied but never observed stable: the device went away mid-session.
            return self.pending.clone();
        }
        if self.attempted && !self.healthy {
            // The attempt itself died before reaching a stable state.
            return KINDS.iter().map(|k| (*k).to_owned()).collect();
        }
        Vec::new()
    }
}

/// Lock-free read for the boot-stage scheduler, which must decide whether to
/// spawn anything before it is allowed to take the store lock. Writes are atomic
/// renames, so a plain read sees the old or the new file, never a mixture.
pub(super) fn peek() -> Config {
    let path = std::path::Path::new(super::ROOT).join("config.json");
    super::bounded_read(&path)
        .ok()
        .and_then(|bytes| serde_json::from_slice(&bytes).ok())
        .unwrap_or_default()
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn config_defaults_are_off_and_round_trip() {
        let config = Config::default();
        assert!(!config.hide_auto && !config.umount_auto && !config.boot_probe);
        assert!(config.enabled_kinds().is_empty());
        let json = serde_json::to_vec(&config).unwrap();
        assert_eq!(serde_json::from_slice::<Config>(&json).unwrap(), config);
    }

    #[test]
    fn unknown_or_legacy_fields_fall_back_to_safe_defaults() {
        let config: Config = serde_json::from_str(r#"{"hide_auto":true}"#).unwrap();
        assert!(config.hide_auto);
        assert!(!config.umount_auto && !config.boot_probe);
        assert!(config.umount_paths.is_empty());
    }

    #[test]
    fn disabling_one_kind_never_touches_the_other_or_the_paths() {
        let mut config = Config {
            umount_paths: "mod/system/media/a".into(),
            hide_auto: true,
            umount_auto: true,
            boot_probe: false,
        };
        assert!(config.disable_auto("hide"));
        assert!(!config.hide_auto);
        assert!(config.umount_auto);
        assert_eq!(config.umount_paths, "mod/system/media/a");
        assert!(!config.disable_auto("nonsense"));
    }

    #[test]
    fn interruption_is_reported_for_pending_or_unhealthy_attempts() {
        let mut auto = AutoState::default();
        assert!(auto.interrupted().is_empty());
        auto.attempted = true;
        auto.healthy = true;
        assert!(auto.interrupted().is_empty());
        auto.pending = vec!["umount".into()];
        assert_eq!(auto.interrupted(), vec!["umount".to_owned()]);
        auto.pending.clear();
        auto.healthy = false;
        assert_eq!(auto.interrupted(), vec!["hide".to_owned(), "umount".to_owned()]);
    }

    #[test]
    fn invalid_saved_paths_are_rejected_before_they_can_run_at_boot() {
        let bad = Config {
            umount_paths: "m/system/media/../bin/sh".into(),
            ..Default::default()
        };
        assert!(bad.validated_umount_paths().is_err());
        let empty = Config::default();
        assert!(empty.validated_umount_paths().unwrap().is_empty());
    }
}
