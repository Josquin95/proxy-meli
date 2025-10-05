package com.mercadolibre.proxy.config;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "proxy.rate-limiter")
public record RateLimiterProperties(
        @NotBlank String backend,              // "memory" | "redis"
        @Min(1) int ipPerMinute,               // límite por IP
        @Min(1) int categoriesPerMinute,       // límite global /categories/*
        @Min(1) int itemsIpPerMinute           // límite por IP + /items/*
) {
}
