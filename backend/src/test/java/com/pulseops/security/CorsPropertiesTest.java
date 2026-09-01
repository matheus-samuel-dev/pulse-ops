package com.pulseops.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("CorsProperties")
class CorsPropertiesTest {

    @Test
    void shouldDefaultToLocalDevelopmentOriginsWhenConfigurationIsAbsent() {
        CorsProperties properties = new CorsProperties(null);

        assertThat(properties.allowedOriginPatterns()).containsExactly("http://localhost:*");
    }

    @Test
    void shouldDefaultToLocalDevelopmentOriginsWhenConfigurationIsEmpty() {
        CorsProperties properties = new CorsProperties(List.of());

        assertThat(properties.allowedOriginPatterns()).containsExactly("http://localhost:*");
    }

    @Test
    void shouldDefensivelyCopyConfiguredOrigins() {
        List<String> configuredOrigins = new ArrayList<>(List.of("https://app.pulseops.io"));

        CorsProperties properties = new CorsProperties(configuredOrigins);
        configuredOrigins.add("https://unexpected.example");

        assertThat(properties.allowedOriginPatterns()).containsExactly("https://app.pulseops.io");
        assertThatThrownBy(() -> properties.allowedOriginPatterns().add("https://mutated.example"))
                .isInstanceOf(UnsupportedOperationException.class);
    }
}
