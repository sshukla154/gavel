# syntax=docker/dockerfile:1
# Multi-stage build for auction-service.
#
# Stage layout:
#   deps      — downloads all external Maven dependencies.
#               Keyed on pom files + gavel-common source only, so a code-only
#               change in auction-service reuses this layer and skips the ~2-min
#               dependency download.
#   build     — full source compile and package (tests skipped; CI already ran them).
#   extractor — splits the fat jar into Spring Boot layers so the dependency
#               layer (rarely changes) is cached separately from application
#               classes (changes on every commit).
#   runtime   — minimal JRE image, non-root user, healthcheck on Actuator.

# ─── Stage 1: dependency resolution ─────────────────────────────────────────
FROM maven:3.9-eclipse-temurin-21 AS deps
WORKDIR /build

# Copy only pom files first — this layer changes only when dependencies change.
COPY pom.xml .
COPY common/pom.xml common/
COPY services/auction-service/pom.xml services/auction-service/
COPY services/bid-service/pom.xml services/bid-service/
COPY services/notification-service/pom.xml services/notification-service/

# gavel-common is a project-local module, not available from any remote repo.
# Copy its source so Maven can compile and install it into the local .m2 cache.
COPY common/src common/src

# 1. Install the root (parent) POM into the local cache.
#    -N = non-recursive; builds only the aggregator, not child modules.
#    Required because gavel-common and auction-service both declare this POM
#    as their parent and Maven must be able to resolve it.
# 2. Install gavel-common into the local cache.
# 3. Resolve all external dependencies (compile + test scope) for auction-service.
#    dependency:resolve-plugins also pre-fetches Maven plugin artifacts, preventing
#    surprise network calls in the build stage.
RUN mvn -B -N install && \
    mvn -B -pl common install -DskipTests && \
    mvn -B -pl services/auction-service \
        dependency:resolve \
        dependency:resolve-plugins \
        -DincludeScope=test \
        -DskipTests

# ─── Stage 2: compile and package ────────────────────────────────────────────
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /build

# Reuse the primed .m2 from the deps stage.
COPY --from=deps /root/.m2 /root/.m2

# Copy full source tree (invalidates only when application source changes).
COPY . .

# Build auction-service and its reactor dependencies; skip tests (CI ran them).
RUN mvn -B -pl services/auction-service -am -DskipTests package

# ─── Stage 3: extract Spring Boot layers ─────────────────────────────────────
FROM eclipse-temurin:21-jre-jammy AS extractor
WORKDIR /extract

COPY --from=build /build/services/auction-service/target/auction-service-*.jar app.jar

# Spring Boot 4 jarmode=tools splits the fat jar into four layers.
# Layer order (least-to-most volatile):
#   dependencies          — release third-party JARs
#   spring-boot-loader    — Spring Boot loader classes
#   snapshot-dependencies — SNAPSHOT JARs
#   application           — compiled app classes + resources
RUN java -Djarmode=tools -jar app.jar extract --layers --launcher
# Spring Boot 4 extracts into an app/ subdirectory:
#   /extract/app/dependencies/
#   /extract/app/spring-boot-loader/
#   /extract/app/snapshot-dependencies/
#   /extract/app/application/

# ─── Stage 4: runtime ────────────────────────────────────────────────────────
FROM eclipse-temurin:21-jre-jammy

# Install curl for the HEALTHCHECK; clean up apt lists immediately to keep
# the layer small.
RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/* \
    && groupadd -r app -g 1001 \
    && useradd -r -u 1001 -g app -d /app appuser

WORKDIR /app

# Copy layers in order from least to most volatile.
# Each COPY is a separate layer — Docker only re-pushes layers that changed.
COPY --from=extractor /extract/app/dependencies/ ./
COPY --from=extractor /extract/app/spring-boot-loader/ ./
COPY --from=extractor /extract/app/snapshot-dependencies/ ./
COPY --from=extractor /extract/app/application/ ./

USER appuser

EXPOSE 8081

# --start-period gives Flyway + Hibernate time to initialise before the first probe.
HEALTHCHECK --interval=30s --timeout=3s --start-period=45s --retries=3 \
    CMD curl -fsS http://localhost:8081/actuator/health || exit 1

# -XX:MaxRAMPercentage caps heap at 75 % of container memory limit.
# JarLauncher class path changed in Spring Boot 3.2 → org.springframework.boot.loader.launch.*
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75.0", "org.springframework.boot.loader.launch.JarLauncher"]
