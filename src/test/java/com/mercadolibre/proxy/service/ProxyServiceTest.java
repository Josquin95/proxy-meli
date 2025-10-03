package com.mercadolibre.proxy.service;


import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.*;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.test.StepVerifier;

import java.io.IOException;

class ProxyServiceTest {

    private static MockWebServer mockWebServer;
    private ProxyService proxyService;

    @BeforeAll
    static void setUpServer() throws IOException {
        mockWebServer = new MockWebServer();
        mockWebServer.start();
    }

    @AfterAll
    static void tearDownServer() throws IOException {
        mockWebServer.shutdown();
    }

    @BeforeEach
    void setUp() {
        WebClient mockClient = WebClient.builder()
                .baseUrl(mockWebServer.url("/").toString())
                .build();

        proxyService = new ProxyService(mockClient);
    }

    @Test
    void shouldForwardRequestAndReturnResponse() {
        // given
        mockWebServer.enqueue(new MockResponse()
                .setResponseCode(200)
                .setBody("{\"id\":\"MLA123\"}"));

        // when
        StepVerifier.create(proxyService.forwardRequest("/categories/MLA123"))
                .expectNext("{\"id\":\"MLA123\"}")
                .verifyComplete();
    }
}

