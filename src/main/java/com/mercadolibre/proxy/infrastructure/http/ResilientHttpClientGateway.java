package com.mercadolibre.proxy.infrastructure.http;

import com.mercadolibre.proxy.domain.ForwardRequest;
import com.mercadolibre.proxy.domain.ForwardResponse;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.reactor.circuitbreaker.operator.CircuitBreakerOperator;
import io.github.resilience4j.reactor.timelimiter.TimeLimiterOperator;
import io.github.resilience4j.timelimiter.TimeLimiter;
import reactor.core.publisher.Mono;

public class ResilientHttpClientGateway implements HttpClientGateway {

    private final HttpClientGateway delegate;
    private final CircuitBreaker cb;
    private final TimeLimiter tl;

    public ResilientHttpClientGateway(HttpClientGateway delegate, CircuitBreaker cb, TimeLimiter tl) {
        this.delegate = delegate;
        this.cb = cb;
        this.tl = tl;
    }

    @Override
    public Mono<ForwardResponse> exchange(ForwardRequest request) {
        return delegate.exchange(request)
                .transformDeferred(TimeLimiterOperator.of(tl))
                .transformDeferred(CircuitBreakerOperator.of(cb));
    }
}
