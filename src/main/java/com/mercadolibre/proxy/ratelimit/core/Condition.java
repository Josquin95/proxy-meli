package com.mercadolibre.proxy.ratelimit.core;

import org.springframework.util.AntPathMatcher;
import org.springframework.web.server.ServerWebExchange;

import java.util.Objects;
import java.util.Set;
import java.util.function.Predicate;

@FunctionalInterface
public interface Condition extends Predicate<ServerWebExchange> {

    @Override
    boolean test(ServerWebExchange ex);

    default Condition and(Condition other) {
        Objects.requireNonNull(other);
        return ex -> this.test(ex) && other.test(ex);
    }

    default Condition or(Condition other) {
        Objects.requireNonNull(other);
        return ex -> this.test(ex) || other.test(ex);
    }

    default Condition not() { return ex -> !this.test(ex); }

    // Helpers comunes
    static Condition always() { return ex -> true; }
    static Condition methodIs(String m) { return ex -> m.equalsIgnoreCase(ex.getRequest().getMethodValue()); }
    static Condition methodIn(Set<String> ms) { return ex -> ms.contains(ex.getRequest().getMethodValue()); }
    static Condition hasHeader(String name) { return ex -> ex.getRequest().getHeaders().containsKey(name); }
    static Condition pathMatches(String pattern) {
        var m = new AntPathMatcher();
        return ex -> m.match(pattern, ex.getRequest().getPath().value());
    }
    static Condition pathStartsWith(String prefix) {
        return ex -> ex.getRequest().getPath().value().startsWith(prefix);
    }
}
