ALTER TABLE monitored_systems ADD COLUMN owner_id UUID REFERENCES app_users(id) ON DELETE RESTRICT;
ALTER TABLE app_users ADD COLUMN session_version BIGINT NOT NULL DEFAULT 0;
ALTER TABLE monitored_systems DROP CONSTRAINT uk_monitored_systems_name;
CREATE UNIQUE INDEX uk_systems_owner_name ON monitored_systems(owner_id, lower(name)) WHERE owner_id IS NOT NULL;
CREATE UNIQUE INDEX uk_systems_legacy_name ON monitored_systems(lower(name)) WHERE owner_id IS NULL;
CREATE INDEX idx_systems_owner ON monitored_systems(owner_id);
CREATE UNIQUE INDEX uk_users_normalized_email ON app_users(lower(email));
-- Old records are preserved. Only administrators can access unowned legacy records.
-- Demo content is never assigned to newly registered accounts.
