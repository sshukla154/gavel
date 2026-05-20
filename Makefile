.PHONY: demo-local demo-k8s clean test build help

# ─── Local Docker Compose mode ──────────────────────────────────────────────

## Start the local infrastructure stack (Postgres, OTel, Prometheus, Grafana, Tempo, Loki)
## then run the service against it.
demo-local:
	docker compose up -d
	@echo "Waiting for Postgres to be ready..."
	@sleep 5
	SPRING_PROFILES_ACTIVE=local \
	  SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:5432/hello_db \
	  SPRING_DATASOURCE_USERNAME=postgres \
	  SPRING_DATASOURCE_PASSWORD=postgres \
	  mvn -pl services/auction-service -am spring-boot:run

# ─── Kubernetes / kind mode ──────────────────────────────────────────────────

## Bootstrap the kind cluster and deploy all apps via ArgoCD.
demo-k8s:
	bash deploy/scripts/bootstrap-cluster.sh

## Port-forward auction-service from the kind cluster to localhost:8081.
k8s-forward:
	kubectl port-forward svc/auction-service -n gavel 8081:8081

## Port-forward ArgoCD UI to localhost:8080.
argocd-ui:
	kubectl port-forward svc/argocd-server -n argocd 8080:443

# ─── Build and test ──────────────────────────────────────────────────────────

## Run all tests (unit + integration via Testcontainers).
test:
	mvn -B verify

## Build without tests.
build:
	mvn -B -DskipTests package

# ─── Cleanup ─────────────────────────────────────────────────────────────────

## Stop Docker Compose services and remove containers/volumes.
clean:
	docker compose down -v
	mvn -B clean

## Delete the kind cluster entirely.
cluster-delete:
	kind delete cluster --name gavel

# ─── Help ────────────────────────────────────────────────────────────────────

help:
	@grep -E '^## ' Makefile | sed 's/## /  /'
	@echo ""
	@echo "Targets: demo-local  demo-k8s  k8s-forward  argocd-ui  test  build  clean  cluster-delete"
