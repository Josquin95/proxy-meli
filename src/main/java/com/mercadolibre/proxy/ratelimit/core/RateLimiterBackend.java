package com.mercadolibre.proxy.ratelimit.core;

import reactor.core.publisher.Mono;

public interface RateLimiterBackend {
    Mono<Boolean> tryConsume(String key, int permits, Limit limit);
}
