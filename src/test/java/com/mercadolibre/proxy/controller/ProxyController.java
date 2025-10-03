package com.mercadolibre.proxy.controller;

import com.mercadolibre.proxy.service.ProxyException;
import com.mercadolibre.proxy.service.ProxyService;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.WebFluxTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Mono;

@WebFluxTest(controllers = ProxyController.class)
@Import(ProxyControllerTest.TestConfig.class)
class ProxyControllerTest {

    @TestConfiguration
    static class TestConfig {
        @Bean
        ProxyService proxyService() {
            return Mockito.mock(ProxyService.class);
        }
    }

    @Autowired
    private WebTestClient webTestClient;

    @Autowired
    private ProxyService proxyService;

    @Test
    void shouldReturnResponseFromService() {
        Mockito.when(proxyService.forwardRequest("categories/MLA123"))
                .thenReturn(Mono.just("{\"id\":\"MLA123\"}"));

        webTestClient.get()
                .uri("/categories/MLA123")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.id").isEqualTo("MLA123");
    }

    @Test
    void shouldReturnNotFoundWhenServiceReturnsEmpty() {
        Mockito.when(proxyService.forwardRequest("categories/NO_EXISTE"))
                .thenReturn(Mono.empty());

        webTestClient.get()
                .uri("/categories/NO_EXISTE")
                .exchange()
                .expectStatus().isNotFound();
    }

    @Test
    void shouldReturnGatewayTimeoutOnDelay() {
        Mockito.when(proxyService.forwardRequest("categories/TIMEOUT"))
                .thenReturn(Mono.error(new java.util.concurrent.TimeoutException("Simulated timeout")));

        webTestClient.get()
                .uri("/categories/TIMEOUT")
                .exchange()
                .expectStatus().isEqualTo(HttpStatus.GATEWAY_TIMEOUT);
    }

    @Test
    void shouldReturnTooManyRequestsWhenRateLimitExceeded() {
        Mockito.when(proxyService.forwardRequest("items/MLA1"))
                .thenReturn(Mono.error(new ProxyException(HttpStatus.TOO_MANY_REQUESTS, "Rate limit exceeded")));

        webTestClient.get()
                .uri("/items/MLA1")
                .exchange()
                .expectStatus().isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
    }
}
