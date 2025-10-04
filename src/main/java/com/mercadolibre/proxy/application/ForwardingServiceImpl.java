package com.mercadolibre.proxy.application;

import com.mercadolibre.proxy.domain.ForwardRequest;
import com.mercadolibre.proxy.domain.ForwardResponse;
import com.mercadolibre.proxy.domain.RequestContext;
import com.mercadolibre.proxy.infrastructure.http.HttpClientGateway;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Service("forwardingCore")
public class ForwardingServiceImpl implements ForwardingService {

    private static final Logger log = LoggerFactory.getLogger(ForwardingServiceImpl.class);
    private static final byte[] EMPTY = new byte[0];

    private final HttpClientGateway http;
    private final HeaderPolicy headerPolicy;

    public ForwardingServiceImpl(HttpClientGateway http, HeaderPolicy headerPolicy) {
        this.http = http;
        this.headerPolicy = headerPolicy;
    }

    @Override
    public Mono<ForwardResponse> forward(ForwardRequest req, RequestContext ctx) {
        long t0 = ctx.startNanos();
        return http.exchange(req)
                .map(raw -> {
                    var clientHeaders = headerPolicy.toClient(raw.headers(), ctx.reqId());
                    var body = raw.body() != null ? raw.body() : EMPTY;
                    long ms = (System.nanoTime() - t0) / 1_000_000;
                    String pat = "[{}:{}] <- {} ({} bytes) in {} ms";
                    if (raw.status() >= 500)      log.error(pat, ctx.traceId(), ctx.reqId(), raw.status(), body.length, ms);
                    else if (raw.status() >= 400) log.warn (pat, ctx.traceId(), ctx.reqId(), raw.status(), body.length, ms);
                    else                          log.info (pat, ctx.traceId(), ctx.reqId(), raw.status(), body.length, ms);
                    return new ForwardResponse(raw.status(), clientHeaders, body);
                });
    }
}
