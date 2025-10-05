# Proxy Meli (WebFlux Reverse Proxy con Rate Limiting)

Este proyecto implementa un reverse proxy reactivo (Spring Boot WebFlux) pensado para exponer un front-door simple hacia APIs de Mercado Libre con capacidades de:

- Forwarding de requests HTTP de manera no bloqueante (Reactor Netty WebClient)
- Políticas de headers hacia backend y hacia el cliente
- Rate limiting configurable (in‑memory Bucket4j o Redis, a elección)
- Resiliencia (Resilience4j: TimeLimiter + CircuitBreaker)
- Observabilidad (Actuator + Micrometer Prometheus)
- CORS y manejo de errores centralizado

Está orientado a alta concurrencia y baja latencia, integrando back-pressure nativo de WebFlux.


## Arquitectura (alto nivel)

1. Cliente → ProxyController → Filtro de Rate Limiting → ForwardingService → HttpClientGateway (WebClient) → Backend (api.mercadolibre.com por defecto).
2. El RateLimitEngineFilter evalúa una lista de reglas (RateLimitRule). Si alguna bloquea, retorna 429 Too Many Requests, opcionalmente con Retry-After.
3. ForwardingService aplica política de headers, reenvía la petición y adapta la respuesta.
4. Resilience4j rodea las llamadas remotas con límites de tiempo y circuit breaker.

```
[Client]
   │ HTTP
   ▼
[WebFlux Router/Controller]
   │
   ├── [RateLimitEngineFilter] ──► (429 si bloquea)
   │
   └── [ForwardingService] ──► [WebClient] ──► [Backend API]
```

## Componentes clave (código)

