package com.mercadolibre.proxy.domain;

import org.springframework.http.HttpHeaders;

public record ForwardResponse(
        int status,
        HttpHeaders headers,
        byte[] body
) {}
