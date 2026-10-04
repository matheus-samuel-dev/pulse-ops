package com.pulseops.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import com.pulseops.security.IntegrationCredentialCipher;
import com.pulseops.service.integration.NexusEventDelivery;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;

/** Full HTTP, security, services, Flyway and PostgreSQL paths. Private HTTP fixtures exist only in tests. */
@SpringBootTest(properties = {"pulseops.monitoring.enabled=false", "pulseops.security.outbound.allow-private-networks=true",
        "pulseops.integrations.delivery-delay-ms=3600000", "pulseops.integrations.check-cooldown=PT30S"})
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Testcontainers
class OperationalFlowIntegrationTest {
    @Container static final PostgreSQLContainer<?> DB = new PostgreSQLContainer<>("postgres:16-alpine");
    static HttpServer fixture;
    static final AtomicInteger status = new AtomicInteger(200);
    static final AtomicInteger webhookCalls = new AtomicInteger();
    static final String AUDIT_ID = UUID.randomUUID().toString();
    @DynamicPropertySource static void database(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url", DB::getJdbcUrl); r.add("spring.datasource.username", DB::getUsername); r.add("spring.datasource.password", DB::getPassword);
    }
    @BeforeAll static void startFixture() throws Exception {
        fixture = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        fixture.setExecutor(Executors.newVirtualThreadPerTaskExecutor());
        fixture.createContext("/", exchange -> {
            int response = 200; String body = "{}"; String path = exchange.getRequestURI().getPath();
            if(path.equals("/probe")) response=status.get();
            if(path.equals("/slow")) { try { Thread.sleep(350); } catch(InterruptedException e) { Thread.currentThread().interrupt(); } }
            if(path.startsWith("/api/audits")) {
                if(!"Bearer integration-test-secret".equals(exchange.getRequestHeaders().getFirst("Authorization"))) response=401;
                else if(exchange.getRequestMethod().equals("POST")) {
                    String request = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
                    if(!request.contains("\"authorizationConfirmed\":true")) response=400;
                    else { response=201; body="{\"id\":\""+AUDIT_ID+"\",\"status\":\"PENDING\"}"; }
                } else body="{\"id\":\""+AUDIT_ID+"\",\"status\":\"COMPLETED\",\"overallScore\":87}";
            }
            if(path.equals("/webhook")) { webhookCalls.incrementAndGet(); response=202; }
            if(path.equals("/malformed")) body="{\"id\":\"bad\",\"status\":\"UNKNOWN\"}";
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            byte[] bytes=body.getBytes(StandardCharsets.UTF_8);
            try { exchange.sendResponseHeaders(response,bytes.length); exchange.getResponseBody().write(bytes); } finally { exchange.close(); }
        }); fixture.start();
    }
    @AfterAll static void stopFixture(){fixture.stop(0);}
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate jdbc;
    @Autowired NexusEventDelivery delivery;
    @Autowired IntegrationCredentialCipher cipher;
    String token;
    @BeforeEach void account() throws Exception { status.set(200); webhookCalls.set(0); token=register("Operator", UUID.randomUUID()+"@example.org", "Password@2026"); }
    String origin(){return "http://127.0.0.1:"+fixture.getAddress().getPort();}
    String register(String name,String email,String password) throws Exception {return send(post("/api/auth/register"),null,Map.of("name",name,"email",email,"password",password),201).path("token").asText();}
    JsonNode send(MockHttpServletRequestBuilder request,String auth,Object body,int expected) throws Exception {
        if(auth!=null) request.header("Authorization","Bearer "+auth);
        if(body!=null) request.contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsBytes(body));
        var response=mvc.perform(request).andReturn().getResponse();
        assertThat(response.getStatus()).as("HTTP %s",request).isEqualTo(expected);
        return response.getContentAsString().isBlank() ? json.nullNode() : json.readTree(response.getContentAsString());
    }
    Map<String,Object> systemInput(String name,String endpoint) {return Map.of("name",name,"description","Integration test fixture","baseUrl",origin(),"healthEndpoint",endpoint,"environment","PRODUCTION","active",true,"expectedStatusCode",200,"timeoutMs",1000,"latencyThresholdMs",500,"targetAvailability",99.9);}
    String system(String name,String endpoint) throws Exception{return send(post("/api/systems"),token,systemInput(name,endpoint),201).path("id").asText();}
    JsonNode check(String id) throws Exception{return send(post("/api/systems/"+id+"/checks"),token,null,201);}
    void connect(String slug,String remote,String path,String credential,boolean automatic) throws Exception {
        send(put("/api/connections/"+slug),token,Map.of("systemId",remote,"publicUrl","https://auditor.example.org","actionPath",path,"accessToken",credential,"clearToken",false,"autoDispatch",automatic),200);
    }
    @Test void newAccountHasEmptyDashboardAndNoInventedMetrics() throws Exception {
        var summary=send(get("/api/dashboard/summary"),token,null,200);
        assertThat(summary.path("monitoredSystems").asInt()).isZero(); assertThat(summary.path("overallHealth").asText()).isEqualTo("UNKNOWN");
        assertThat(summary.hasNonNull("averageAvailability")).isFalse();
        assertThat(send(get("/api/events"),token,null,200).path("totalElements").asInt()).isZero();
        var me=send(get("/api/account/me"),token,null,200); assertThat(me.path("role").asText()).isEqualTo("DEVELOPER"); assertThat(me.has("password")).isFalse();
    }
    @Test void successfulProbeProducesActualHttpResultAndAvailability() throws Exception {
        String id=system("Operational API","/probe"); JsonNode result=check(id);
        assertThat(result.path("httpStatus").asInt()).isEqualTo(200); assertThat(result.path("success").asBoolean()).isTrue();
        assertThat(result.path("responseTimeMs").asLong()).isGreaterThanOrEqualTo(0);
        var metrics=send(get("/api/systems/"+id+"/metrics"),token,null,200);
        assertThat(metrics.path("availability").path("totalChecks").asInt()).isOne();
        assertThat(metrics.path("availability").hasNonNull("availabilityPercentage")).isFalse();
        for (int i = 0; i < 4; i++) check(id);
        metrics=send(get("/api/systems/"+id+"/metrics"),token,null,200);
        assertThat(metrics.path("availability").path("totalChecks").asInt()).isEqualTo(5);
        assertThat(metrics.path("availability").path("availabilityPercentage").asDouble()).isEqualTo(100);
        assertThat(send(get("/api/events").param("systemId",id),token,null,200).path("content").toString()).contains("HEALTH_CHECK","SYSTEM_CREATED");
    }
    @Test void noCheckMetricsAreAbsentInsteadOfZeroAvailability() throws Exception {
        String id=system("Awaiting probe","/probe"); var metrics=send(get("/api/systems/"+id+"/metrics"),token,null,200);
        assertThat(metrics.path("availability").hasNonNull("availabilityPercentage")).isFalse();
        assertThat(metrics.path("sla").path("status").asText()).isEqualTo("NO_DATA");
    }
    @Test void accountsCannotReadOrMutateEachOthersSystemsHistoryOrIncidents() throws Exception {
        String id=system("Private app","/probe"); check(id); String owner=token;
        String incident=send(post("/api/incidents"),token,Map.of("systemId",id,"title","Private incident","severity","HIGH"),201).path("id").asText();
        token=register("Other operator",UUID.randomUUID()+"@example.org","Password@2026");
        for(String path:new String[]{"/api/systems/"+id,"/api/systems/"+id+"/metrics","/api/systems/"+id+"/checks","/api/incidents?systemId="+id,"/api/events?systemId="+id})send(get(path),token,null,404);
        send(put("/api/systems/"+id),token,systemInput("Hijack","/probe"),404); send(delete("/api/systems/"+id),token,null,404);
        send(patch("/api/incidents/"+incident+"/resolve"),token,null,404);
        assertThat(send(get("/api/systems"),token,null,200)).isEmpty(); assertThat(send(get("/api/incidents"),token,null,200)).isEmpty();
        token=owner; assertThat(send(get("/api/systems/"+id),token,null,200).path("name").asText()).isEqualTo("Private app");
    }
    @Test void editPersistsAndDeletionPreservesEventSnapshots() throws Exception {
        String id=system("Editable","/probe"); var changed=systemInput("Renamed","/probe"); send(put("/api/systems/"+id),token,changed,200);
        assertThat(send(get("/api/systems/"+id),token,null,200).path("name").asText()).isEqualTo("Renamed");
        check(id); send(delete("/api/systems/"+id),token,null,204); send(get("/api/systems/"+id),token,null,404);
        assertThat(send(get("/api/events"),token,null,200).path("content").toString()).contains("SYSTEM_DELETED","Renamed");
    }
    @Test void repeatedFailuresOpenSingleIncidentAtFirstFailureThenRecover() throws Exception {
        String id=system("Unstable","/probe"); status.set(503); String first=check(id).path("checkedAt").asText(); check(id); check(id); check(id);
        var incidents=send(get("/api/incidents").param("systemId",id),token,null,200); assertThat(incidents.size()).isOne();
        assertThat(incidents.get(0).path("automatic").asBoolean()).isTrue();
        assertThat(java.time.OffsetDateTime.parse(incidents.get(0).path("startedAt").asText()).toInstant()).isCloseTo(java.time.OffsetDateTime.parse(first).toInstant(), within(2, java.time.temporal.ChronoUnit.MICROS));
        status.set(200); check(id); check(id);
        assertThat(send(get("/api/incidents").param("systemId",id),token,null,200).get(0).path("status").asText()).isEqualTo("RESOLVED");
        assertThat(send(get("/api/events"),token,null,200).path("content").toString()).contains("SYSTEM_DOWN","SYSTEM_RECOVERED","INCIDENT_RESOLVED");
    }
    @Test void slowEndpointRecordsTimeoutWithoutInventingHttpStatus() throws Exception {
        var input=new java.util.HashMap<>(systemInput("Slow","/slow"));input.put("timeoutMs",100);
        String id=send(post("/api/systems"),token,input,201).path("id").asText(); var result=check(id);
        assertThat(result.path("success").asBoolean()).isFalse(); assertThat(result.hasNonNull("httpStatus")).isFalse();
    }
    @Test void manualIncidentSupportsContextInvestigationAndResolution() throws Exception {
        String id=system("Manual app","/probe"); String incident=send(post("/api/incidents"),token,Map.of("systemId",id,"title","Manual incident","severity","MEDIUM"),201).path("id").asText();
        send(patch("/api/incidents/"+incident),token,Map.of("title","Updated context","description","Investigation notes","severity","HIGH"),200);
        assertThat(send(patch("/api/incidents/"+incident+"/investigating"),token,null,200).hasNonNull("investigatingAt")).isTrue();
        assertThat(send(patch("/api/incidents/"+incident+"/resolve"),token,null,200).hasNonNull("resolvedAt")).isTrue();
    }
    @Test void profilePersistsAndLogoutRevokesJwt() throws Exception {
        var profile=send(get("/api/account/me"),token,null,200);
        token=send(put("/api/account/me"),token,Map.of("name","New real name","email",profile.path("email").asText()),200).path("token").asText();
        assertThat(send(get("/api/account/me"),token,null,200).path("name").asText()).isEqualTo("New real name");
        send(post("/api/account/logout"),token,null,204); send(get("/api/systems"),token,null,401);
    }
    @Test void emailChangeRequiresPasswordAndReissuesSession() throws Exception {
        String email=UUID.randomUUID()+"@example.org"; String previous=token;
        send(put("/api/account/me"),token,Map.of("name","Operator","email",email),422);
        token=send(put("/api/account/me"),token,Map.of("name","Operator","email",email,"currentPassword","Password@2026"),200).path("token").asText();
        send(get("/api/account/me"),previous,null,401); send(get("/api/account/me"),token,null,200);
        send(post("/api/auth/login"),null,Map.of("email",email,"password","wrong-password"),401);
        send(post("/api/auth/login"),null,Map.of("email",email,"password","Password@2026"),200);
    }
    @Test void validationRejectsWeakPasswordsAndDuplicateEmails() throws Exception {
        send(post("/api/auth/register"),null,Map.of("name","A","email","bad","password","short"),400);
        String email=UUID.randomUUID()+"@example.org";register("User",email,"Password@2026");
        send(post("/api/auth/register"),null,Map.of("name","Other","email",email.toUpperCase(),"password","Password@2026"),409);
        send(get("/api/events").param("size","1000"),token,null,400);
    }
    @Test void ssrfBlocksMetadataEvenInPrivateTestEnvironment() throws Exception {
        var input=new java.util.HashMap<>(systemInput("Metadata attack","/latest"));input.put("baseUrl","http://169.254.169.254");
        send(post("/api/systems"),token,input,422);
        input.put("baseUrl","https://user:password@example.org");send(post("/api/systems"),token,input,422);
    }
    @Test void integrationCredentialsAreEncryptedAndAuditResultComesFromRemote() throws Exception {
        String target=system("Audit target","/probe"), remote=system("Auditor","/health");connect("ai-web-auditor",remote,"/api/audits","integration-test-secret",false);
        String encrypted=jdbc.queryForObject("select encrypted_token from integration_connections where system_id=?",String.class,UUID.fromString(remote));
        assertThat(encrypted).doesNotContain("integration-test-secret"); assertThat(cipher.decrypt(encrypted)).isEqualTo("integration-test-secret");
        var config=send(get("/api/connections/ai-web-auditor"),token,null,200); assertThat(config.has("accessToken")).isFalse();
        send(post("/api/connections/ai-web-auditor/actions"),token,Map.of("systemId",target,"authorizationConfirmed",false),422);
        var run=send(post("/api/connections/ai-web-auditor/actions"),token,Map.of("systemId",target,"authorizationConfirmed",true),201);
        assertThat(run.path("state").asText()).isEqualTo("PENDING");
        var result=send(post("/api/connections/runs/"+run.path("id").asText()+"/refresh"),token,null,200);
        assertThat(result.path("state").asText()).isEqualTo("COMPLETED"); assertThat(result.path("overallScore").asInt()).isEqualTo(87);
        assertThat(result.path("reportUrl").asText()).endsWith("/audits/"+AUDIT_ID);
    }
    @Test void integrationWrongTokenRecordsFailureWithoutEchoingSecret() throws Exception {
        String target=system("Target","/probe"),remote=system("Auditor","/health");connect("ai-web-auditor",remote,"/api/audits","wrong-secret",false);
        var result=send(post("/api/connections/ai-web-auditor/actions"),token,Map.of("systemId",target,"authorizationConfirmed",true),201);
        assertThat(result.path("state").asText()).isEqualTo("FAILED");assertThat(result.path("message").asText()).contains("credencial").doesNotContain("wrong-secret");
    }
    @Test void malformedRemoteContractRecordsFailure() throws Exception {
        String target=system("Target","/probe"),remote=system("Auditor","/health");connect("ai-web-auditor",remote,"/malformed","",false);
        assertThat(send(post("/api/connections/ai-web-auditor/actions"),token,Map.of("systemId",target,"authorizationConfirmed",true),201).path("state").asText()).isEqualTo("FAILED");
    }
    @Test void nexusReceivesDurableEventAndCompletionRequiresCallback() throws Exception {
        String target=system("Workflow target","/probe"),remote=system("Nexus","/health");connect("nexus-flow",remote,"/webhook","",true);
        status.set(503);check(target); check(target); check(target); delivery.deliver();
        var list=send(get("/api/connections/runs").param("systemId",target),token,null,200).path("content");assertThat(list.size()).isOne();
        assertThat(list.get(0).path("state").asText()).isEqualTo("ACCEPTED"); assertThat(webhookCalls.get()).isOne();
        String run=list.get(0).path("id").asText();
        assertThat(send(patch("/api/connections/runs/"+run),token,Map.of("state","COMPLETED","externalId","workflow-42"),200).path("state").asText()).isEqualTo("COMPLETED");
        send(patch("/api/connections/runs/"+run),token,Map.of("state","RUNNING"),422); delivery.deliver();assertThat(webhookCalls.get()).isOne();
    }
    @Test void integrationRunCannotBeRefreshedByAnotherAccount() throws Exception {
        String target=system("Target","/probe"),remote=system("Auditor","/health");connect("ai-web-auditor",remote,"/api/audits","integration-test-secret",false);
        String run=send(post("/api/connections/ai-web-auditor/actions"),token,Map.of("systemId",target,"authorizationConfirmed",true),201).path("id").asText();
        token=register("Other",UUID.randomUUID()+"@example.org","Password@2026");send(post("/api/connections/runs/"+run+"/refresh"),token,null,404);
        assertThat(send(get("/api/connections/runs"),token,null,200).path("content")).isEmpty();
    }
    @Test void emptyEvidenceInvariantAcrossEveryReadScreen() throws Exception {
        String id=system("Sem evidência","/probe");
        var detail=send(get("/api/systems/"+id),token,null,200);
        assertThat(detail.path("status").asText()).isEqualTo("UNKNOWN");assertThat(detail.hasNonNull("lastCheck")).isFalse();
        for(String period:new String[]{"24h","7d","30d"}) {
            var metrics=send(get("/api/systems/"+id+"/metrics").param("period",period),token,null,200);
            assertThat(metrics.path("availability").path("totalChecks").asInt()).isZero();assertThat(metrics.path("availability").hasNonNull("availabilityPercentage")).isFalse();
            assertThat(send(get("/api/systems/"+id+"/latency").param("period",period),token,null,200)).isEmpty();
            var dashboard=send(get("/api/dashboard").param("period",period),token,null,200);
            assertThat(dashboard.path("summary").hasNonNull("averageCoverage")).isFalse();assertThat(dashboard.path("summary").hasNonNull("averageAvailability")).isFalse();
            assertThat(dashboard.path("health").get(0).hasNonNull("lastCheckedAt")).isFalse();assertThat(dashboard.path("health").get(0).path("totalChecks").asInt()).isZero();
            var report=send(get("/api/reports/operational").param("period",period).param("systemId",id),token,null,200);
            assertThat(report.path("kpis").path("totalHealthChecks").asInt()).isZero();assertThat(report.path("kpis").hasNonNull("availability")).isFalse();assertThat(report.path("kpis").hasNonNull("averageLineCoverage")).isFalse();
        }
        assertThat(send(get("/api/deployments"),token,null,200)).isEmpty();assertThat(send(get("/api/quality/history"),token,null,200)).isEmpty();
        assertThat(send(get("/api/integrations"),token,null,200).path("summary").path("connected").asInt()).isZero();
    }
    @Test void lastCheckLatencyHistoryAvailabilityAndEventShareEvidence() throws Exception {
        String id=system("Consistência HTTP","/probe");JsonNode last=null;
        for(int i=0;i<5;i++){status.set(i<3?200:503);last=check(id);}
        var detail=send(get("/api/systems/"+id),token,null,200);
        assertThat(detail.path("lastCheck").path("id").asText()).isEqualTo(last.path("id").asText());assertThat(last.path("failureType").asText()).isEqualTo("HTTP_STATUS");
        for(String period:new String[]{"24h","7d","30d"}) {
            var metrics=send(get("/api/systems/"+id+"/metrics").param("period",period),token,null,200);
            var dashboard=send(get("/api/dashboard").param("period",period),token,null,200);
            var report=send(get("/api/reports/operational").param("period",period).param("systemId",id),token,null,200);
            assertThat(metrics.path("availability").path("availabilityPercentage").asDouble()).isEqualTo(60);
            assertThat(report.path("kpis").path("availability")).isEqualTo(dashboard.path("summary").path("averageAvailability"));
            assertThat(dashboard.path("health").get(0).path("uptime")).isEqualTo(metrics.path("availability").path("availabilityPercentage"));
            assertThat(dashboard.path("health").get(0).path("lastCheckedAt").asText()).isEqualTo(detail.path("lastCheck").path("checkedAt").asText());
            assertThat(dashboard.path("health").get(0).path("status")).isEqualTo(detail.path("status"));
            var history=send(get("/api/systems/"+id+"/checks").param("period",period),token,null,200);
            assertThat(history.path("totalElements").asInt()).isEqualTo(5);
            int samples=0;for(var point:send(get("/api/systems/"+id+"/latency").param("period",period),token,null,200))samples+=point.path("samples").asInt();assertThat(samples).isEqualTo(5);
        }
        assertThat(jdbc.queryForObject("select count(*) from operational_events where system_id=? and type='HEALTH_CHECK' and resource_id in(select id from health_checks where monitored_system_id=?)",Long.class,UUID.fromString(id),UUID.fromString(id))).isEqualTo(5);
        assertThat(send(get("/api/incidents").param("systemId",id),token,null,200)).isEmpty();
    }
    @Test void transientFailureStableRecoveryAndAlertOriginsAreLinked() throws Exception {
        String id=system("Incidentes coerentes","/probe");status.set(503);check(id);
        assertThat(send(get("/api/systems/"+id),token,null,200).path("status").asText()).isEqualTo("DEGRADED");
        assertThat(send(get("/api/incidents"),token,null,200)).isEmpty();check(id);assertThat(send(get("/api/incidents"),token,null,200)).isEmpty();check(id);
        var incident=send(get("/api/incidents"),token,null,200).get(0);String incidentId=incident.path("id").asText();
        for(String period:new String[]{"24h","7d","30d"}) {
            assertThat(send(get("/api/dashboard").param("period",period),token,null,200).path("summary").path("openIncidents").asInt()).isOne();
            assertThat(send(get("/api/reports/operational").param("period",period),token,null,200).path("kpis").path("activeIncidents").asInt()).isOne();
            assertThat(send(get("/api/incidents").param("period",period).param("status","OPEN"),token,null,200)).hasSize(1);
        }
        var alerts=send(get("/api/notifications"),token,null,200);JsonNode incidentAlert=null;
        for(var item:alerts.path("items"))if(item.path("type").asText().equals("INCIDENT"))incidentAlert=item;
        assertThat(incidentAlert).isNotNull();assertThat(incidentAlert.path("resourceId").asText()).isEqualTo(incidentId);assertThat(incidentAlert.path("systemId").asText()).isEqualTo(id);assertThat(incidentAlert.hasNonNull("eventId")).isTrue();
        int unread=alerts.path("unreadCount").asInt();String notice=incidentAlert.path("id").asText();send(patch("/api/notifications/"+notice+"/read"),token,null,200);
        assertThat(send(get("/api/notifications"),token,null,200).path("unreadCount").asInt()).isEqualTo(unread-1);send(patch("/api/notifications/"+notice+"/unread"),token,null,200);assertThat(send(get("/api/notifications"),token,null,200).path("unreadCount").asInt()).isEqualTo(unread);
        status.set(200);check(id);assertThat(send(get("/api/incidents"),token,null,200).get(0).path("status").asText()).isEqualTo("OPEN");check(id);
        assertThat(send(get("/api/incidents"),token,null,200).get(0).path("status").asText()).isEqualTo("RESOLVED");assertThat(send(get("/api/dashboard"),token,null,200).path("summary").path("openIncidents").asInt()).isZero();
    }
    @Test void independentIntegrationProbeCannotChangeMonitoredSystemHistory() throws Exception {
        String id=system("Aplicação observada","/probe");
        send(put("/api/connections/ai-web-auditor"),token,Map.of("baseUrl",origin(),"healthEndpoint","/health","timeoutMs",1000,"actionPath","/api/audits","clearToken",false,"autoDispatch",false),200);
        var probe=send(post("/api/integrations/ai-web-auditor/health-check"),token,null,200);assertThat(probe.path("status").asText()).isEqualTo("ONLINE");
        assertThat(send(get("/api/systems"),token,null,200)).hasSize(1);assertThat(send(get("/api/systems/"+id+"/checks"),token,null,200).path("totalElements").asInt()).isZero();
        var config=send(get("/api/connections/ai-web-auditor"),token,null,200);assertThat(config.path("baseUrl").asText()).isEqualTo(origin());
        assertThat(send(post("/api/integrations/ai-web-auditor/health-check"),token,null,200).path("cached").asBoolean()).isTrue();
        assertThat(send(get("/api/integrations/events"),token,null,200)).isNotEmpty();
        assertThat(jdbc.queryForObject("select count(*) from integration_probes where connection_id=(select id from integration_connections where owner_id=(select owner_id from monitored_systems where id=?) and slug='ai-web-auditor')",Long.class,UUID.fromString(id))).isOne();
    }
    @Test void manualDeploymentAndImportedQualityHaveRealProvenance() throws Exception {
        String id=system("Origem verificável","/probe");
        var deployment=send(post("/api/deployments"),token,Map.of("systemId",id,"version","1.2.3","environment","PRODUCTION"),201);String deploymentId=deployment.path("id").asText();assertThat(deployment.path("source").asText()).isEqualTo("MANUAL");
        send(patch("/api/deployments/"+deploymentId+"/start"),token,null,200);deployment=send(patch("/api/deployments/"+deploymentId+"/success"),token,Map.of(),200);assertThat(deployment.hasNonNull("durationSeconds")).isFalse();
        var quality=send(post("/api/quality/reports"),token,Map.of("systemId",id,"totalTests",10,"passedTests",9,"failedTests",1,"skippedTests",0,"lineCoverage",87,"branchCoverage",72),201);
        var dashboard=send(get("/api/dashboard").param("period","24h"),token,null,200);var report=send(get("/api/reports/operational").param("period","24h"),token,null,200);var overview=send(get("/api/quality/overview").param("period","24h"),token,null,200);
        assertThat(dashboard.path("summary").path("averageCoverage")).isEqualTo(report.path("kpis").path("averageLineCoverage"));assertThat(overview.path("averageLineCoverage").asDouble()).isEqualTo(87);assertThat(report.path("kpis").path("totalTests").asInt()).isEqualTo(10);assertThat(report.path("kpis").path("testPassRate").asDouble()).isEqualTo(90);
        assertThat(overview.path("systems").get(0).path("source").asText()).isEqualTo("API_IMPORT");
        assertThat(jdbc.queryForObject("select count(*) from operational_events where resource_id=? and type='DEPLOYMENT_RECORDED'",Long.class,UUID.fromString(deploymentId))).isOne();assertThat(jdbc.queryForObject("select count(*) from operational_events where resource_id=? and type='QUALITY_RECEIVED'",Long.class,UUID.fromString(quality.path("reportId").asText()))).isOne();
        assertThat(report.path("feed").toString()).doesNotContain("GitHub Actions","Pipeline");
    }
    @Test void reportFiltersApplyToAllKpisAndPersistedEvents() throws Exception {
        String prod=system("Produção","/probe"),staging=system("Homologação","/probe");var config=new java.util.HashMap<>(systemInput("Homologação","/probe"));config.put("environment","STAGING");send(put("/api/systems/"+staging),token,config,200);check(prod);check(staging);
        jdbc.update("update health_checks set checked_at=now()-interval '2 days' where monitored_system_id=?",UUID.fromString(staging));jdbc.update("update operational_events set occurred_at=now()-interval '2 days' where system_id=? and type='HEALTH_CHECK'",UUID.fromString(staging));
        for(String period:new String[]{"24h","7d","30d"}){
            var report=send(get("/api/reports/operational").param("period",period).param("systemId",staging).param("environment","STAGING"),token,null,200);int expected=period.equals("24h")?0:1;assertThat(report.path("kpis").path("totalHealthChecks").asInt()).isEqualTo(expected);
            var dashboard=send(get("/api/dashboard").param("period",period).param("environment","STAGING"),token,null,200);assertThat(dashboard.path("health").get(0).path("totalChecks").asInt()).isEqualTo(expected);
            for(var event:report.path("feed")){assertThat(event.path("systemId").asText()).isEqualTo(staging);assertThat(event.path("environment").asText()).isEqualTo("STAGING");}
        }
        String other=register("Outra conta",UUID.randomUUID()+"@example.org","Password@2026");send(get("/api/reports/operational").param("systemId",prod),other,null,404);send(get("/api/quality/overview").param("systemId",prod),other,null,404);
    }
    @Test void recordedEnvironmentCannotBeReassignedAndArchivedEventsDoNotPolluteReports() throws Exception {
        String id=system("Ambiente estável","/probe");check(id);
        var config=new java.util.HashMap<>(systemInput("Ambiente estável","/probe"));config.put("environment","STAGING");
        var error=send(put("/api/systems/"+id),token,config,422);assertThat(error.path("message").asText()).contains("histórico");
        assertThat(send(get("/api/systems/"+id),token,null,200).path("environment").asText()).isEqualTo("PRODUCTION");
        send(delete("/api/systems/"+id),token,null,204);
        var report=send(get("/api/reports/operational"),token,null,200);assertThat(report.path("kpis").path("totalHealthChecks").asInt()).isZero();assertThat(report.path("feed")).isEmpty();
        assertThat(send(get("/api/events"),token,null,200).path("content").toString()).contains("SYSTEM_DELETED");
    }
    @Test void blockedConfigurationIsNotInventedApplicationDowntime() throws Exception {
        String id=system("Configuração bloqueada","/probe");
        // Test-only corruption represents a target whose address becomes forbidden after registration.
        jdbc.update("update monitored_systems set base_url='http://169.254.169.254' where id=?",UUID.fromString(id));
        for(int i=0;i<5;i++) assertThat(check(id).path("failureType").asText()).isEqualTo("SECURITY_POLICY");
        var metrics=send(get("/api/systems/"+id+"/metrics"),token,null,200).path("availability");
        assertThat(metrics.path("totalChecks").asInt()).isEqualTo(5);assertThat(metrics.path("eligibleChecks").asInt()).isZero();assertThat(metrics.path("excludedChecks").asInt()).isEqualTo(5);assertThat(metrics.hasNonNull("availabilityPercentage")).isFalse();
        assertThat(send(get("/api/systems/"+id),token,null,200).path("status").asText()).isEqualTo("CONFIGURATION_REQUIRED");assertThat(send(get("/api/incidents"),token,null,200)).isEmpty();
        assertThat(send(get("/api/dashboard"),token,null,200).path("summary").hasNonNull("averageAvailability")).isFalse();
    }
    @Test void pausedAndMaintenanceSystemsRetainMeasuredHistory() throws Exception {
        String id=system("Pausa legítima","/probe");check(id);var config=new java.util.HashMap<>(systemInput("Pausa legítima","/probe"));config.put("maintenance",true);config.put("monitoringIntervalSeconds",30);send(put("/api/systems/"+id),token,config,200);
        assertThat(send(get("/api/systems/"+id),token,null,200).path("status").asText()).isEqualTo("MAINTENANCE");send(post("/api/systems/"+id+"/checks"),token,null,422);
        assertThat(send(get("/api/dashboard"),token,null,200).path("health").get(0).path("totalChecks").asInt()).isOne();
        config.put("monitoringIntervalSeconds",29);send(put("/api/systems/"+id),token,config,400);
    }

}
