package com.mercadolibre.proxy.infrastructure.http;

import com.mercadolibre.proxy.domain.ForwardRequest;
import com.mercadolibre.proxy.domain.ForwardResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

@Component
public class WebClientHttpClient implements HttpClientGateway {

    private static final byte[] EMPTY = new byte[0];
    private final WebClient webClient;

    public WebClientHttpClient(WebClient webClient) {
        this.webClient = webClient;
    }

    @Override
    public Mono<ForwardResponse> exchange(ForwardRequest req) {
        boolean hasBody = req.body() != null && req.body().length > 0;
        return webClient.method(req.method())
                .uri(req.targetUri())
                .headers(h -> h.addAll(req.headers()))
                .body(hasBody ? BodyInserters.fromValue(req.body()) : BodyInserters.empty())
                // No excepciones en 4xx/5xx:
                .exchangeToMono(resp -> resp.toEntity(byte[].class))
                .map(ent -> new ForwardResponse(
                        ent.getStatusCode().value(),
                        ent.getHeaders(),
                        ent.getBody() != null ? ent.getBody() : EMPTY
                ));
    }
}
