package com.mercadolibre.proxy.web;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.WebFluxTest;
import org.springframework.test.web.reactive.server.WebTestClient;

@WebFluxTest(controllers = HealthController.class)
class HealthControllerTest {

    @Autowired
    WebTestClient client;

    @Test
    void healthz_returns_ok() {
        client.get().uri("/healthz")
                .exchange()
                .expectStatus().isOk()
                .expectBody(String.class).isEqualTo("ok");
    }
}
