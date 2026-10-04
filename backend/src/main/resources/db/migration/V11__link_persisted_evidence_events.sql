-- Rebuild only unlinked projections whose originating rows still exist.
-- Deleted-resource snapshots and user context/configuration events are preserved.
DELETE FROM operational_events WHERE resource_id IS NULL
 AND type IN ('HEALTH_CHECK','INCIDENT_OPENED','INCIDENT_INVESTIGATING','INCIDENT_RESOLVED')
 AND system_id IN (SELECT id FROM monitored_systems WHERE demonstration = FALSE);

INSERT INTO operational_events (id,owner_id,system_id,system_name,environment,type,severity,title,description,source,occurred_at,created_at,updated_at,resource_id,status)
SELECT h.id,s.owner_id,s.id,s.name,s.environment,'HEALTH_CHECK',CASE WHEN h.success THEN 'SUCCESS' ELSE 'WARNING' END,
 'Verificação executada',CASE WHEN h.http_status IS NOT NULL THEN 'HTTP '||h.http_status||' · '||h.response_time_ms||' ms' ELSE LEFT(h.error_message,2000) END,
 'Histórico persistido de monitoramento',h.checked_at,h.created_at,h.updated_at,h.id,CASE WHEN h.success THEN 'SUCCESS' ELSE 'FAILED' END
FROM health_checks h JOIN monitored_systems s ON s.id=h.monitored_system_id WHERE s.demonstration=FALSE;

INSERT INTO operational_events (id,owner_id,system_id,system_name,environment,type,severity,title,description,source,occurred_at,created_at,updated_at,resource_id,status)
SELECT i.id,s.owner_id,s.id,s.name,s.environment,'INCIDENT_OPENED','WARNING','Incidente aberto',i.title,
 CASE WHEN i.automatic THEN 'Monitoramento PulseOps' ELSE 'Registro manual via API' END,i.created_at,i.created_at,i.updated_at,i.id,'OPEN'
FROM incidents i JOIN monitored_systems s ON s.id=i.monitored_system_id WHERE s.demonstration=FALSE;

INSERT INTO operational_events (id,owner_id,system_id,system_name,environment,type,severity,title,description,source,occurred_at,created_at,updated_at,resource_id,status)
SELECT gen_random_uuid(),s.owner_id,s.id,s.name,s.environment,'INCIDENT_INVESTIGATING','WARNING','Investigação iniciada',i.title,
 'Histórico persistido de incidentes',i.investigating_at,i.created_at,i.updated_at,i.id,'INVESTIGATING'
FROM incidents i JOIN monitored_systems s ON s.id=i.monitored_system_id WHERE s.demonstration=FALSE AND i.investigating_at IS NOT NULL;

INSERT INTO operational_events (id,owner_id,system_id,system_name,environment,type,severity,title,description,source,occurred_at,created_at,updated_at,resource_id,status)
SELECT gen_random_uuid(),s.owner_id,s.id,s.name,s.environment,'INCIDENT_RESOLVED','SUCCESS','Incidente resolvido',i.title,
 'Histórico persistido de incidentes',i.resolved_at,i.created_at,i.updated_at,i.id,'RESOLVED'
FROM incidents i JOIN monitored_systems s ON s.id=i.monitored_system_id WHERE s.demonstration=FALSE AND i.resolved_at IS NOT NULL;

INSERT INTO operational_events (id,owner_id,system_id,system_name,environment,type,severity,title,description,source,occurred_at,created_at,updated_at,resource_id,status)
SELECT d.id,s.owner_id,s.id,s.name,d.environment,'DEPLOYMENT_RECORDED','INFO','Registro de implantação',d.version||' · origem: '||d.source,
 'Registro recebido pela API',d.created_at,d.created_at,d.updated_at,d.id,NULL
FROM deployments d JOIN monitored_systems s ON s.id=d.monitored_system_id WHERE s.demonstration=FALSE;

INSERT INTO operational_events (id,owner_id,system_id,system_name,environment,type,severity,title,description,source,occurred_at,created_at,updated_at,resource_id,status)
SELECT r.id,s.owner_id,s.id,s.name,s.environment,'QUALITY_RECEIVED','INFO','Relatório de testes recebido',r.total_tests||' testes · origem: '||r.source,
 'Relatório importado via API',r.created_at,r.created_at,r.updated_at,r.id,NULL
FROM test_reports r JOIN monitored_systems s ON s.id=r.monitored_system_id WHERE s.demonstration=FALSE;

CREATE UNIQUE INDEX uk_events_evidence_creation ON operational_events(resource_id,type)
 WHERE resource_id IS NOT NULL AND type IN ('HEALTH_CHECK','INCIDENT_OPENED','QUALITY_RECEIVED','DEPLOYMENT_RECORDED');
