package com.mercadolibre.proxy.web;

import com.mercadolibre.proxy.application.ForwardingService;
import com.mercadolibre.proxy.application.HeaderPolicy;
import com.mercadolibre.proxy.domain.ForwardRequest;
import com.mercadolibre.proxy.domain.ForwardResponse;
import com.mercadolibre.proxy.domain.RequestContext;
import com.mercadolibre.proxy.metrics.ProxyMetrics;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.WebFluxTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Mono;

import java.net.URI;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@WebFluxTest(controllers = ProxyController.class)
@Import({ProxyMetrics.class, ProxyControllerTest.TestConfig.class})
@TestPropertySource(properties = {
        "backend.base-url=https://api.example.com"
})
class ProxyControllerTest {

    @Autowired
    WebTestClient client;

    @MockBean
    ForwardingService forwardingService;

    @MockBean
    HeaderPolicy headerPolicy;

    HttpHeaders backendHeaders;

    @BeforeEach
    void setUp() {
        backendHeaders = new HttpHeaders();
        backendHeaders.add("X-From-Backend", "yes");
        // add hop-by-hop headers to ensure they are removed in response
        backendHeaders.add(HttpHeaders.CONNECTION, "keep-alive");
        backendHeaders.add(HttpHeaders.TRANSFER_ENCODING, "chunked");

        when(headerPolicy.toBackend(any(HttpHeaders.class), any(String.class)))
                .thenAnswer(inv -> new HttpHeaders());

        when(forwardingService.forward(any(ForwardRequest.class), any(RequestContext.class)))
                .thenReturn(Mono.just(new ForwardResponse(
                        HttpStatus.OK.value(),
                        backendHeaders,
                        "hello".getBytes()
                )));
    }

    @Test
    void options_should_return_200_no_body_and_bypass_forward() {
        client.method(HttpMethod.OPTIONS)
                .uri("/any/path")
                .exchange()
                .expectStatus().isOk()
                .expectBody().isEmpty();
        Mockito.verifyNoInteractions(forwardingService);
    }

    @Test
    void get_should_forward_and_sanitize_response_headers() {
        var res = client.get().uri("/items?q=phone")
                .header("X-Request-Id", "req-123")
                .exchange()
                .expectStatus().isOk()
                .expectHeader().doesNotExist(HttpHeaders.CONNECTION)
                .expectHeader().doesNotExist(HttpHeaders.TRANSFER_ENCODING)
                .expectHeader().valueEquals("X-From-Backend", "yes")
                .expectBody(byte[].class)
                .returnResult();

        assertThat(new String(res.getResponseBody())).isEqualTo("hello");

        ArgumentCaptor<ForwardRequest> captor = ArgumentCaptor.forClass(ForwardRequest.class);
        Mockito.verify(forwardingService).forward(captor.capture(), any(RequestContext.class));
        ForwardRequest fr = captor.getValue();
        assertThat(fr.method()).isEqualTo(HttpMethod.GET);
        assertThat(fr.targetUri()).isEqualTo(URI.create("https://api.example.com/items?q=phone"));
    }

    @Test
    void head_should_return_empty_body_even_if_backend_has_body() {
        when(forwardingService.forward(any(ForwardRequest.class), any(RequestContext.class)))
                .thenReturn(Mono.just(new ForwardResponse(
                        HttpStatus.NO_CONTENT.value(),
                        backendHeaders,
                        "ignored".getBytes()
                )));

        client.method(HttpMethod.HEAD).uri("/status")
                .exchange()
                .expectStatus().isNoContent()
                .expectBody().isEmpty();
    }

    @TestConfiguration
    static class TestConfig {
        @Bean
        MeterRegistry meterRegistry() {
            return new SimpleMeterRegistry();
        }
    }
}
