CREATE UNIQUE INDEX uk_app_users_email_lower ON app_users (LOWER(email));
CREATE UNIQUE INDEX uk_monitored_systems_name_lower ON monitored_systems (LOWER(name));

CREATE INDEX idx_app_users_role ON app_users (role);

CREATE INDEX idx_monitored_systems_status ON monitored_systems (status);
CREATE INDEX idx_monitored_systems_active_environment ON monitored_systems (active, environment);

CREATE INDEX idx_health_checks_system_checked
    ON health_checks (monitored_system_id, checked_at DESC);
CREATE INDEX idx_health_checks_system_success_checked
    ON health_checks (monitored_system_id, success, checked_at DESC);

CREATE INDEX idx_incidents_system_status
    ON incidents (monitored_system_id, status);
CREATE INDEX idx_incidents_status_severity
    ON incidents (status, severity);
CREATE INDEX idx_incidents_started_at ON incidents (started_at DESC);

CREATE INDEX idx_deployments_system_deployed
    ON deployments (monitored_system_id, deployed_at DESC);
CREATE INDEX idx_deployments_status_environment
    ON deployments (status, environment);

CREATE INDEX idx_test_reports_system_generated
    ON test_reports (monitored_system_id, generated_at DESC);

CREATE INDEX idx_notifications_user_read_created
    ON notifications (user_id, is_read, created_at DESC);
CREATE INDEX idx_notifications_type ON notifications (type);
