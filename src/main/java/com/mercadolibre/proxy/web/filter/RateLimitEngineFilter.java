package com.mercadolibre.proxy.web.filter;

import com.mercadolibre.proxy.metrics.ProxyMetrics;
import com.mercadolibre.proxy.ratelimit.core.Decision;
import com.mercadolibre.proxy.ratelimit.core.RateLimitRule;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.Ordered;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;

@ConditionalOnProperty(name = "proxy.rate-limiter.backend")
public class RateLimitEngineFilter implements WebFilter, Ordered {

    private final List<RateLimitRule> rules;
    private final ProxyMetrics metrics;

    public RateLimitEngineFilter(List<RateLimitRule> rules, ProxyMetrics metrics) {
        this.rules = rules;
        this.metrics = metrics;
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE + 10;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        if (HttpMethod.OPTIONS.equals(exchange.getRequest().getMethod()) ||
                exchange.getRequest().getPath().value().startsWith("/actuator")) {
            return chain.filter(exchange);
        }

        Mono<Decision> decisionMono = Flux.fromIterable(rules)
                .concatMap(rule -> rule.evaluate(exchange))
                .filter(dec -> !dec.allowed())
                .next();

        return decisionMono
                .flatMap(dec -> {                // HAY bloqueo
                    var resp = exchange.getResponse();
                    if (resp.isCommitted()) return Mono.empty();
                    // (opcional) aseguras CORS si no corrió antes
                    ensureCorsHeaders(exchange);
                    resp.setStatusCode(HttpStatus.TOO_MANY_REQUESTS);
                    dec.retryAfterOpt().ifPresent(sec -> resp.getHeaders().set("Retry-After", String.valueOf(sec)));
                    try { metrics.incrementRateLimitRejection(); } catch (Exception ignore) {}
                    return resp.setComplete();
                })
                .switchIfEmpty(chain.filter(exchange));
    }

    private void ensureCorsHeaders(ServerWebExchange exchange) {
        var h = exchange.getResponse().getHeaders();
        if (!h.containsKey("Access-Control-Allow-Origin")) {
            h.set("Access-Control-Allow-Origin", "*");
            h.add("Vary", "Origin");
            h.add("Vary", "Access-Control-Request-Method");
            h.add("Vary", "Access-Control-Request-Headers");
        }
    }
}
