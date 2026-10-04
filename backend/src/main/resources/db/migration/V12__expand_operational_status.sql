-- Configuration is a separate state, rather than measured application downtime.
ALTER TABLE monitored_systems ALTER COLUMN status TYPE VARCHAR(32);
