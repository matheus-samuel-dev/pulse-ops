package com.pulseops.security;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import static org.assertj.core.api.Assertions.*;

class ProductionConfigurationGuardTest {
    private MockEnvironment configured() {
        return new MockEnvironment()
                .withProperty("pulseops.security.jwt.secret", "a".repeat(40))
                .withProperty("pulseops.integrations.encryption-key", "b".repeat(40))
                .withProperty("spring.datasource.password", "controlled-unit-test-password")
                .withProperty("pulseops.security.cors.allowed-origin-patterns", "https://ops.example.org");
    }

    @Test void acceptsExplicitProductionConfiguration() {
        assertThatCode(() -> new ProductionConfigurationGuard(configured()).afterPropertiesSet()).doesNotThrowAnyException();
    }

    @Test void rejectsKnownDevelopmentCredentialsAndSharedKeysWithoutDisclosingSecrets() {
        var env = configured().withProperty("pulseops.security.jwt.secret", "pulseops-compose-development-secret-change-before-production");
        assertThatThrownBy(() -> new ProductionConfigurationGuard(env).afterPropertiesSet())
                .hasMessageContaining("chave própria").hasMessageNotContaining("pulseops-compose-development");
        env.withProperty("pulseops.security.jwt.secret", "b".repeat(40));
        assertThatThrownBy(() -> new ProductionConfigurationGuard(env).afterPropertiesSet()).hasMessageContaining("diferentes");
    }

    @Test void rejectsUnrestrictedCorsAndDefaultDatabasePassword() {
        var env = configured().withProperty("spring.datasource.password", "pulseops-local-change-before-production");
        assertThatThrownBy(() -> new ProductionConfigurationGuard(env).afterPropertiesSet()).hasMessageContaining("banco");
        env.withProperty("spring.datasource.password", "controlled-unit-test-password").withProperty("pulseops.security.cors.allowed-origin-patterns", "https://*.example.org");
        assertThatThrownBy(() -> new ProductionConfigurationGuard(env).afterPropertiesSet()).hasMessageContaining("CORS");
    }

    @Test void rejectsDemoOrUnrestrictedPrivateNetworkFlags() {
        var env = configured().withProperty("pulseops.demo.seed-enabled", "true");
        assertThatThrownBy(() -> new ProductionConfigurationGuard(env).afterPropertiesSet()).hasMessageContaining("proibidos");
        env.withProperty("pulseops.demo.seed-enabled", "false").withProperty("pulseops.security.outbound.allow-private-networks", "true");
        assertThatThrownBy(() -> new ProductionConfigurationGuard(env).afterPropertiesSet()).hasMessageContaining("proibidos");
    }
}
