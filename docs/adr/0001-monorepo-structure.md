# 0001 — Monorepo structure with Maven multi-module

- **Status:** Accepted
- **Date:** 2026-05-15
- **Deciders:** Seemant

## Context

Gavel will eventually have multiple services (hello, auction, identity, notification) plus shared libraries. The primary decision is whether to keep all code in one repository or split it across per-service repositories. A secondary decision is whether to use Maven or Gradle as the build tool within the chosen structure.

## Decision

Single Git repository (monorepo) with a Maven multi-module layout. The root `pom.xml` is the aggregator and dependency-management POM. Shared code lives in `common/`. Each service lives under `services/<name>/`.

## Consequences

**Easier:**
- Atomic cross-module changes — a shared DTO change and its service consumers land in one commit.
- Single CI pipeline for the whole system; no cross-repo dependency version drift.
- Refactoring across service boundaries is a local operation.
- Onboarding: one `git clone`, one `mvn verify` to validate the whole system.

**Harder:**
- As the repo grows, full builds get slower. Addressed later with Maven build cache / incremental builds.
- Access control is coarser-grained — all contributors see all services. Acceptable for a portfolio project; revisit if the repo goes multi-team.

## Alternatives considered

**Polyrepo (one repo per service):** Easier to scope CI per service and give per-service access control. Rejected because cross-service refactoring becomes a multi-PR choreography problem at this stage, and shared library versioning adds overhead that isn't justified until teams are independent.

**Git submodules:** Combines a single top-level repo with per-service sub-repos. Rejected because submodule tooling friction (sync, detached HEAD, partial clones) outweighs the benefits at this scale.

**Gradle instead of Maven:** Gradle offers faster incremental builds and a Kotlin DSL that is more concise than XML. Rejected because the lead engineer has 15 years of Maven experience, the Spring Boot ecosystem is built around Maven conventions, and Gradle's build scripts add cognitive overhead without a clear win for a project of this size.
