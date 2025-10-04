package com.mercadolibre.proxy.config;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import reactor.netty.http.client.PrematureCloseException;
import java.net.SocketTimeoutException;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler({ SocketTimeoutException.class, PrematureCloseException.class })
    public ResponseEntity<String> handleTimeouts(Exception ex) {
        return ResponseEntity.status(HttpStatus.GATEWAY_TIMEOUT)
                .body("Upstream timeout");
    }
}
