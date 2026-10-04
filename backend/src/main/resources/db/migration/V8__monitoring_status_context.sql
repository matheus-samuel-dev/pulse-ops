ALTER TABLE monitored_systems ADD COLUMN status_reason varchar(512);
ALTER TABLE monitored_systems ADD COLUMN status_changed_at timestamptz;
ALTER TABLE monitored_systems ADD COLUMN last_failure_at timestamptz;
-- Historical failure timestamps come only from persisted observations, never synthetic backfill.
UPDATE monitored_systems s SET last_failure_at = (
  SELECT max(h.checked_at) FROM health_checks h WHERE h.monitored_system_id = s.id AND h.success = false
);
