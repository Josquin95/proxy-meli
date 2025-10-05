package com.mercadolibre.proxy.config;

import com.mercadolibre.proxy.application.ForwardingService;
import com.mercadolibre.proxy.application.LoggingForwardingService;
import com.mercadolibre.proxy.infrastructure.http.HttpClientGateway;
import com.mercadolibre.proxy.infrastructure.http.ResilientHttpClientGateway;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.timelimiter.TimeLimiterRegistry;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.*;

@Configuration
public class ForwardingConfig {

    @Bean
    @Primary
    @ConditionalOnProperty(prefix = "forwarding.resilience", name = "enabled", havingValue = "true", matchIfMissing = true)
    public HttpClientGateway resilientGateway(
            HttpClientGateway baseGateway,
            ForwardingFeaturesProperties props,
            CircuitBreakerRegistry cbRegistry,
            TimeLimiterRegistry tlRegistry
    ) {
        var name = props.resilience().instanceName();
        return new ResilientHttpClientGateway(
                baseGateway,
                cbRegistry.circuitBreaker(name),
                tlRegistry.timeLimiter(name)
        );
    }

    @Bean
    @Primary
    @ConditionalOnProperty(prefix = "forwarding", name = "logging-enabled", havingValue = "true", matchIfMissing = true)
    public ForwardingService loggingForwardingService(@Qualifier("forwardingCore") ForwardingService core) {
        return new LoggingForwardingService(core);
    }

    @Bean
    @Primary
    @ConditionalOnProperty(prefix = "forwarding", name = "logging-enabled", havingValue = "false")
    public ForwardingService primaryCoreForwarding(@Qualifier("forwardingCore") ForwardingService core) {
        return core;
    }
}
