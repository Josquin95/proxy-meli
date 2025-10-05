package com.mercadolibre.proxy.config;

import com.mercadolibre.proxy.ratelimit.core.*;
import com.mercadolibre.proxy.ratelimit.core.Condition;
import com.mercadolibre.proxy.ratelimit.memory.MemoryRateLimiterBackend;
import com.mercadolibre.proxy.ratelimit.redis.RedisRateLimiterBackend;
import com.mercadolibre.proxy.web.filter.RateLimitEngineFilter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.*;

import java.util.List;

@Configuration
public class RateLimitConfig {

    public static final String PATH_ITEMS = "/items";

    @Bean
    @ConditionalOnProperty(name="proxy.rate-limiter.backend", havingValue="memory", matchIfMissing = true)
    public RateLimiterBackend memoryBackend() {
        return new MemoryRateLimiterBackend();
    }

    @Bean
    @ConditionalOnProperty(name="proxy.rate-limiter.backend", havingValue="redis")
    public RateLimiterBackend redisBackend(org.springframework.data.redis.core.ReactiveStringRedisTemplate tpl) {
        return new RedisRateLimiterBackend(tpl);
    }

    @Bean
    public RateLimitRule ipRule(RateLimiterBackend backend, RateLimiterProperties props) {
        return RuleBuilder.named("ip")
                .when(Condition.always())
                .key(KeyGenerator.ip())
                .limit(Limit.perMinute(props.ipPerMinute()))
                .backend(backend)
                .build();
    }

    @Bean
    public RateLimitRule categoriesGlobal(RateLimiterBackend backend, RateLimiterProperties props) {
        return RuleBuilder.named("categories")
                .when(Condition.pathStartsWith("/categories"))
                .key(KeyGenerator.constant("categories"))
                .limit(Limit.perMinute(props.categoriesPerMinute()))
                .backend(backend)
                .build();
    }

    @Bean
    public RateLimitRule itemsIp(RateLimiterBackend backend, RateLimiterProperties props) {
        return RuleBuilder.named("items_ip")
                .when(Condition.pathStartsWith(PATH_ITEMS))
                .key(KeyGenerator.compose(KeyGenerator.ip(), KeyGenerator.constant(PATH_ITEMS)))
                .limit(Limit.perMinute(props.itemsIpPerMinute()))
                .backend(backend)
                .build();
    }

    @Bean
    @ConditionalOnProperty(name="proxy.rate-limiter.extra.ip-path-token.enabled", havingValue="true", matchIfMissing = false)
    public RateLimitRule ipPathToken(RateLimiterBackend backend) {
        return RuleBuilder.named("ip_path_token")
                .when(Condition.pathStartsWith("/secure").and(Condition.hasHeader("X-Api-Token")))
                .key(KeyGenerator.compose(KeyGenerator.ip(), KeyGenerator.path(), KeyGenerator.header("X-Api-Token")))
                .limit(Limit.perMinute(50))
                .backend(backend)
                .build();
    }

    @Bean
    @ConditionalOnProperty(name="proxy.rate-limiter.extra.ip-path-method.enabled", havingValue="true", matchIfMissing = false)
    public RateLimitRule ipPathMethod(RateLimiterBackend backend) {
        return RuleBuilder.named("ip_path_method")
                .when(Condition.pathStartsWith(PATH_ITEMS).and(Condition.methodIs(org.springframework.http.HttpMethod.POST)))
                .key(KeyGenerator.compose(KeyGenerator.ip(), KeyGenerator.path(), KeyGenerator.method()))
                .limit(Limit.perMinute(20))
                .backend(backend)
                .build();
    }

    @Bean
    public RateLimitEngineFilter rateLimitEngineFilter(List<RateLimitRule> rules, com.mercadolibre.proxy.metrics.ProxyMetrics metrics) {
        return new RateLimitEngineFilter(rules, metrics);
    }
}
