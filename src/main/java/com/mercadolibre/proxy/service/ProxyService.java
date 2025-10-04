package com.mercadolibre.proxy.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.net.URI;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Service
public class ProxyService {

    private static final Logger log = LoggerFactory.getLogger(ProxyService.class);

    private final WebClient webClient;

    @Value("${backend.base-url}")
    private String backendBaseUrl;

    @Value("${proxy.max-redirects:5}")
    private int maxRedirects;

    public ProxyService(WebClient webClient) {
        this.webClient = webClient;
    }

    public Mono<ResponseEntity<byte[]>> forwardRequest(ServerWebExchange exchange) {
        String traceId = UUID.randomUUID().toString().substring(0, 8);
        final long t0 = System.nanoTime();

        String rawPath  = exchange.getRequest().getURI().getRawPath();
        String rawQuery = exchange.getRequest().getURI().getRawQuery();
        String targetUrl = backendBaseUrl + rawPath + (rawQuery != null ? "?" + rawQuery : "");

        HttpMethod method = Objects.requireNonNull(exchange.getRequest().getMethod());
        HttpHeaders inHeaders = exchange.getRequest().getHeaders();

        log.info("[{}] -> {} {}", traceId, method, targetUrl);

        HttpHeaders outbound = new HttpHeaders();
        copyIfPresent(inHeaders, outbound, HttpHeaders.ACCEPT);
        copyIfPresent(inHeaders, outbound, HttpHeaders.ACCEPT_LANGUAGE);
        copyIfPresent(inHeaders, outbound, HttpHeaders.ACCEPT_CHARSET);
        copyIfPresent(inHeaders, outbound, HttpHeaders.CONTENT_TYPE);
        copyIfPresent(inHeaders, outbound, HttpHeaders.AUTHORIZATION);
        copyIfPresent(inHeaders, outbound, "User-Agent");

        if (!outbound.containsKey(HttpHeaders.ACCEPT)) {
            outbound.set(HttpHeaders.ACCEPT, "application/json");
        }

        Mono<byte[]> bodyMono = DataBufferUtils.join(exchange.getRequest().getBody())
                .map(dataBuffer -> {
                    try {
                        byte[] bytes = new byte[dataBuffer.readableByteCount()];
                        dataBuffer.read(bytes);
                        return bytes;
                    } finally {
                        DataBufferUtils.release(dataBuffer);
                    }
                })
                .defaultIfEmpty(new byte[0]);

        return bodyMono.flatMap(body ->
                webClient.method(method)
                        .uri(URI.create(targetUrl))
                        .headers(h -> h.addAll(outbound))
                        .body((requiresBody(method) && body.length > 0) ? BodyInserters.fromValue(body) : BodyInserters.empty())
                        .retrieve()
                        .toEntity(byte[].class)
                        .map(backendEntity -> {
                            HttpHeaders out = new HttpHeaders();
                            backendEntity.getHeaders().forEach(out::addAll);
                            removeHopByHop(out);

                            out.setCacheControl("no-store, no-cache, must-revalidate");
                            out.setPragma("no-cache");
                            out.setExpires(0);

                            byte[] respBody = backendEntity.getBody() != null ? backendEntity.getBody() : new byte[0];

                            long ms = (System.nanoTime() - t0) / 1_000_000;
                            log.info("[{}] <- {} ({} bytes) in {} ms", traceId, backendEntity.getStatusCode(), respBody.length, ms);

                            return ResponseEntity
                                    .status(backendEntity.getStatusCode().value())
                                    .headers(out)
                                    .body(respBody);
                        })
        ).doOnError(e -> log.error("[{}] !! Error: {}", traceId, e.getMessage(), e));
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

    private boolean requiresBody(HttpMethod method) {
        return method == HttpMethod.POST
                || method == HttpMethod.PUT
                || method == HttpMethod.PATCH
                || method == HttpMethod.DELETE;
    }
}
