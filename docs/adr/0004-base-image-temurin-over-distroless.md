# 0004 — Base image: Eclipse Temurin JRE over distroless

- **Status:** Accepted
- **Date:** 2026-05-20
- **Deciders:** Seemant

## Context

The production container image needs a JRE base layer. Two credible options exist:

- **Eclipse Temurin 21 JRE** (`eclipse-temurin:21-jre-jammy`) — maintained by the Adoptium Working Group under the Eclipse Foundation
- **Distroless Java 21** (`gcr.io/distroless/java21-debian12`) — Google-maintained minimal image with no shell, no package manager, no debugging tools

The choice affects image size, security surface, debuggability, and compatibility with GitHub Actions and local development.

## Decision

Use **`eclipse-temurin:21-jre-jammy`** as the runtime base image.

## Consequences

**Easier:**
- Works out of the box on `ubuntu-latest` GitHub Actions runners — no registry auth, no pull-rate limits, no firewall concerns.
- `docker exec -it <container> bash` is available for local debugging; `kubectl exec` into a pod gives a shell.
- Debian Jammy (22.04 LTS) base means `apt-get` is available for adding diagnostic tools in a dev override image.
- The Adoptium project is free and has no commercial licensing concerns (unlike some Oracle JDK builds).
- Long-term support: Temurin 21 is an LTS release supported until at least 2029.

**Harder:**
- Larger attack surface than distroless: a shell and basic Debian utilities are present in the image.
- Image size is ~230 MB compared to ~220 MB for distroless — marginal difference after layer caching.

## Alternatives considered

**Distroless Java 21 (`gcr.io/distroless/java21-debian12`):**
Smallest attack surface — no shell means an attacker who breaks into the container cannot run arbitrary commands. The right choice for hardened production. Rejected for Phase 0 because `kubectl exec` debugging is unavailable, Trivy scanning requires a different SBOM approach, and it adds friction during the early development phase when inspecting running containers is common. Revisit in Phase 3 or 4 when security hardening becomes a priority.

**`amazoncorretto:21-al2023-jre`:**
Good choice for AWS-hosted deployments; Amazon maintains it. Rejected because this project targets Azure AKS (Phase 2+) and the Adoptium community maintains Temurin for all platforms equally.

**`openjdk:21-jre-slim`:**
Deprecated — the OpenJDK Docker Hub images are no longer maintained. Rejected on maintenance grounds.
