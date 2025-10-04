package com.mercadolibre.proxy.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.reactor.circuitbreaker.operator.CircuitBreakerOperator;
import io.github.resilience4j.reactor.timelimiter.TimeLimiterOperator;
import io.github.resilience4j.timelimiter.TimeLimiter;
import io.github.resilience4j.timelimiter.TimeLimiterRegistry;

import java.net.URI;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import static java.util.Objects.requireNonNullElse;

@Service
public class ProxyService {

    private static final Logger log = LoggerFactory.getLogger(ProxyService.class);

    private static final String RXI = "X-Request-Id";

    private static final byte[] EMPTY_BYTES = new byte[0];

    private final WebClient webClient;
    private final CircuitBreaker cb;
    private final TimeLimiter tl;


    @Value("${backend.base-url}")
    private String backendBaseUrl;

    public ProxyService(WebClient webClient,
                        CircuitBreakerRegistry cbRegistry,
                        TimeLimiterRegistry tlRegistry) {
        this.webClient = webClient;
        this.cb = cbRegistry.circuitBreaker("meliBackend");
        this.tl = tlRegistry.timeLimiter("meliBackend");
    }

    public Mono<ResponseEntity<byte[]>> forwardRequest(ServerWebExchange exchange) {
        RequestContext ctx = createContext(exchange);
        HttpHeaders outbound = buildOutboundHeaders(ctx);
        Mono<byte[]> bodyMono = readBody(exchange);

        return bodyMono
                .flatMap(body -> protect(sendToBackend(ctx, outbound, body)))
                .map(backend -> toClientResponse(ctx, backend))
                .doOnError(e -> log.error("[{}:{}] !! Transport error: {}", ctx.traceId(), ctx.reqId(), e.toString(), e));
    }

    private Mono<ResponseEntity<byte[]>> protect(Mono<ResponseEntity<byte[]>> mono) {
        return mono
                .transformDeferred(TimeLimiterOperator.of(tl))
                .transformDeferred(CircuitBreakerOperator.of(cb));
    }

    private RequestContext createContext(ServerWebExchange exchange) {
        String traceId = UUID.randomUUID().toString().substring(0, 8);

        String rawPath  = exchange.getRequest().getURI().getRawPath();
        String rawQuery = exchange.getRequest().getURI().getRawQuery();
        String targetUrl = backendBaseUrl + rawPath + (rawQuery != null ? "?" + rawQuery : "");

        HttpMethod method = Objects.requireNonNull(exchange.getRequest().getMethod());
        HttpHeaders inHeaders = exchange.getRequest().getHeaders();

        String reqId = inHeaders.getFirst(RXI);
        if (reqId == null || reqId.isBlank()) reqId = UUID.randomUUID().toString();

        long startNanos = System.nanoTime();

        // Log de entrada con headers saneados
        log.info("[{}:{}] -> {} {} headers={}", traceId, reqId, method, targetUrl, sanitizeHeaders(inHeaders));

        return new RequestContext(traceId, reqId, method, targetUrl, inHeaders, startNanos);
    }

    private HttpHeaders buildOutboundHeaders(RequestContext ctx) {
        HttpHeaders out = new HttpHeaders();
        copyIfPresent(ctx.inHeaders(), out, HttpHeaders.ACCEPT);
        copyIfPresent(ctx.inHeaders(), out, HttpHeaders.ACCEPT_LANGUAGE);
        copyIfPresent(ctx.inHeaders(), out, HttpHeaders.ACCEPT_CHARSET);
        copyIfPresent(ctx.inHeaders(), out, HttpHeaders.CONTENT_TYPE);
        copyIfPresent(ctx.inHeaders(), out, HttpHeaders.AUTHORIZATION);
        copyIfPresent(ctx.inHeaders(), out, "User-Agent");
        if (!out.containsKey(HttpHeaders.ACCEPT)) {
            out.set(HttpHeaders.ACCEPT, "application/json");
        }
        out.set(RXI, ctx.reqId()); // Propagar request-id
        return out;
    }

