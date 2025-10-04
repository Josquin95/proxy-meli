package com.mercadolibre.proxy.web;

import com.mercadolibre.proxy.application.*;
import com.mercadolibre.proxy.domain.*;
import org.slf4j.Logger; import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.buffer.DataBuffer; import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.http.*; import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.RequestMapping; import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.net.URI; import java.util.Objects; import java.util.UUID;

@RestController
public class ProxyController {
    private static final Logger log = LoggerFactory.getLogger(ProxyController.class);
    private static final String RXI = "X-Request-Id";
    private static final byte[] EMPTY = new byte[0];

    @Value("${backend.base-url}") private String backendBaseUrl;
    private final ForwardingService forwarding; private final HeaderPolicy headerPolicy;

    public ProxyController(ForwardingService forwarding, HeaderPolicy headerPolicy) {
        this.forwarding = forwarding; this.headerPolicy = headerPolicy;
    }

    @RequestMapping("/**")
    public Mono<ResponseEntity<byte[]>> forwardAll(ServerWebExchange ex) {
        var ctx = context(ex);
        return body(ex)
                .map(b -> new ForwardRequest(URI.create(ctx.targetUrl()),
                        ctx.method(),
                        headerPolicy.toBackend(ctx.inboundHeaders(), ctx.reqId()),
                        b))
                .flatMap(req -> forwarding.forward(req, ctx))
                .map(res -> ResponseEntity.status(res.status()).headers(res.headers()).body(res.body()))
                .doOnError(e -> log.error("[{}:{}] transport error: {}", ctx.traceId(), ctx.reqId(), e.toString(), e));
    }

    private RequestContext context(ServerWebExchange ex) {
        String trace = UUID.randomUUID().toString().substring(0, 8);
        var r = ex.getRequest(); var u = r.getURI();
        String target = backendBaseUrl + u.getRawPath() + (u.getRawQuery() != null ? "?"+u.getRawQuery() : "");
        HttpMethod m = Objects.requireNonNull(r.getMethod());
        HttpHeaders in = r.getHeaders();
        String reqId = in.getFirst(RXI); if (!StringUtils.hasText(reqId)) reqId = UUID.randomUUID().toString();
        log.info("[{}:{}] -> {} {} headers={}", trace, reqId, m, target, sanitize(in));
        return new RequestContext(trace, reqId, m, target, in, System.nanoTime());
    }

    private Mono<byte[]> body(ServerWebExchange ex) {
        return DataBufferUtils.join(ex.getRequest().getBody())
                .map((DataBuffer db) -> { try {
                    byte[] bytes = new byte[db.readableByteCount()]; db.read(bytes); return bytes;
                } finally { DataBufferUtils.release(db); }})
                .defaultIfEmpty(EMPTY);
    }

    private HttpHeaders sanitize(HttpHeaders src) {
        HttpHeaders safe = new HttpHeaders();
        src.forEach((k,v) -> safe.add(k, HttpHeaders.AUTHORIZATION.equalsIgnoreCase(k) ? "***" : String.join(",", v)));
        return safe;
    }
}