- web/ProxyController: entrada HTTP del proxy.
- web/filter/RateLimitEngineFilter: orquesta evaluación de reglas de rate limit y decide 429.
- application/ForwardingService(Impl): composición de request, logging y adaptación de respuesta.
- infrastructure/http/WebClientHttpClient: cliente HTTP reactivo.
- ratelimit/core: núcleo de reglas (Condition, KeyGenerator, Limit, RuleBuilder, RateLimiterBackend, Decision).
- ratelimit/memory/MemoryRateLimiterBackend: uso de Bucket4j en memoria (interval refill).
- ratelimit/redis/RedisRateLimiterBackend: ventana fija (fixed window) en Redis con script Lua atómico.
- config/*: beans de configuración (CORS, WebClient, RateLimitConfig, propiedades y wiring).

Rutas de interés en src/main/java/com/mercadolibre/proxy/...

- config/RateLimitConfig.java: define reglas y selecciona backend según propiedad proxy.rate-limiter.backend.
- config/RateLimiterProperties.java: propiedades de límites (cargadas desde application.yml/env).
- web/filter/RateLimitEngineFilter.java: filtra OPTIONS y /actuator; evalúa reglas en orden y bloquea al primer deny.

## Flujo de una petición

- Se ignoran OPTIONS y /actuator para no interferir con CORS y métricas.
- Para el resto, se evalúan reglas en cadena (concatMap) y se toma la primera decisión denegatoria.
- Si se permite: se construye ForwardRequest y se reenvía con WebClient. Se registran logs con traceId/reqId y latencia.

## Rate Limiting: conceptos y diseño

- RuleBuilder: DSL para declarar reglas con:
  - when(Condition): condiciones booleanas (path, método, headers, etc.).
  - key(KeyGenerator): cómo se compone la clave (IP, path, método, header, constantes, composición).
  - limit(Limit): capacidad y ventana (ej. perMinute(60)).
  - backend(RateLimiterBackend): implementación concreta (memoria o Redis).
- Decision: allow|block con retryAfter opcional (segundos restantes de la ventana vigente).

Backends disponibles:

- Memoria (Bucket4j):
  - Algoritmo: token bucket con refill intervalado igual al tamaño de la ventana solicitada.
  - Ventajas: baja latencia, sin dependencia externa.
  - Contras: no distribuido, pierde estado al reiniciar, no válido para múltiples instancias.
- Redis (Lua, ventana fija):
  - Clave: rl:{key}:{windowStartMs}
  - Script Lua atómico asegura no sobre-consumir la capacidad y setea TTL de la ventana restante.
  - Ventajas: distribuido, consistente entre instancias.
  - Contras: ventana fija (no deslizante), requiere Redis.

Cálculo de Retry-After: se usa el tiempo restante de la ventana actual: ceil(remainingMs/1000).

## Reglas por defecto (RateLimitConfig)

- ip: límite por IP global, configurable ip-per-minute (default 1000).
- categories (global): límite por path /categories, configurable categories-per-minute (default 10000).
- items_ip: límite por IP específicamente en /items, configurable items-ip-per-minute (default 10).
- Reglas opcionales activables por propiedades:
  - ip_path_token: requiere header X-Api-Token y path /secure. Clave compuesta ip+path+token. Límite 50/min.
  - ip_path_method: solo para POST en /items. Clave compuesta ip+path+método. Límite 20/min.

Activación del motor: el filtro se registra si existe proxy.rate-limiter.backend (memory o redis). Se excluyen OPTIONS y /actuator.

## Configuración (application.yml y variables de entorno)

Propiedades principales:

- backend.base-url: URL destino para el forwarding. Default: https://api.mercadolibre.com
- proxy.rate-limiter.backend: memory (default) o redis
- proxy.rate-limiter.ip-per-minute: default 1000
- proxy.rate-limiter.categories-per-minute: default 10000
- proxy.rate-limiter.items-ip-per-minute: default 10
- forwarding.logging-enabled: true/false
- forwarding.resilience.enabled: true/false
- forwarding.resilience.instance-name: nombre para Resilience4j (ej. meliBackend)

Perfil redis:

- spring.data.redis.host (REDIS_HOST, default localhost)
- spring.data.redis.port (REDIS_PORT, default 6379)

Resilience4j (ejemplo en yml):

- CircuitBreaker: ventana COUNT_BASED de 50, failureRateThreshold 50%, slowCallDurationThreshold 2s, waitDurationInOpenState 30s
- TimeLimiter: timeout 3s

Micrometer/Actuator:

- management.endpoints.web.exposure.include: health,info,prometheus
- Histogramas para http.server.requests y http.client.requests activados

## Ejecución

Requisitos: Java 17, Gradle Wrapper, Docker opcional para Redis/observabilidad.

1) Local simple (memoria):

- BACKEND_BASE_URL=https://api.mercadolibre.com (o lo que corresponda)
- RATE_LIMIT_BACKEND=memory (default)

Comandos:

- ./gradlew bootRun

2) Con Redis (perfil redis):

- Levantar Redis: docker-compose -f docker-compose.yml up -d redis
- Ejecutar con perfil: SPRING_PROFILES_ACTIVE=redis ./gradlew bootRun
  - Alternativamente: RATE_LIMIT_BACKEND=redis REDIS_HOST=localhost ./gradlew bootRun

3) Docker local del proxy:

- docker build -t proxy-meli:local .
- docker run -p 8080:8080 -e BACKEND_BASE_URL=... -e RATE_LIMIT_BACKEND=memory proxy-meli:local

4) Makefile (si aplica):

- make run
- make test

## Endpoints

- /health: simple healthcheck.
- /actuator/health, /actuator/info, /actuator/prometheus: endpoints de Actuator.
- Resto de rutas: proxied hacia backend.base-url conservando path y query.

## CORS

- CorsConfig permite configurar orígenes y headers. OPTIONS bypass en RateLimitEngineFilter.

## Política de headers

- DefaultHeaderPolicy decide qué headers se envían al backend y cuáles se devuelven al cliente.
- Se propagan identificadores de trazado (traceId/reqId) en logs y encabezados cuando corresponde.

## Observabilidad

- Logs estructurados con logstash-logback-encoder.
- Métricas Prometheus via Actuator. En observability/docker-compose.yml hay un stack básico para Prometheus.

## Seguridad y buenas prácticas

- Validar y sanear headers de entrada (HeaderPolicy).
- Rate limiting por IP y recursos sensibles.
- Timeouts y circuit breaker para evitar acoplamiento fuerte al backend.
- Evitar exponer Actuator más allá de lo necesario (filtrar por red o auth en entornos productivos).

## Troubleshooting

- 429 Too Many Requests: revisar los límites configurados y claves generadas (IP, path, método, token).
- Latencia alta: revisar Resilience4j TimeLimiter, backend.base-url, y consumo del backend.
- Redis: si backend redis está activo, validar conectividad, host/port, y que las claves rl:* aparecen con TTL.
- Memoria: recordar que el estado se pierde entre reinicios y no se comparte entre instancias.

## Extensiones y personalización

- Nuevas reglas: agregar @Bean de RateLimitRule en RateLimitConfig usando RuleBuilder.
- Nuevos generadores de clave: implementar KeyGenerator y componer con los existentes.
- Nuevos backends: implementar RateLimiterBackend.tryConsume(key, permits, limit).
- Políticas de header: extender HeaderPolicy y ajustar WiringConfig.

## Requisitos de compilación y test

- JDK 17
- ./gradlew test

## Licencia

Uso interno con fines educativos.
