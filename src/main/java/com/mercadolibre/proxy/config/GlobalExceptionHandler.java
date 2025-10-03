package com.mercadolibre.proxy.config;

import com.mercadolibre.proxy.service.ProxyException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(ProxyException.class)
    public ResponseEntity<String> handleProxyException(ProxyException ex) {
        return ResponseEntity.status(ex.getStatus()).body(ex.getMessage());
    }

    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<String> handleRuntimeException(RuntimeException ex) {
        return ResponseEntity.status(502).body("Bad Gateway: " + ex.getMessage());
    }
}
