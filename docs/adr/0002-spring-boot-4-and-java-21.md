# 0002 — Spring Boot 4.0.7 and Java 21

- **Status:** Accepted
- **Date:** 2026-05-15
- **Deciders:** Seemant
- **Updated:** 2026-08-04 — Spring Boot upgraded to 4.0.7 to resolve CVEs in PostgreSQL JDBC

## Context

Choosing a Java and Spring Boot version to pin for the entire project. The choice affects available APIs, LTS support timeline, performance characteristics, and hiring signal for a portfolio project.

## Decision

Pin to **Java 21** (LTS) and **Spring Boot 4.0.7** (latest GA on the 4.0.x line). Enable virtual threads via `spring.threads.virtual.enabled: true` from day one.

## Consequences

**What we get:**

- **Java 21 virtual threads (Project Loom):** Blocking I/O on virtual threads scales to hundreds of thousands of concurrent connections without thread pool tuning. The Bidding service will lean on this heavily when handling concurrent bid streams.
- **Spring Boot 4 / Spring Framework 7:** Jakarta EE 11 namespace, first-class support for Loom-aware blocking, updated Micrometer + OTel integration, improved AOT/GraalVM compilation pipeline.
- **Long support runway:** Java 21 is LTS until at least 2029. Spring Boot 4.0.x receives OSS support until mid-2027.
- **Portfolio signal:** Demonstrates familiarity with the current Spring ecosystem rather than legacy 2.x patterns.

**What we give up / accept:**

- Spring Boot 4 breaks compatibility with Spring Boot 3.x extension libraries that haven't migrated to Jakarta EE namespace. Any third-party library must be verified for Jakarta EE 11 compatibility before adding.
- GraalVM native image support is present but not targeted in Phase 0. If native images become a goal, revisit the build pipeline.

## Alternatives considered

**Spring Boot 3.4.x (latest 3.x):** Still receives OSS support and has a larger ecosystem of compatible libraries. Rejected because starting on 4.x avoids a forced migration later, and the portfolio signal is stronger with the latest major version.

**Java 17 (LTS):** Still supported and widely deployed. Rejected because Java 21 adds virtual threads, pattern matching enhancements, and sequenced collections that we'll use. The JDK 21 LTS timeline is longer.

**Java 23 / 24 (non-LTS):** Latest features but no LTS guarantee, meaning a forced upgrade every six months. Rejected for production-stability reasons.
