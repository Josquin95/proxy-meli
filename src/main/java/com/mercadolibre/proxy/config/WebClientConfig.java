package com.mercadolibre.proxy.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

@Configuration
public class WebClientConfig {

    @Bean
    public WebClient meliWebClient() {
        return WebClient.builder()
                .baseUrl("https://api.mercadolibre.com")
                .build();
    }
}

