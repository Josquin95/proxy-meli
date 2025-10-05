package com.mercadolibre.proxy;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(exclude = {
        org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration.class,
        org.springframework.boot.autoconfigure.data.redis.RedisReactiveAutoConfiguration.class
})
public class ProxyApplication {

    public static void main(String[] args) {
        System.setProperty("management.server.port", "9091");
        SpringApplication.run(ProxyApplication.class, args);
    }
}
