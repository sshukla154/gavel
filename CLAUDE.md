# Gavel — session memory

Read this file at the start of every session. Update it at the end.

---

## Project state

Phase 0.4 complete. The full Phase 0 deliverable set is now in place.

| Layer | State |
|---|---|
| Service | `auction-service` on Java 21 / Spring Boot 4.0.6, virtual threads enabled |
| Database | PostgreSQL 16 via Flyway, `hello_db`, manual `FlywayMigrationConfiguration` (Spring Boot 4 no longer autoconfigures Flyway with a plain `DataSource` bean) |
| Observability | OTel Collector → Prometheus / Tempo / Loki / Grafana (all in Docker Compose) |
| CI | GitHub Actions: build-and-test → docker → scan (Trivy) → update-helm-tag |
| Container image | `ghcr.io/sshukla154/gavel/auction-service`, pushed on every push to `shukla` |
| Kubernetes | kind cluster `gavel`, Helm chart, ArgoCD GitOps, app-of-apps pattern |
| Next phase | Phase 1.1 — Keycloak / OAuth2 resource server |

---

## Recent session log

### 2026-05-20 (Phase 0 close-out)
- Renamed `hello-service` → `auction-service` throughout (directory, Maven coords, packages, image name, Helm chart, ArgoCD app, Prometheus config)
- Added `OPERATIONS.md` covering tool install, configuration, and three operating modes
- Added ADRs 0004 (Temurin base image), 0005 (GitHub Actions CI), 0006 (ArgoCD GitOps)
- Added `.github/dependabot.yaml` for Maven + Actions weekly dependency updates
- Added Trivy scan job to `ci.yml` (scans GHCR image for HIGH/CRITICAL CVEs after push)
- Added `deploy/scripts/bootstrap-cluster.ps1` + `.sh` — one-command kind cluster setup
- Added `k8s/argocd/app-of-apps.yaml` — ArgoCD app-of-apps pattern
- Added `helm/auction-service/templates/NOTES.txt` — post-install kubectl hint
- Added `helm/auction-service/templates/ingress.yaml` — optional ingress template
- Added `docs/runbooks/argocd-operations.md` — add app, rollback, investigate OutOfSync
- Added `Makefile` — convenience targets: `demo-local`, `demo-k8s`, `clean`

### 2026-05-15 to 2026-05-19 (Phase 0.1–0.4)
- Maven multi-module layout: root POM + `common/` + `services/auction-service/`
- Flyway migrations, JPA entity, visit counter endpoint at `/api/v1/ping`
- Testcontainers integration tests (`PingControllerTest`, `VisitPersistenceIT`)
- Multi-stage Dockerfile: deps → build → extractor → runtime (eclipse-temurin:21-jre-jammy)
- GitHub Actions CI with GHCR push and GitOps tag update loop
- kind + Helm chart + ArgoCD with automated sync

---

## Open decisions

| # | Question | Status |
|---|---|---|
| 1 | Postgres in-cluster vs external managed (Phase 1+) | Deferred to Phase 2 |
| 2 | Angular frontend repo: same monorepo or separate? | Deferred to Phase 1 frontend spike |
| 3 | Switch ArgoCD ApplicationSet when second service is added? | Deferred |

---

## Known gotchas

**Spring Boot 4 / Flyway:** Boot 4 no longer autoconfigures Flyway when you provide a `DataSource` bean manually. `FlywayMigrationConfiguration` must implement `ApplicationDataSourceScriptDatabaseInitializer` explicitly. Without it, Flyway silently skips migrations.

**Layered jar paths (Boot 4):** `java -Djarmode=tools extract` outputs to an `app/` subdirectory, not the working directory root. Dockerfile `COPY --from=extractor` paths must use `/extract/app/dependencies/`, `/extract/app/spring-boot-loader/`, etc.

**`JarLauncher` class path:** Spring Boot 3.2+ moved the launcher to `org.springframework.boot.loader.launch.JarLauncher`. The old `org.springframework.boot.loader.JarLauncher` does not exist in Boot 4.

**WEPoll on Windows 11 + JDK 21:** Tomcat's default NIO2 connector crashes with a WEPoll error on Windows 11 Enterprise. Fix: `TomcatNio2Config` bean that forces `NioProtocol`. This only affects running the service directly on Windows — Docker and kind are unaffected.

**JAVA_HOME on this machine:** Multiple JDKs installed via Scoop. Must override before any Maven command:
```powershell
$env:JAVA_HOME = "$env:USERPROFILE\scoop\apps\zulu21-jdk\current"
```

**Maven root POM install in Docker:** The root `pom.xml` is packaging `pom`, not `jar`. The deps stage must run `mvn -B -N install` first (installs root POM to local Maven repo) before child module commands work.

---

## Next session start

Start Phase 1.1 — Keycloak as the identity provider:
1. Docker Compose: add Keycloak container, realm import volume
2. `gavel-realm.json`: realm `gavel`, client `auction-service`, role `BIDDER`
3. `auction-service`: add `spring-boot-starter-oauth2-resource-server`, configure JWT issuer URI
4. Secure `/api/v1/ping` behind `BIDDER` role; keep `/actuator/health` public
5. Integration test: obtain token from Keycloak, call secured endpoint
6. Update Helm chart: add `KEYCLOAK_ISSUER_URI` env + Secret
7. ADR 0007: Keycloak as identity provider
