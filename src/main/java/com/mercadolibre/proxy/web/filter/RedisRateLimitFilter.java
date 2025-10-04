package com.mercadolibre.proxy.web.filter;

import com.mercadolibre.proxy.config.RateLimiterProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import java.util.Collections;

import static com.mercadolibre.proxy.web.filter.RateLimitFilter.getXForwardedFor;

@Component
@ConditionalOnProperty(name="proxy.rate-limiter.backend", havingValue="redis")
public class RedisRateLimitFilter implements WebFilter {

    private final RateLimiterProperties props;
    private final ReactiveStringRedisTemplate redis;

    public RedisRateLimitFilter(RateLimiterProperties props, ReactiveStringRedisTemplate redis) {
        this.props = props;
        this.redis = redis;
    }

    private static final String LUA_TOKEN = "local c = redis.call('INCR', KEYS[1]); "
            + "if c == 1 then redis.call('PEXPIRE', KEYS[1], ARGV[1]); end; "
            + "if c <= tonumber(ARGV[2]) then return 1 else return 0 end";

    private final DefaultRedisScript<Long> script = new DefaultRedisScript<>(LUA_TOKEN, Long.class);

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        String ip = extractClientIp(exchange);
        String path = exchange.getRequest().getURI().getPath();

        // Ventana de 60s (fixed window por minuto)
        String ttlMs = "60000";

        // 1) Por IP
        Mono<Boolean> ipAllowed = eval("rl:ip:" + ip, ttlMs, String.valueOf(props.ipPerMinute()));

        // 2) Global /categories/*
        Mono<Boolean> catAllowed = path.startsWith("/categories")
                ? eval("rl:path:categories", ttlMs, String.valueOf(props.categoriesPerMinute()))
                : Mono.just(true);

        // 3) IP + /items/*
        Mono<Boolean> itemsAllowed = path.startsWith("/items")
                ? eval("rl:items:ip:" + ip, ttlMs, String.valueOf(props.itemsIpPerMinute()))
                : Mono.just(true);

        return Mono.zip(ipAllowed, catAllowed, itemsAllowed)
                .flatMap(tuple -> {
                    boolean ok = tuple.getT1() && tuple.getT2() && tuple.getT3();
                    if (!ok) {
                        exchange.getResponse().setStatusCode(HttpStatus.TOO_MANY_REQUESTS);
                        return exchange.getResponse().setComplete();
                    }
                    return chain.filter(exchange);
                });
    }

    private Mono<Boolean> eval(String key, String ttlMs, String limit) {
        return redis.execute(script, Collections.singletonList(key), ttlMs, limit)
                .single()
                .map(res -> res != null && res == 1L);
    }

    private String extractClientIp(ServerWebExchange exchange) {
        return getXForwardedFor(exchange);
    }
}
