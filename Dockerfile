# ========= 1) BUILDER: compila y extrae capas (JDK 17) =========
FROM gradle:8.9-jdk17-alpine AS builder
WORKDIR /workspace

# Copia lo mínimo primero para cachear dependencias
COPY build.gradle settings.gradle gradlew ./
COPY gradle ./gradle

# Precalienta caché de dependencias (no falla el build si no hay tasks)
RUN ./gradlew --no-daemon dependencies || true

# Ahora el código fuente
COPY src ./src

# Construye el JAR ejecutable
RUN ./gradlew --no-daemon clean bootJar

# Extrae el layered JAR a carpetas (layertools)
# Crea: dependencies/, snapshot-dependencies/, spring-boot-loader/, application/
RUN JAR_PATH=$(ls build/libs/*-SNAPSHOT.jar || ls build/libs/*.jar) \
 && java -Djarmode=layertools -jar "$JAR_PATH" extract


# ========= 2) RUNTIME: imagen mínima solo con capas =========
FROM eclipse-temurin:17-jre-alpine AS runtime

# Usuario no root
RUN addgroup -S app && adduser -S app -G app
USER app

WORKDIR /app

COPY --from=builder /workspace/build/libs/*.jar /app/app.jar

# Config por defecto (puedes override con -e)
ENV SERVER_PORT=8080 \
    BACKEND_BASE_URL=https://api.mercadolibre.com \
    RATE_LIMIT_BACKEND=memory

EXPOSE 8080

# Healthcheck (requiere /actuator/health expuesto en application.yml)
HEALTHCHECK --interval=30s --timeout=3s --start-period=20s CMD \
  wget -qO- http://127.0.0.1:${SERVER_PORT}/actuator/health | grep -q '"status":"UP"' || exit 1

# Ejecuta el loader de Spring Boot (carga desde las capas copiadas)
ENTRYPOINT ["sh","-c","java $JAVA_OPTS -jar /app/app.jar \
  --server.port=${SERVER_PORT} \
  --backend.base-url=${BACKEND_BASE_URL} \
  --proxy.rate-limiter.backend=${RATE_LIMIT_BACKEND}"]

