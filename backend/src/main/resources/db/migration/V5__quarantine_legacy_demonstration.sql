ALTER TABLE monitored_systems ADD COLUMN demonstration BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE app_users ADD COLUMN demonstration BOOLEAN NOT NULL DEFAULT FALSE;
-- Exact signatures of the previous automatic seed. Preserve records for inspection,
-- exclude them from normal monitoring and aggregates instead of deleting real data.
UPDATE monitored_systems SET demonstration = TRUE, active = FALSE, status = 'UNKNOWN'
WHERE owner_id IS NULL AND base_url = 'https://example.com' AND health_endpoint = '/'
AND (name, description) IN (
 ('PlaySpace', 'Reserva inteligente de quadras e experiências esportivas.'),
 ('LogiTrack', 'Rastreamento logístico e gestão de entregas em tempo real.'),
 ('Gestão Financeira', 'Consolidação financeira, conciliação e relatórios executivos.'),
 ('AI Web Auditor', 'Auditoria automatizada de qualidade, SEO e acessibilidade.')
);
UPDATE app_users SET demonstration = TRUE
WHERE email IN ('admin@pulseops.dev', 'dev@pulseops.dev', 'viewer@pulseops.dev');
