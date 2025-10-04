package com.mercadolibre.proxy.infrastructure.http;

import com.mercadolibre.proxy.domain.ForwardRequest;
import com.mercadolibre.proxy.domain.ForwardResponse;
import reactor.core.publisher.Mono;

public interface HttpClientGateway {
    Mono<ForwardResponse> exchange(ForwardRequest request);
}
