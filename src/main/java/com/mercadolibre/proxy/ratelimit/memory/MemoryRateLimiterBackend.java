package com.mercadolibre.proxy.ratelimit.memory;

import com.mercadolibre.proxy.ratelimit.core.Limit;
import com.mercadolibre.proxy.ratelimit.core.RateLimiterBackend;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import reactor.core.publisher.Mono;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class MemoryRateLimiterBackend implements RateLimiterBackend {

    private final Map<String, Bucket> buckets = new ConcurrentHashMap<>();

    @Override
    public Mono<Boolean> tryConsume(String key, int permits, Limit limit) {
        Bucket b = buckets.computeIfAbsent(key, k -> newBucket(limit));
        return Mono.just(b.tryConsume(permits));
    }

    private Bucket newBucket(Limit limit) {
        Bandwidth bw = Bandwidth.builder()
                .capacity(limit.capacity())
                .refillIntervally(limit.capacity(), limit.window())
                .build();
        return Bucket.builder().addLimit(bw).build();
    }
}
