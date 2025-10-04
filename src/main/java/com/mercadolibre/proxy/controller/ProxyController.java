package com.mercadolibre.proxy.controller;

import com.mercadolibre.proxy.service.ProxyService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

@RestController
public class ProxyController {

    private final ProxyService proxyService;

    public ProxyController(ProxyService proxyService) {
        this.proxyService = proxyService;
    }

    @RequestMapping("/**")
    public Mono<ResponseEntity<byte[]>> proxyRequest(ServerWebExchange exchange) {
        return proxyService.forwardRequest(exchange);
    }
}
