-- Integration activity is ordered by receipt time, independently of report generation time.
CREATE INDEX idx_test_reports_system_created ON test_reports (monitored_system_id, created_at DESC);
