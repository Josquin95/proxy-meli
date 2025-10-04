package com.mercadolibre.proxy.application;

import com.mercadolibre.proxy.domain.ForwardRequest;
import com.mercadolibre.proxy.domain.ForwardResponse;
import com.mercadolibre.proxy.domain.RequestContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;

public class LoggingForwardingService implements ForwardingService {

    private static final Logger log = LoggerFactory.getLogger(LoggingForwardingService.class);
    private final ForwardingService delegate;

    public LoggingForwardingService(ForwardingService delegate) {
        this.delegate = delegate;
    }

    @Override
    public Mono<ForwardResponse> forward(ForwardRequest request, RequestContext ctx) {
        log.info("[{}:{}] forwarding {}", ctx.traceId(), ctx.reqId(), ctx.targetUrl());
        return delegate.forward(request, ctx);
    }
}
