package com.pulseops.security;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.json.JsonMapper;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

@DisplayName("SecurityErrorWriter")
class SecurityErrorWriterTest {

    @Test
    void shouldWriteStandardJsonErrorToServletResponse() throws Exception {
        JsonMapper objectMapper = JsonMapper.builder().findAndAddModules().build();
        SecurityErrorWriter writer = new SecurityErrorWriter(objectMapper);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/users");
        request.setQueryString("page=2");
        MockHttpServletResponse response = new MockHttpServletResponse();

        writer.write(
                request,
                response,
                HttpStatus.FORBIDDEN.value(),
                "Forbidden",
                "Acesso negado para este recurso"
        );

        assertThat(response.getStatus()).isEqualTo(HttpStatus.FORBIDDEN.value());
        assertThat(response.getContentType()).isEqualTo(MediaType.APPLICATION_JSON_VALUE);
        JsonNode body = objectMapper.readTree(response.getContentAsString(StandardCharsets.UTF_8));
        assertThat(body.get("timestamp").asText()).isNotBlank();
        assertThat(body.get("status").asInt()).isEqualTo(403);
        assertThat(body.get("error").asText()).isEqualTo("Forbidden");
        assertThat(body.get("message").asText()).isEqualTo("Acesso negado para este recurso");
        assertThat(body.get("path").asText()).isEqualTo("/api/users");
        assertThat(body.has("validationErrors")).isFalse();
    }
}
