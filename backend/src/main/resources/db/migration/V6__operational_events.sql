CREATE TABLE operational_events (
 id UUID PRIMARY KEY, owner_id UUID REFERENCES app_users(id) ON DELETE CASCADE,
 system_id UUID REFERENCES monitored_systems(id) ON DELETE SET NULL,
 system_name VARCHAR(120) NOT NULL, environment VARCHAR(20) NOT NULL,
 type VARCHAR(40) NOT NULL, severity VARCHAR(20) NOT NULL,
 title VARCHAR(180) NOT NULL, description VARCHAR(2000), source VARCHAR(100) NOT NULL,
 occurred_at TIMESTAMPTZ NOT NULL, created_at TIMESTAMPTZ NOT NULL, updated_at TIMESTAMPTZ NOT NULL
);
CREATE INDEX idx_events_owner_time ON operational_events(owner_id, occurred_at DESC);
CREATE INDEX idx_events_system_time ON operational_events(system_id, occurred_at DESC);
ALTER TABLE incidents ADD COLUMN investigating_at TIMESTAMPTZ;
