package com.mercadolibre.proxy.filter;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import org.springframework.beans.factory.annotation.Value;
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
@Component
public class RateLimitFilter implements WebFilter {

    // Puedes parametrizar estos números desde application.yml
    @Value("${proxy.rate-limits.ip-per-minute:1000}")
    private int ipPerMinute;

    @Value("${proxy.rate-limits.categories-per-minute:10000}")
    private int categoriesPerMinute;

    @Value("${proxy.rate-limits.items-ip-per-minute:10}")
    private int itemsIpPerMinute;

    private final Map<String, Bucket> ipBuckets = new ConcurrentHashMap<>();
    private final Map<String, Bucket> categoriesBuckets = new ConcurrentHashMap<>();
    private final Map<String, Bucket> itemsIpBuckets = new ConcurrentHashMap<>();

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        final String clientIp = extractClientIp(exchange);
        final String path = exchange.getRequest().getURI().getPath();

        // 1) Límite por IP (1000/min)
        Bucket ipBucket = ipBuckets.computeIfAbsent(clientIp, k -> newBucket(ipPerMinute, Duration.ofMinutes(1)));
        if (!ipBucket.tryConsume(1)) {
            exchange.getResponse().setStatusCode(HttpStatus.TOO_MANY_REQUESTS);
            return exchange.getResponse().setComplete();
        }

        // 2) Límite global por path /categories/*
        if (path.startsWith("/categories")) {
            Bucket catBucket = categoriesBuckets.computeIfAbsent("categories", k -> newBucket(categoriesPerMinute, Duration.ofMinutes(1)));
            if (!catBucket.tryConsume(1)) {
                exchange.getResponse().setStatusCode(HttpStatus.TOO_MANY_REQUESTS);
                return exchange.getResponse().setComplete();
            }
        }

        // 3) Límite por IP + path /items/*
        if (path.startsWith("/items")) {
            String key = clientIp + ":/items";
            Bucket itemsBucket = itemsIpBuckets.computeIfAbsent(key, k -> newBucket(itemsIpPerMinute, Duration.ofMinutes(1)));
            if (!itemsBucket.tryConsume(1)) {
                exchange.getResponse().setStatusCode(HttpStatus.TOO_MANY_REQUESTS);
                return exchange.getResponse().setComplete();
            }
        }

        // Si pasa todos los límites, continúa al Controller → Service → WebClient
        return chain.filter(exchange);
    }

    private Bucket newBucket(int capacity, Duration window) {
        // API moderna (sin métodos deprecated):
        // capacity = tokens del bucket y refillIntervally = reposición en la ventana
        Bandwidth limit = Bandwidth.builder()
                .capacity(capacity)
                .refillIntervally(capacity, window)
                .build();

        return Bucket.builder()
                .addLimit(limit)
                .build();
    }

    private String extractClientIp(ServerWebExchange exchange) {
        // Intentar X-Forwarded-For si vienes detrás de un proxy/ingress
        String xff = exchange.getRequest().getHeaders().getFirst("X-Forwarded-For");
        if (StringUtils.hasText(xff)) {
            // Toma el primero de la lista
            String first = xff.split(",")[0].trim();
            if (StringUtils.hasText(first)) {
                return first;
            }
        }
        // Fallback a la IP del socket
        InetSocketAddress remote = exchange.getRequest().getRemoteAddress();
        if (remote != null && remote.getAddress() != null) {
            return remote.getAddress().getHostAddress();
        }
        return "unknown";
    }
}
