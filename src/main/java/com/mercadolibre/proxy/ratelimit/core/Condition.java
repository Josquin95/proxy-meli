package com.mercadolibre.proxy.ratelimit.core;

import org.springframework.http.HttpMethod;
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

    default Condition not() {
        return ex -> !this.test(ex);
    }

    static Condition always() {
        return ex -> true;
    }

    static Condition methodIs(HttpMethod method) {
        return ex -> method.equals(ex.getRequest().getMethod());
    }

    static Condition methodIn(Set<HttpMethod> methods) {
        return ex -> {
            HttpMethod hm = ex.getRequest().getMethod();
            return hm != null && methods.contains(hm);
        };
    }

    static Condition methodIs(String m) {
        return ex -> {
            HttpMethod hm = ex.getRequest().getMethod();
            return hm != null && hm.name().equalsIgnoreCase(m);
        };
    }

    static Condition methodInStrings(Set<String> ms) {
        return ex -> {
            HttpMethod hm = ex.getRequest().getMethod();
            return hm != null && ms.contains(hm.name());
        };
    }

    static Condition hasHeader(String name) {
        return ex -> ex.getRequest().getHeaders().containsKey(name);
    }

    static Condition pathMatches(String pattern) {
        var m = new AntPathMatcher();
        return ex -> m.match(pattern, ex.getRequest().getPath().value());
    }

    static Condition pathStartsWith(String prefix) {
        return ex -> ex.getRequest().getPath().value().startsWith(prefix);
    }
}
