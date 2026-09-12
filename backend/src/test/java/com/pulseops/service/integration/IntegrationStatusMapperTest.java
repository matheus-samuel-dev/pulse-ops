package com.pulseops.service.integration;

import com.pulseops.domain.system.SystemStatus;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
import static com.pulseops.service.integration.IntegrationServiceTest.*;

class IntegrationStatusMapperTest {
    @Test void normalizesStatusesFromFreshEvidence() {
        var system = system(); var check = check(system, true, NOW);
        assertThat(IntegrationStatusMapper.status(system, check, NOW, Duration.ofMinutes(5))).isEqualTo("ONLINE");
        check.setResponseTimeMs(501);
        assertThat(IntegrationStatusMapper.status(system, check, NOW, Duration.ofMinutes(5))).isEqualTo("ATTENTION");
        check.setResponseTimeMs(100); system.setStatus(SystemStatus.DEGRADED);
        assertThat(IntegrationStatusMapper.status(system, check, NOW, Duration.ofMinutes(5))).isEqualTo("ATTENTION");
        check.setSuccess(false); check.setHttpStatus(403);
        assertThat(IntegrationStatusMapper.status(system, check, NOW, Duration.ofMinutes(5))).isEqualTo("ATTENTION");
        system.setStatus(SystemStatus.DOWN);
        assertThat(IntegrationStatusMapper.status(system, check, NOW, Duration.ofMinutes(5))).isEqualTo("OFFLINE");
        check.setHttpStatus(null);
        assertThat(IntegrationStatusMapper.status(system, check, NOW, Duration.ofMinutes(5))).isEqualTo("OFFLINE");
        check.setHttpStatus(503);
        assertThat(IntegrationStatusMapper.status(system, check, NOW, Duration.ofMinutes(5))).isEqualTo("OFFLINE");
        system.setActive(false);
        assertThat(IntegrationStatusMapper.status(system, check, NOW, Duration.ofMinutes(5))).isEqualTo("UNKNOWN");
        assertThat(IntegrationStatusMapper.reason(system, check, "UNKNOWN")).contains("pausado");
        system.setActive(true); check.setCheckedAt(NOW.plusSeconds(1));
        assertThat(IntegrationStatusMapper.status(system, check, NOW, Duration.ofMinutes(5))).isEqualTo("UNKNOWN");
    }

    @Test void messagesNeverContainRawExceptionsOrSensitiveUrls() {
        var system = system(); var check = check(system, false, NOW);
        check.setErrorMessage("secret http://private/health?apiKey=token");
        assertThat(IntegrationStatusMapper.checkMessage(check)).isEqualTo("Resposta HTTP inesperada: 503");
        check.setHttpStatus(null);
        assertThat(IntegrationStatusMapper.checkMessage(check)).doesNotContain("private", "secret", "token");
        assertThat(IntegrationStatusMapper.reason(system, check, "OFFLINE")).contains("Falha");
        assertThat(IntegrationStatusMapper.reason(system, check, "ATTENTION")).contains("falhas recentes");
    }
}
