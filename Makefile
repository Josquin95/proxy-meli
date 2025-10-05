# ==============================
# Variables
# ==============================
IMAGE        ?= meli-proxy:layered
PORT         ?= 8080
BACKEND_URL  ?= https://api.mercadolibre.com
RL_BACKEND   ?= memory         # memory | redis
REDIS_HOST   ?= host.docker.internal
REDIS_PORT   ?= 6379

# ==============================
# Targets
# ==============================
.PHONY: help
help:
	@echo "Targets disponibles:"
	@echo "  make build           -> Compila (bootJar) y construye imagen Docker layered"
	@echo "  make run             -> Ejecuta contenedor (rate limit in-memory)"
	@echo "  make run-redis       -> Ejecuta contenedor usando Redis (SPRING_PROFILES_ACTIVE=redis)"
	@echo "  make compose-up      -> Levanta Redis (docker-compose.yml)"
	@echo "  make compose-down    -> Baja Redis"
	@echo "  make obs-up          -> Levanta Prometheus y Grafana (observability/docker-compose.yml)"
	@echo "  make obs-down        -> Baja Prometheus y Grafana"
	@echo "  make smoke           -> Pruebas rápidas (healthz y categoria)"
	@echo "  make logs            -> Logs del último contenedor ejecutado"
	@echo "  make clean           -> Limpia build Gradle"
	@echo ""
	@echo "Variables override: IMAGE, PORT, BACKEND_URL, RL_BACKEND, REDIS_HOST, REDIS_PORT"

# Build del jar y la imagen layered (usa el Dockerfile multi-stage con layertools)
.PHONY: build
build:
	./gradlew clean bootJar
	docker build -t $(IMAGE) .

# Ejecuta la app con rate limit in-memory
.PHONY: run
run:
	docker run --rm \
	  -p $(PORT):$(PORT) \
	  -p 9091:9091 \
	  -e SERVER_PORT=$(PORT) \
	  -e BACKEND_BASE_URL=$(BACKEND_URL) \
	  -e RATE_LIMIT_BACKEND=$(RL_BACKEND) \
	  $(IMAGE)

# Ejecuta la app contra Redis (requiere compose-up o un Redis accesible)
.PHONY: run-redis
run-redis:
	docker run --rm \
	  -p $(PORT):$(PORT) \
	  -p 9091:9091 \
	  -e SERVER_PORT=$(PORT) \
	  -e SPRING_PROFILES_ACTIVE=redis \
	  -e BACKEND_BASE_URL=$(BACKEND_URL) \
	  -e RATE_LIMIT_BACKEND=redis \
	  -e REDIS_HOST=$(REDIS_HOST) \
	  -e REDIS_PORT=$(REDIS_PORT) \
	  $(IMAGE)

# Levanta / baja Redis del docker-compose.yml (en la raíz del repo)
.PHONY: compose-up
compose-up:
	docker compose up -d
.PHONY: compose-down
compose-down:
	docker compose down

# Levanta / baja Prometheus y Grafana (observability/docker-compose.yml)
.PHONY: obs-up
obs-up:
	docker compose -f observability/docker-compose.yml up -d
.PHONY: obs-down
obs-down:
	docker compose -f observability/docker-compose.yml down

# Smoke tests rápidos
.PHONY: smoke
smoke:
	@echo "Healthcheck:"
	@curl -s -i http://localhost:$(PORT)/healthz || true
	@echo "\nSites MLA:"
	@curl -s -i http://localhost:$(PORT)/sites/MLA || true
	@echo "\nCategoria pública:"
	@curl -s -i http://localhost:$(PORT)/categories/MLA120352 || true
	@echo "\n404 de prueba:"
	@curl -s -i http://localhost:$(PORT)/categories/MLA120352clear || true

# Logs del último contenedor (si está en ejecución en modo attach-less)
.PHONY: logs
logs:
	@docker ps --latest --format '{{.ID}} {{.Image}}'
	@docker logs -f $$((docker ps -l -q))

# Limpieza de build Gradle
.PHONY: clean
clean:
	./gradlew clean
