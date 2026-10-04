-- A monitored registration represents one application in one environment.
-- Moving recorded evidence to another environment would falsify historical filters.
CREATE FUNCTION prevent_recorded_environment_change() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
  IF NEW.environment <> OLD.environment AND (
    EXISTS (SELECT 1 FROM health_checks WHERE monitored_system_id=OLD.id) OR
    EXISTS (SELECT 1 FROM incidents WHERE monitored_system_id=OLD.id) OR
    EXISTS (SELECT 1 FROM deployments WHERE monitored_system_id=OLD.id) OR
    EXISTS (SELECT 1 FROM test_reports WHERE monitored_system_id=OLD.id)
  ) THEN
    RAISE EXCEPTION 'O ambiente possui histórico. Cadastre um sistema separado para o novo ambiente.' USING ERRCODE='23514';
  END IF;
  RETURN NEW;
END;
$$;
CREATE TRIGGER preserve_recorded_environment BEFORE UPDATE OF environment ON monitored_systems
  FOR EACH ROW EXECUTE FUNCTION prevent_recorded_environment_change();
