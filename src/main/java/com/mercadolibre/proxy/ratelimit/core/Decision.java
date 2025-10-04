package com.mercadolibre.proxy.ratelimit.core;

import java.util.Optional;

public record Decision(boolean allowed, String ruleName, Integer retryAfterSeconds) {
    public static Decision allow() { return new Decision(true, null, null); }
    public static Decision block(String ruleName, Integer retryAfterSeconds) {
        return new Decision(false, ruleName, retryAfterSeconds);
    }
    public Optional<Integer> retryAfterOpt() { return Optional.ofNullable(retryAfterSeconds); }
}
