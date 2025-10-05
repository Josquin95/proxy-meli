package com.mercadolibre.proxy.domain;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;

import java.net.URI;

public record ForwardRequest(
        URI targetUri,
        HttpMethod method,
        HttpHeaders headers,
        byte[] body
) {
}
