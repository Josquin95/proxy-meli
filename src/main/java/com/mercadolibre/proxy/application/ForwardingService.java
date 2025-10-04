package com.mercadolibre.proxy.application;

import com.mercadolibre.proxy.domain.ForwardRequest;
import com.mercadolibre.proxy.domain.ForwardResponse;
import com.mercadolibre.proxy.domain.RequestContext;
import reactor.core.publisher.Mono;

public interface ForwardingService {
    Mono<ForwardResponse> forward(ForwardRequest request, RequestContext ctx);
}
