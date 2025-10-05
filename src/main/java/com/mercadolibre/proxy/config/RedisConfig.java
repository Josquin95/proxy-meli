package com.mercadolibre.proxy.config;

import java.time.Duration;

import org.springframework.boot.autoconfigure.data.redis.RedisProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.*;
import org.springframework.data.redis.connection.*;
import org.springframework.data.redis.connection.lettuce.*;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;

@Configuration
@Profile("redis")
@EnableConfigurationProperties(RedisProperties.class)
public class RedisConfig {

    @Bean
    public ReactiveRedisConnectionFactory reactiveRedisConnectionFactory(RedisProperties props) {
        RedisStandaloneConfiguration standalone = new RedisStandaloneConfiguration(
                props.getHost(), props.getPort());
        if (props.getPassword() != null && !props.getPassword().isEmpty()) {
            standalone.setPassword(RedisPassword.of(props.getPassword()));
        }

        LettuceClientConfiguration clientCfg = LettuceClientConfiguration.builder()
                .commandTimeout(props.getTimeout() != null ? props.getTimeout() : Duration.ofSeconds(2))
                .shutdownTimeout(Duration.ofMillis(100))
                .build();

        return new LettuceConnectionFactory(standalone, clientCfg);
    }

    @Bean
    public ReactiveStringRedisTemplate reactiveStringRedisTemplate(ReactiveRedisConnectionFactory cf) {
        return new ReactiveStringRedisTemplate(cf);
    }
}
