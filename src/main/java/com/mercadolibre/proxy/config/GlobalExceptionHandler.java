package com.mercadolibre.proxy.config;

import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.concurrent.TimeoutException;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(TimeoutException.class)
    public ResponseEntity<String> handleTimeout(TimeoutException ex) {
        return ResponseEntity.status(HttpStatus.GATEWAY_TIMEOUT).body("Upstream timeout");
    }

    @ExceptionHandler(CallNotPermittedException.class)
    public ResponseEntity<String> handleOpenCircuit(CallNotPermittedException ex) {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body("Upstream temporarily unavailable");
    }
}
