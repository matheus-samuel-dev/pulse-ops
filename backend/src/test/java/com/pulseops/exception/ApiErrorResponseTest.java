package com.pulseops.exception;

import static org.assertj.core.api.Assertions.assertThat;

import com.pulseops.dto.error.ApiErrorResponse;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("ApiErrorResponse")
class ApiErrorResponseTest {

    @Test
    void shouldBuildStandardErrorWithoutValidationDetails() {
        Instant beforeCreation = Instant.now();

        ApiErrorResponse response = ApiErrorResponse.of(
                404,
                "Not Found",
                "Sistema não encontrado",
                "/api/systems/42"
        );

        assertThat(response.timestamp()).isBetween(beforeCreation, Instant.now());
        assertThat(response.status()).isEqualTo(404);
        assertThat(response.error()).isEqualTo("Not Found");
        assertThat(response.message()).isEqualTo("Sistema não encontrado");
        assertThat(response.path()).isEqualTo("/api/systems/42");
        assertThat(response.validationErrors()).isNull();
    }
}
