package com.mercadolibre.proxy.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "forwarding")
public record ForwardingFeaturesProperties(
        boolean loggingEnabled,
        Resilience resilience
) {
    public record Resilience(boolean enabled, String instanceName) {
    }
}
