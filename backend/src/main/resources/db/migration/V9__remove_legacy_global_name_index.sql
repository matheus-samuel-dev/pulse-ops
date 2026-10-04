-- V2 enforced names globally. Ownership requires uniqueness within each account.
DROP INDEX IF EXISTS uk_monitored_systems_name_lower;
-- V2 already created the normalized email index; remove the redundant V4 copy.
DROP INDEX IF EXISTS uk_users_normalized_email;
