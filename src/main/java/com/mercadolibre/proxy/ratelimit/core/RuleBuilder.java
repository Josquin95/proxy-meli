package com.mercadolibre.proxy.ratelimit.core;

import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

public final class RuleBuilder {
    private String name;
    private Condition when = Condition.always();
    private KeyGenerator keygen = KeyGenerator.constant("default");
    private Limit limit = Limit.perMinute(60);
    private RateLimiterBackend backend;

    private RuleBuilder() {}

    public static RuleBuilder named(String name) {
        var b = new RuleBuilder();
        b.name = name; return b;
    }
    public RuleBuilder when(Condition c) { this.when = this.when.and(c); return this; }
    public RuleBuilder key(KeyGenerator k) { this.keygen = k; return this; }
    public RuleBuilder limit(Limit l) { this.limit = l; return this; }
    public RuleBuilder backend(RateLimiterBackend b) { this.backend = b; return this; }

    public RateLimitRule build() {
        if (backend == null) throw new IllegalStateException("backend is required");
        final String rn = name;
        final Condition cond = when;
        final KeyGenerator kg = keygen;
        final Limit lim = limit;

        return (ServerWebExchange ex) -> {
            if (!cond.test(ex)) return Mono.just(Decision.allow());
            String key = rn + ":" + kg.key(ex);
            return backend.tryConsume(key, 1, lim)
                    .map(ok -> ok ? Decision.allow() : Decision.block(rn, 60)); // Retry-After opcional
        };
    }
}
