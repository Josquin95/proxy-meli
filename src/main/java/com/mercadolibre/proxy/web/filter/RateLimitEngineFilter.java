package com.mercadolibre.proxy.web.filter;

import com.mercadolibre.proxy.ratelimit.core.Decision;
import com.mercadolibre.proxy.ratelimit.core.RateLimitRule;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;

@ConditionalOnProperty(name="proxy.rate-limiter.backend") // memory o redis, definido en config
public class RateLimitEngineFilter implements WebFilter {

    private final List<RateLimitRule> rules;

    public RateLimitEngineFilter(List<RateLimitRule> rules) {
        this.rules = rules;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        return Flux.fromIterable(rules)
                .concatMap(rule -> rule.evaluate(exchange))
                .filter(dec -> !dec.allowed())
                .next()
                .flatMap(dec -> {
                    exchange.getResponse().setStatusCode(HttpStatus.TOO_MANY_REQUESTS);
                    dec.retryAfterOpt().ifPresent(sec -> exchange.getResponse().getHeaders().set("Retry-After", String.valueOf(sec)));
                    return exchange.getResponse().setComplete();
                })
                .switchIfEmpty(chain.filter(exchange));
    }
}
