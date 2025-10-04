package com.mercadolibre.proxy.ratelimit.core;

import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

@FunctionalInterface
public interface RateLimitRule {
    Mono<Decision> evaluate(ServerWebExchange exchange);
}
