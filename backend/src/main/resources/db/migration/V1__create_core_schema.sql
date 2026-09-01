CREATE TABLE app_users (
    id UUID PRIMARY KEY,
    name VARCHAR(120) NOT NULL,
    email VARCHAR(254) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    role VARCHAR(20) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uk_app_users_email UNIQUE (email),
    CONSTRAINT ck_app_users_role CHECK (role IN ('ADMIN', 'DEVELOPER', 'VIEWER'))
);

CREATE TABLE monitored_systems (
    id UUID PRIMARY KEY,
    name VARCHAR(120) NOT NULL,
    description VARCHAR(1000),
    base_url VARCHAR(2048) NOT NULL,
    health_endpoint VARCHAR(512) NOT NULL,
    environment VARCHAR(20) NOT NULL,
    status VARCHAR(20) NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    expected_status_code INTEGER NOT NULL DEFAULT 200,
    timeout_ms INTEGER NOT NULL DEFAULT 5000,
    latency_threshold_ms BIGINT NOT NULL DEFAULT 1000,
    target_availability NUMERIC(6, 3) NOT NULL DEFAULT 99.900,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uk_monitored_systems_name UNIQUE (name),
    CONSTRAINT ck_monitored_systems_environment
        CHECK (environment IN ('PRODUCTION', 'STAGING', 'DEVELOPMENT')),
    CONSTRAINT ck_monitored_systems_status
        CHECK (status IN ('OPERATIONAL', 'DEGRADED', 'DOWN', 'UNKNOWN')),
    CONSTRAINT ck_monitored_systems_expected_status CHECK (expected_status_code BETWEEN 100 AND 599),
    CONSTRAINT ck_monitored_systems_timeout CHECK (timeout_ms > 0),
    CONSTRAINT ck_monitored_systems_latency_threshold CHECK (latency_threshold_ms > 0),
    CONSTRAINT ck_monitored_systems_target_availability CHECK (target_availability BETWEEN 0 AND 100)
);

CREATE TABLE health_checks (
    id UUID PRIMARY KEY,
    monitored_system_id UUID NOT NULL,
    checked_at TIMESTAMPTZ NOT NULL,
    http_status INTEGER,
    response_time_ms BIGINT NOT NULL,
    success BOOLEAN NOT NULL,
    error_message VARCHAR(2000),
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT fk_health_checks_system FOREIGN KEY (monitored_system_id)
        REFERENCES monitored_systems (id) ON DELETE CASCADE,
    CONSTRAINT ck_health_checks_http_status CHECK (http_status IS NULL OR http_status BETWEEN 100 AND 599),
    CONSTRAINT ck_health_checks_response_time CHECK (response_time_ms >= 0)
);

CREATE TABLE incidents (
    id UUID PRIMARY KEY,
    monitored_system_id UUID NOT NULL,
    title VARCHAR(180) NOT NULL,
    description VARCHAR(4000),
    severity VARCHAR(20) NOT NULL,
    status VARCHAR(20) NOT NULL,
    started_at TIMESTAMPTZ NOT NULL,
    resolved_at TIMESTAMPTZ,
    automatic BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT fk_incidents_system FOREIGN KEY (monitored_system_id)
        REFERENCES monitored_systems (id) ON DELETE CASCADE,
    CONSTRAINT ck_incidents_severity CHECK (severity IN ('LOW', 'MEDIUM', 'HIGH', 'CRITICAL')),
    CONSTRAINT ck_incidents_status CHECK (status IN ('OPEN', 'INVESTIGATING', 'RESOLVED')),
    CONSTRAINT ck_incidents_resolution_time CHECK (resolved_at IS NULL OR resolved_at >= started_at)
);

CREATE TABLE deployments (
    id UUID PRIMARY KEY,
    monitored_system_id UUID NOT NULL,
    version VARCHAR(80) NOT NULL,
    environment VARCHAR(20) NOT NULL,
    status VARCHAR(20) NOT NULL,
    deployed_at TIMESTAMPTZ NOT NULL,
    duration_seconds BIGINT,
    commit_hash VARCHAR(64),
    description VARCHAR(2000),
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT fk_deployments_system FOREIGN KEY (monitored_system_id)
        REFERENCES monitored_systems (id) ON DELETE CASCADE,
    CONSTRAINT ck_deployments_environment
        CHECK (environment IN ('PRODUCTION', 'STAGING', 'DEVELOPMENT')),
    CONSTRAINT ck_deployments_status
        CHECK (status IN ('PENDING', 'RUNNING', 'SUCCESS', 'FAILED', 'ROLLED_BACK')),
    CONSTRAINT ck_deployments_duration CHECK (duration_seconds IS NULL OR duration_seconds >= 0)
);

CREATE TABLE test_reports (
    id UUID PRIMARY KEY,
    monitored_system_id UUID NOT NULL,
    total_tests INTEGER NOT NULL,
    passed_tests INTEGER NOT NULL,
    failed_tests INTEGER NOT NULL,
    skipped_tests INTEGER NOT NULL,
    line_coverage NUMERIC(5, 2) NOT NULL,
    branch_coverage NUMERIC(5, 2) NOT NULL,
    generated_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT fk_test_reports_system FOREIGN KEY (monitored_system_id)
        REFERENCES monitored_systems (id) ON DELETE CASCADE,
    CONSTRAINT ck_test_reports_counts_non_negative
        CHECK (total_tests >= 0 AND passed_tests >= 0 AND failed_tests >= 0 AND skipped_tests >= 0),
    CONSTRAINT ck_test_reports_counts_consistent
        CHECK (passed_tests + failed_tests + skipped_tests = total_tests),
    CONSTRAINT ck_test_reports_line_coverage CHECK (line_coverage BETWEEN 0 AND 100),
    CONSTRAINT ck_test_reports_branch_coverage CHECK (branch_coverage BETWEEN 0 AND 100)
);

CREATE TABLE notifications (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL,
    title VARCHAR(180) NOT NULL,
    message VARCHAR(2000) NOT NULL,
    type VARCHAR(20) NOT NULL,
    is_read BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT fk_notifications_user FOREIGN KEY (user_id)
        REFERENCES app_users (id) ON DELETE CASCADE,
    CONSTRAINT ck_notifications_type
        CHECK (type IN ('INFO', 'SUCCESS', 'WARNING', 'ERROR', 'INCIDENT', 'DEPLOYMENT', 'QUALITY'))
);
