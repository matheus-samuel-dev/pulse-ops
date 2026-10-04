package com.pulseops.security;

import org.springframework.beans.factory.InitializingBean;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

/** Local Compose defaults must never become production credentials accidentally. */
@Component
@Profile("prod")
public class ProductionConfigurationGuard implements InitializingBean {
    private final Environment environment;

    public ProductionConfigurationGuard(Environment environment) {
        this.environment = environment;
    }

    @Override
    public void afterPropertiesSet() {
        String jwt = requireSecret("pulseops.security.jwt.secret");
        String integrations = requireSecret("pulseops.integrations.encryption-key");
        if (jwt.equals(integrations)) {
            throw new IllegalStateException("Use chaves diferentes para JWT e credenciais das integrações em produção");
        }
        String password = environment.getProperty("spring.datasource.password", "");
        if (password.isBlank() || unsafeDefault(password)) {
            throw new IllegalStateException("Configure uma senha própria para o banco de produção");
        }
        if (environment.getProperty("pulseops.security.cors.allowed-origin-patterns", "").contains("*")) {
            throw new IllegalStateException("Configure origens CORS explícitas em produção");
        }
        if (environment.getProperty("pulseops.demo.seed-enabled", Boolean.class, false)
                || environment.getProperty("pulseops.security.outbound.allow-private-networks", Boolean.class, false)) {
            throw new IllegalStateException("Dados demonstrativos e acesso irrestrito a redes privadas são proibidos em produção");
        }
    }

    private String requireSecret(String property) {
        String secret = environment.getProperty(property, "");
        if (secret.length() < 32 || unsafeDefault(secret)) {
            throw new IllegalStateException("Configure uma chave própria de pelo menos 32 caracteres: " + property);
        }
        return secret;
    }

    private boolean unsafeDefault(String value) {
        return value.startsWith("pulseops-compose-") || value.startsWith("pulseops-local-")
                || value.startsWith("replace-") || value.contains("change-before-production");
    }
}
