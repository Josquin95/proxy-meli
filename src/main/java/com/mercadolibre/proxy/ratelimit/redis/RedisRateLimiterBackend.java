package com.mercadolibre.proxy.ratelimit.redis;

import com.mercadolibre.proxy.ratelimit.core.Limit;
import com.mercadolibre.proxy.ratelimit.core.RateLimiterBackend;
import org.springframework.data.redis.connection.ReturnType;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import reactor.core.publisher.Mono;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
/**
 * Fixed-window por clave + ventana; estricto con Lua (no hay sobre-consumo).
 * Clave real: "rl:{key}:{windowStartMs}"
 */
public class RedisRateLimiterBackend implements RateLimiterBackend {

    private static final String SCRIPT = String.join("\n",
            "local current = redis.call('GET', KEYS[1])",
            "local permits  = tonumber(ARGV[1])",
            "local ttlMs    = tonumber(ARGV[2])",
            "local capacity = tonumber(ARGV[3])",
            "if not current then",
            "  if permits <= capacity then",
            "    redis.call('SET', KEYS[1], permits, 'PX', ttlMs)",
            "    return permits",
            "  else",
            "    return -1",
            "  end",
            "else",
            "  local c = tonumber(current)",
            "  local newc = c + permits",
            "  if newc <= capacity then",
            "    redis.call('INCRBY', KEYS[1], permits)",
            "    return newc",
            "  else",
            "    return -1",
            "  end",
            "end"
    );

    private final ReactiveStringRedisTemplate redis;

    public RedisRateLimiterBackend(ReactiveStringRedisTemplate redis) {
        this.redis = redis;
    }

    @Override
    public Mono<Boolean> tryConsume(String key, int permits, Limit limit) {
        long now = System.currentTimeMillis();
        long windowMs = limit.window().toMillis();
        long windowStart = (now / windowMs) * windowMs;
        long ttlMs = windowMs - (now % windowMs);

        String redisKey = "rl:" + key + ":" + windowStart;

        var k = ByteBuffer.wrap(redisKey.getBytes(StandardCharsets.UTF_8));
        var a1 = ByteBuffer.wrap(String.valueOf(permits).getBytes(StandardCharsets.UTF_8));
        var a2 = ByteBuffer.wrap(String.valueOf(ttlMs).getBytes(StandardCharsets.UTF_8));
        var a3 = ByteBuffer.wrap(String.valueOf(limit.capacity()).getBytes(StandardCharsets.UTF_8));

        return redis.execute(conn -> conn.scriptingCommands()
                        .eval(ByteBuffer.wrap(SCRIPT.getBytes(StandardCharsets.UTF_8)),
                                ReturnType.INTEGER, 1, k, a1, a2, a3))
                .single()
                .map(res -> {
                    if (res == null) return false;
                    long v = ((Number) res).longValue();
                    return v > 0; // -1 => bloqueado
                });
    }
}
