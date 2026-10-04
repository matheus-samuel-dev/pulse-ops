CREATE TABLE integration_connections (
 id UUID PRIMARY KEY, owner_id UUID NOT NULL REFERENCES app_users(id) ON DELETE CASCADE,
 slug VARCHAR(40) NOT NULL, system_id UUID NOT NULL REFERENCES monitored_systems(id) ON DELETE CASCADE,
 public_url VARCHAR(2048), action_path VARCHAR(512) NOT NULL, encrypted_token TEXT,
 auto_dispatch BOOLEAN NOT NULL DEFAULT FALSE,
 created_at TIMESTAMPTZ NOT NULL, updated_at TIMESTAMPTZ NOT NULL,
 CONSTRAINT uk_connections_owner_slug UNIQUE(owner_id, slug)
);
CREATE TABLE integration_runs (
 id UUID PRIMARY KEY, owner_id UUID NOT NULL REFERENCES app_users(id) ON DELETE CASCADE,
 connection_id UUID NOT NULL REFERENCES integration_connections(id) ON DELETE CASCADE,
 system_id UUID REFERENCES monitored_systems(id) ON DELETE SET NULL,
 system_name VARCHAR(120) NOT NULL, slug VARCHAR(40) NOT NULL,
 external_id VARCHAR(120), state VARCHAR(30) NOT NULL, message VARCHAR(1000),
 report_url VARCHAR(2048), overall_score INTEGER, http_status INTEGER, event_id UUID,
 created_at TIMESTAMPTZ NOT NULL, updated_at TIMESTAMPTZ NOT NULL,
 CONSTRAINT ck_integration_run_state CHECK(state IN ('PENDING','RUNNING','COMPLETED','FAILED','CANCELLED','ACCEPTED')),
 CONSTRAINT ck_integration_run_score CHECK(overall_score IS NULL OR overall_score BETWEEN 0 AND 100)
);
CREATE INDEX idx_runs_owner_time ON integration_runs(owner_id, created_at DESC);
CREATE INDEX idx_runs_system_time ON integration_runs(system_id, created_at DESC);
CREATE UNIQUE INDEX uk_runs_connection_event ON integration_runs(connection_id, event_id) WHERE event_id IS NOT NULL;
