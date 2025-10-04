package com.mercadolibre.proxy.domain;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;

public record RequestContext(
        String traceId,
        String reqId,
        HttpMethod method,
        String targetUrl,
        HttpHeaders inboundHeaders,
        long startNanos
) {}
