package com.mercadolibre.proxy.controller;

import com.mercadolibre.proxy.service.ProxyException;
import com.mercadolibre.proxy.service.ProxyService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/")
public class ProxyController {

    private final ProxyService proxyService;

    public ProxyController(ProxyService proxyService) {
        this.proxyService = proxyService;
    }

    @GetMapping("/{*path}")
    public Mono<ResponseEntity<String>> forwardRequest(@PathVariable("path") String path) {
        return proxyService.forwardRequest(path)
                .map(ResponseEntity::ok)
                .switchIfEmpty(Mono.just(ResponseEntity.notFound().build()))
                .onErrorResume(ProxyException.class, ex ->
                        Mono.just(ResponseEntity.status(ex.getStatus()).body(ex.getMessage())))
                .onErrorResume(e ->
                        Mono.just(ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                                .body("Downstream error: " + e.getMessage())));
    }
}
