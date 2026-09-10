package com.pulseops.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "pulseops.demo")
public record DemoModeProperties(boolean readOnly) {
}
