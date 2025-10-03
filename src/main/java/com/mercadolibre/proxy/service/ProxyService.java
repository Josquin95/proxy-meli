package com.mercadolibre.proxy.service;


import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.Refill;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeoutException;

@Service
public class ProxyService {

    private final WebClient webClient;

    private final Map<String, Bucket> rateLimiters = new ConcurrentHashMap<>();


    public ProxyService(WebClient webClient) {
        this.webClient = webClient;
    }

    public Mono<String> forwardRequest(String path) {

        Bucket bucket = rateLimiters.computeIfAbsent(path, k -> createBucket());

        if (bucket.tryConsume(1)){
            return webClient.get()
                    .uri("/" + path)
                    .retrieve()
                    .onStatus(HttpStatusCode::is4xxClientError, res -> Mono.error(new RuntimeException("Not found")))
                    .onStatus(HttpStatusCode::is5xxServerError,res -> Mono.error(new RuntimeException("Server error")))
                    .bodyToMono(String.class)
                    .timeout(Duration.ofSeconds(2))
                    .onErrorResume(e -> {
                        if (e instanceof TimeoutException){
                            return Mono.error(new ProxyException(HttpStatus.GATEWAY_TIMEOUT, "TimeOut"));
                        }
                        return Mono.error(new ProxyException(HttpStatus.BAD_GATEWAY, "Downstream error"));
                    });
        }
        else {
            return Mono.error(new ProxyException(HttpStatus.TOO_MANY_REQUESTS, "Rate limit exceeded"));
        }
    }

    private Bucket createBucket() {
        Refill refill = Refill.greedy(10, Duration.ofMinutes(1)); // 10 requests/minuto
        Bandwidth limit = Bandwidth.classic(10, refill);
        return Bucket.builder().addLimit(limit).build();
    }
}
