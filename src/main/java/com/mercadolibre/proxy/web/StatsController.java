package com.mercadolibre.proxy.web;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.search.Search;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/stats")
public class StatsController {

    private final MeterRegistry registry;

    public StatsController(MeterRegistry registry) {
        this.registry = registry;
    }

    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    public Map<String, Object> stats() {
        Map<String, Object> out = new HashMap<>();

        double requests = Search.in(registry).name("proxy_requests_total").counter() != null
                ? Search.in(registry).name("proxy_requests_total").counter().count()
                : 0d;
        out.put("requests_total", requests);

        Map<String, Double> responsesByClass = new HashMap<>();
        registry.find("proxy_responses_total").counters().forEach(c -> {
            String cls = c.getId().getTag("status_class");
            responsesByClass.merge(cls != null ? cls : "unknown", c.count(), Double::sum);
        });
        out.put("responses_by_class_total", responsesByClass);

        double rejections = Search.in(registry).name("proxy_rate_limit_rejections_total").counter() != null
                ? Search.in(registry).name("proxy_rate_limit_rejections_total").counter().count()
                : 0d;
        out.put("rate_limit_rejections_total", rejections);

        return out;
    }
}
