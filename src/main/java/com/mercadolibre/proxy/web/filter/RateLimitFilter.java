package com.mercadolibre.proxy.web.filter;

import com.mercadolibre.proxy.config.RateLimiterProperties;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import java.net.InetSocketAddress;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * WebFlux filter que aplica rate limits ANTES del Controller.
 * Límites:
 *  - IP: 1000/min (configurable)
 *  - /categories/*: 10000/min (global, configurable)
 *  - IP + /items/*: 10/min (configurable)
 */
@ConditionalOnProperty(name="proxy.rate-limiter.backend", havingValue="memory", matchIfMissing=true)
@Component
public class RateLimitFilter implements WebFilter {

    private final RateLimiterProperties props;

    private final MeterRegistry meter;

    private static final String PROXY_RATE_LIMIT_METRIC = "proxy.ratelimit.blocked";

    private final Map<String, Bucket> ipBuckets = new ConcurrentHashMap<>();
    private final Map<String, Bucket> categoriesBuckets = new ConcurrentHashMap<>();
    private final Map<String, Bucket> itemsIpBuckets = new ConcurrentHashMap<>();

    public RateLimitFilter(RateLimiterProperties props, MeterRegistry meter) {
        this.props = props;
        this.meter = meter;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        final String clientIp = extractClientIp(exchange);
        final String path = exchange.getRequest().getURI().getPath();

        // 1) Límite por IP (1000/min)
        Bucket ipBucket = ipBuckets.computeIfAbsent(clientIp, k -> newBucket(props.ipPerMinute(), Duration.ofMinutes(1)));
        if (!ipBucket.tryConsume(1)) {
            meter.counter(PROXY_RATE_LIMIT_METRIC, "rule", "ip").increment();
            exchange.getResponse().setStatusCode(HttpStatus.TOO_MANY_REQUESTS);
            return exchange.getResponse().setComplete();
        }

        // 2) Límite global por path /categories/*
        if (path.startsWith("/categories")) {
            Bucket catBucket = categoriesBuckets.computeIfAbsent("categories", k -> newBucket(props.categoriesPerMinute(), Duration.ofMinutes(1)));
            if (!catBucket.tryConsume(1)) {
                meter.counter(PROXY_RATE_LIMIT_METRIC, "rule", "categories").increment();
                exchange.getResponse().setStatusCode(HttpStatus.TOO_MANY_REQUESTS);
                return exchange.getResponse().setComplete();
            }
        }

        // 3) Límite por IP + path /items/*
        if (path.startsWith("/items")) {
            String key = clientIp + ":/items";
            Bucket itemsBucket = itemsIpBuckets.computeIfAbsent(key, k -> newBucket(props.itemsIpPerMinute(), Duration.ofMinutes(1)));
            if (!itemsBucket.tryConsume(1)) {
                meter.counter(PROXY_RATE_LIMIT_METRIC, "rule", "items_ip").increment();
                exchange.getResponse().setStatusCode(HttpStatus.TOO_MANY_REQUESTS);
                return exchange.getResponse().setComplete();
            }
        }
        return chain.filter(exchange);
    }

    private Bucket newBucket(int capacity, Duration window) {
        Bandwidth limit = Bandwidth.builder()
                .capacity(capacity)
                .refillIntervally(capacity, window)
                .build();

        return Bucket.builder()
                .addLimit(limit)
                .build();
    }

    private String extractClientIp(ServerWebExchange exchange) {
        return getXForwardedFor(exchange);
    }

    static String getXForwardedFor(ServerWebExchange exchange) {
        String xff = exchange.getRequest().getHeaders().getFirst("X-Forwarded-For");
        if (StringUtils.hasText(xff)) {
            String first = xff.split(",")[0].trim();
            if (StringUtils.hasText(first)) return first;
        }
        InetSocketAddress remote = exchange.getRequest().getRemoteAddress();
        if (remote != null && remote.getAddress() != null) {
            return remote.getAddress().getHostAddress();
        }
        return "unknown";
    }
}