    private Mono<byte[]> readBody(ServerWebExchange exchange) {
        return DataBufferUtils.join(exchange.getRequest().getBody())
                .map((DataBuffer db) -> {
                    try {
                        byte[] bytes = new byte[db.readableByteCount()];
                        db.read(bytes);
                        return bytes;
                    } finally {
                        DataBufferUtils.release(db);
                    }
                })
                .defaultIfEmpty(new byte[0]);
    }

    private Mono<ResponseEntity<byte[]>> sendToBackend(RequestContext ctx, HttpHeaders outbound, byte[] body) {
        return webClient.method(ctx.method())
                .uri(URI.create(ctx.targetUrl()))
                .headers(h -> h.addAll(outbound))
                .body((requiresBody(ctx.method()) && body.length > 0)
                        ? BodyInserters.fromValue(body)
                        : BodyInserters.empty())
                .exchangeToMono(resp -> resp.toEntity(byte[].class));
    }

    private ResponseEntity<byte[]> toClientResponse(RequestContext ctx, ResponseEntity<byte[]> backend) {
        HttpHeaders out = new HttpHeaders();
        backend.getHeaders().forEach(out::addAll);
        removeHopByHop(out);

        out.setCacheControl("no-store, no-cache, must-revalidate");
        out.setPragma("no-cache");
        out.setExpires(0);
        out.set("X-Content-Type-Options", "nosniff");
        out.set("X-Frame-Options", "DENY");
        out.set("X-XSS-Protection", "1; mode=block");
        out.set(RXI, ctx.reqId());

        byte[] body = requireNonNullElse(backend.getBody(), EMPTY_BYTES);
        int sc = backend.getStatusCode().value();
        long ms = (System.nanoTime() - ctx.startNanos()) / 1_000_000;

        String msg = "[{}:{}] <- {} ({} bytes) in {} ms";
        if (sc >= 500)      log.error(msg, ctx.traceId(), ctx.reqId(), backend.getStatusCode(), body.length, ms);
        else if (sc >= 400) log.warn (msg, ctx.traceId(), ctx.reqId(), backend.getStatusCode(), body.length, ms);
        else                log.info (msg, ctx.traceId(), ctx.reqId(), backend.getStatusCode(), body.length, ms);

        return ResponseEntity.status(sc).headers(out).body(body);
    }


    private boolean requiresBody(HttpMethod method) {
        return method == HttpMethod.POST
                || method == HttpMethod.PUT
                || method == HttpMethod.PATCH
                || method == HttpMethod.DELETE;
    }

    private void removeHopByHop(HttpHeaders headers) {
        headers.remove(HttpHeaders.CONNECTION);
        headers.remove(HttpHeaders.TRANSFER_ENCODING);
        headers.remove("Keep-Alive");
        headers.remove("Proxy-Authenticate");
        headers.remove("Proxy-Authorization");
        headers.remove("TE");
        headers.remove("Trailer");
        headers.remove("Upgrade");
    }

    private void copyIfPresent(HttpHeaders src, HttpHeaders dst, String name) {
        List<String> vals = src.get(name);
        if (vals != null && !vals.isEmpty()) dst.addAll(name, vals);
    }

    private HttpHeaders sanitizeHeaders(HttpHeaders src) {
        HttpHeaders safe = new HttpHeaders();
        src.forEach((k, v) -> {
            if (HttpHeaders.AUTHORIZATION.equalsIgnoreCase(k)) {
                safe.add(k, "***");
            } else {
                safe.addAll(k, v);
            }
        });
        return safe;
    }

    private record RequestContext(
            String traceId,
            String reqId,
            HttpMethod method,
            String targetUrl,
            HttpHeaders inHeaders,
            long startNanos
    ) {}
}
