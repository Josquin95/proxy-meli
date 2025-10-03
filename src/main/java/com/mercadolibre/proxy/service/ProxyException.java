package com.mercadolibre.proxy.service;

import org.springframework.http.HttpStatus;

public class ProxyException extends RuntimeException {
    private final HttpStatus status;

    public ProxyException(HttpStatus status, String message) {
        super(message);
        this.status = status;
    }

    public HttpStatus getStatus() {
        return status;
    }
}
