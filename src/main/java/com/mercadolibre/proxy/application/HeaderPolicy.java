package com.mercadolibre.proxy.application;

import org.springframework.http.HttpHeaders;

public interface HeaderPolicy {
    HttpHeaders toBackend(HttpHeaders inbound, String requestId);
    HttpHeaders toClient(HttpHeaders backend, String requestId);
}
