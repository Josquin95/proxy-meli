package com.mercadolibre.proxy.management;

import io.micrometer.core.instrument.*;
import io.micrometer.core.instrument.search.Search;
import org.springframework.boot.actuate.endpoint.annotation.*;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

@Component
@Endpoint(id = "proxystats")
public class ProxyStatsEndpoint {

    private final MeterRegistry registry;

    public ProxyStatsEndpoint(MeterRegistry registry) {
        this.registry = registry;
    }

    @ReadOperation
    public Map<String, Object> stats() {
        Map<String, Object> out = new HashMap<>();

        Counter req = Search.in(registry).name("proxy_requests_total").counter();
        out.put("requests_total", req != null ? req.count() : 0d);

        Map<String, Double> responsesByClass = new HashMap<>();
        registry.find("proxy_responses_total").counters().forEach(c -> {
            String cls = c.getId().getTag("status_class");
            responsesByClass.merge(cls != null ? cls : "unknown", c.count(), Double::sum);
        });
        out.put("responses_by_class_total", responsesByClass);

        Counter rej = Search.in(registry).name("proxy_rate_limit_rejections_total").counter();
        out.put("rate_limit_rejections_total", rej != null ? rej.count() : 0d);

        return out;
    }
}
