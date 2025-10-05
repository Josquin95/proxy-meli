package com.mercadolibre.proxy.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;

@Component
public class ProxyMetrics {

    private final MeterRegistry registry;

    public ProxyMetrics(MeterRegistry registry) {
        this.registry = registry;
    }

    public Timer.Sample startRequest() {
        return Timer.start(registry);
    }

    public void recordRequest(String method) {
        Counter.builder("proxy_requests_total")
                .description("Total proxy requests received")
                .tags("method", safe(method))
                .register(registry)
                .increment();
    }

    public void recordResponse(int statusCode, Duration duration) {
        String statusClass = statusClass(statusCode);
        List<Tag> tags = List.of(
                Tag.of("status", String.valueOf(statusCode)),
                Tag.of("status_class", statusClass)
        );
        Counter.builder("proxy_responses_total")
                .description("Total proxy responses sent")
                .tags(tags)
                .register(registry)
                .increment();

        Timer.builder("proxy_request_duration_seconds")
                .description("Proxy request duration in seconds")
                .tags(tags)
                .register(registry)
                .record(duration);
    }

    public void recordDurationWithSample(Timer.Sample sample, int statusCode) {
        String statusClass = statusClass(statusCode);
        Timer timer = Timer.builder("proxy_request_duration_seconds")
                .description("Proxy request duration in seconds")
                .tags("status", String.valueOf(statusCode), "status_class", statusClass)
                .register(registry);
        sample.stop(timer);
    }

    public void incrementRateLimitRejection() {
        Counter.builder("proxy_rate_limit_rejections_total")
                .description("Total number of requests rejected by rate limiting")
                .register(registry)
                .increment();
    }

    private static String statusClass(int statusCode) {
        int c = statusCode / 100;
        return c + "xx";
    }

    private static String safe(String s) {
        return s == null ? "UNKNOWN" : s;
    }
}
