package com.mercadolibre.proxy.ratelimit.core;

import org.springframework.web.server.ServerWebExchange;

@FunctionalInterface
public interface KeyGenerator {
    String key(ServerWebExchange ex);

    static KeyGenerator constant(String c) { return ex -> c; }
    static KeyGenerator ip() {
        return ex -> {
            String xff = ex.getRequest().getHeaders().getFirst("X-Forwarded-For");
            if (xff != null && !xff.isBlank()) return xff.split(",")[0].trim();
            var ra = ex.getRequest().getRemoteAddress();
            return ra != null && ra.getAddress() != null ? ra.getAddress().getHostAddress() : "unknown";
        };
    }
    static KeyGenerator path() {
        return ex -> ex.getRequest().getPath().value();
    }
    static KeyGenerator method() {
        return ex -> ex.getRequest().getMethodValue();
    }
    static KeyGenerator header(String name) {
        return ex -> {
            var v = ex.getRequest().getHeaders().getFirst(name);
            return v != null ? v : "no-header:"+name;
        };
    }
    static KeyGenerator compose(KeyGenerator... parts) {
        return ex -> {
            StringBuilder sb = new StringBuilder();
            for (int i=0;i<parts.length;i++) {
                if (i>0) sb.append('|');
                sb.append(parts[i].key(ex));
            }
            return sb.toString();
        };
    }
}
