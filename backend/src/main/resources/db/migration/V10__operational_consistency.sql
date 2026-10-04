ALTER TABLE monitored_systems ADD COLUMN monitoring_interval_seconds INTEGER NOT NULL DEFAULT 60;
ALTER TABLE monitored_systems ADD COLUMN maintenance BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE monitored_systems ADD CONSTRAINT ck_system_interval CHECK (monitoring_interval_seconds BETWEEN 30 AND 86400);
ALTER TABLE monitored_systems DROP CONSTRAINT ck_monitored_systems_status;
ALTER TABLE monitored_systems ADD CONSTRAINT ck_monitored_systems_status CHECK (status IN ('OPERATIONAL','DEGRADED','DOWN','UNKNOWN','MAINTENANCE','CONFIGURATION_REQUIRED'));
ALTER TABLE health_checks ADD COLUMN failure_type VARCHAR(30) NOT NULL DEFAULT 'LEGACY_UNCLASSIFIED';
UPDATE health_checks SET failure_type = CASE WHEN success THEN 'NONE' WHEN http_status IS NOT NULL THEN 'HTTP_STATUS' ELSE 'LEGACY_UNCLASSIFIED' END;
ALTER TABLE deployments ADD COLUMN source VARCHAR(40) NOT NULL DEFAULT 'LEGACY_API';
ALTER TABLE deployments ADD COLUMN execution_url VARCHAR(2048);
ALTER TABLE test_reports ADD COLUMN source VARCHAR(40) NOT NULL DEFAULT 'API_IMPORT';
ALTER TABLE operational_events ALTER COLUMN environment DROP NOT NULL;
ALTER TABLE operational_events ADD COLUMN resource_id UUID;
ALTER TABLE operational_events ADD COLUMN status VARCHAR(30);
CREATE INDEX idx_events_resource_type ON operational_events(resource_id, type);
ALTER TABLE notifications ADD COLUMN event_id UUID REFERENCES operational_events(id) ON DELETE SET NULL;
ALTER TABLE notifications ADD COLUMN system_id UUID REFERENCES monitored_systems(id) ON DELETE SET NULL;
ALTER TABLE notifications ADD COLUMN resource_id UUID;

-- Existing explicit connections retain their configuration; an application is no longer their live data source.
ALTER TABLE integration_connections ALTER COLUMN system_id DROP NOT NULL;
ALTER TABLE integration_connections DROP CONSTRAINT integration_connections_system_id_fkey;
ALTER TABLE integration_connections ADD CONSTRAINT integration_connections_system_id_fkey FOREIGN KEY(system_id) REFERENCES monitored_systems(id) ON DELETE SET NULL;
ALTER TABLE integration_connections ADD COLUMN base_url VARCHAR(2048);
ALTER TABLE integration_connections ADD COLUMN health_endpoint VARCHAR(512) NOT NULL DEFAULT '/';
ALTER TABLE integration_connections ADD COLUMN timeout_ms INTEGER NOT NULL DEFAULT 10000;
UPDATE integration_connections c SET base_url = s.base_url, health_endpoint = s.health_endpoint, timeout_ms = s.timeout_ms FROM monitored_systems s WHERE c.system_id = s.id;
ALTER TABLE integration_connections ALTER COLUMN base_url SET NOT NULL;
CREATE TABLE integration_probes (
 id UUID PRIMARY KEY, connection_id UUID NOT NULL REFERENCES integration_connections(id) ON DELETE CASCADE,
 owner_id UUID NOT NULL REFERENCES app_users(id) ON DELETE CASCADE, slug VARCHAR(40) NOT NULL,
 checked_at TIMESTAMPTZ NOT NULL, http_status INTEGER, response_time_ms BIGINT,
 success BOOLEAN NOT NULL, failure_type VARCHAR(30) NOT NULL, message VARCHAR(512) NOT NULL,
 created_at TIMESTAMPTZ NOT NULL, updated_at TIMESTAMPTZ NOT NULL
);
CREATE INDEX idx_integration_probes_connection_time ON integration_probes(connection_id,checked_at DESC);
