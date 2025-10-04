package com.mercadolibre.proxy.ratelimit.core;

import java.time.Duration;

public record Limit(int capacity, Duration window) {
    public static Limit perMinute(int n) { return new Limit(n, Duration.ofMinutes(1)); }
    public static Limit perSeconds(int n, int seconds) { return new Limit(n, Duration.ofSeconds(seconds)); }
}
