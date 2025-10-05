package com.mercadolibre.proxy.web.filter;

import com.mercadolibre.proxy.metrics.ProxyMetrics;
import com.mercadolibre.proxy.ratelimit.core.Decision;
import com.mercadolibre.proxy.ratelimit.core.RateLimitRule;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;

class RateLimitEngineFilterTest {

    @Test
    void allowed_path_passes_through() {
        RateLimitRule allowAll = ex -> Mono.just(Decision.allow());
        RateLimitEngineFilter filter = new RateLimitEngineFilter(List.of(allowAll), new ProxyMetrics(new SimpleMeterRegistry()));

        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/echo").build());
        AtomicBoolean chainCalled = new AtomicBoolean(false);
        WebFilterChain chain = ex -> { chainCalled.set(true); return Mono.empty(); };

        filter.filter(exchange, chain).block();

        assertThat(chainCalled).isTrue();
        assertThat(exchange.getResponse().getStatusCode()).isNull();
    }

    @Test
    void blocked_path_returns_429_with_retry_after_and_cors_and_does_not_call_chain() {
        RateLimitRule denyOnBlocked = ex -> {
            if (ex.getRequest().getPath().value().contains("/blocked")) {
                return Mono.just(Decision.block("test", 5));
            }
            return Mono.just(Decision.allow());
        };
        RateLimitEngineFilter filter = new RateLimitEngineFilter(List.of(denyOnBlocked), new ProxyMetrics(new SimpleMeterRegistry()));

        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/blocked").build());
        AtomicBoolean chainCalled = new AtomicBoolean(false);
        WebFilterChain chain = ex -> { chainCalled.set(true); return Mono.empty(); };

        filter.filter(exchange, chain).block();

        assertThat(exchange.getResponse().getStatusCode().value()).isEqualTo(429);
        assertThat(exchange.getResponse().getHeaders().getFirst("Retry-After")).isEqualTo("5");
        assertThat(exchange.getResponse().getHeaders().getFirst("Access-Control-Allow-Origin")).isEqualTo("*");
    }

    @Test
    void options_bypasses_filter_and_calls_chain() {
        RateLimitRule denyAll = ex -> Mono.just(Decision.block("any", 1));
        RateLimitEngineFilter filter = new RateLimitEngineFilter(List.of(denyAll), new ProxyMetrics(new SimpleMeterRegistry()));

        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.method(HttpMethod.OPTIONS, "/any").build());
        AtomicBoolean chainCalled = new AtomicBoolean(false);
        WebFilterChain chain = ex -> { chainCalled.set(true); return Mono.empty(); };

        filter.filter(exchange, chain).block();

        assertThat(chainCalled).isTrue();
        assertThat(exchange.getResponse().getStatusCode()).isNull();
    }
}
