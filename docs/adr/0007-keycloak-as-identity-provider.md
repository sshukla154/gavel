# 0007 — Keycloak as identity provider

- **Status:** Accepted
- **Date:** 2026-07-31
- **Deciders:** Seemant

## Context

Gavel needs an identity provider (IdP) to issue JWTs for authenticated users. The auction-service must validate those JWTs and enforce role-based access (Phase 1.1: `BIDDER` role required on all `/api/**` endpoints). The IdP must run locally in Docker Compose for development, be importable into a Kubernetes cluster in later phases, and support a standard OIDC/OAuth2 flow that the future Angular SPA can use.

## Decision

Use **Keycloak 26** as the identity provider.

- Realm: `gavel` with a `BIDDER` realm role.
- Local dev: Keycloak runs in Docker Compose on host port 8180, imports `infra/keycloak/gavel-realm.json` at startup via `--import-realm`.
- auction-service: configured as an OAuth2 resource server, validating JWTs via the realm's JWK set URI (`${KEYCLOAK_ISSUER_URI}/protocol/openid-connect/certs`).
- Role extraction: `KeycloakJwtRolesConverter` reads `realm_access.roles` from the JWT and maps them to `ROLE_*` Spring `GrantedAuthority` objects.
- Integration tests: `dasniko/testcontainers-keycloak` runs a real Keycloak container with the realm import; tests obtain tokens via the Resource Owner Password Credentials (ROPC) grant and exercise the 401/403/200 paths end-to-end.

## Consequences

**Easier:**
- Standard OIDC: any OIDC-compliant client (Angular, mobile, CLI) can authenticate without service-level changes.
- Realm import as code: `gavel-realm.json` is version-controlled, so realm config is reproducible across machines and environments.
- Keycloak's admin UI (`localhost:8180/admin`) enables role and user management during development.
- Spring Boot's `spring-boot-starter-oauth2-resource-server` handles all JWT validation plumbing; the service only writes authorization rules.

**Harder:**
- Docker Compose stack grows by one container (~700 MB image). Keycloak takes ~30–40 s to start with `start-dev`.
- `KeycloakContainer` in integration tests adds significant wall-clock time to `mvn verify` (one container per test class that needs real JWT validation).
- Issuer URI validation is not enforced in Phase 1.1 (only `jwk-set-uri` is set, not `issuer-uri`). Any JWT signed with keys from the configured JWK set is accepted. Phase 2 will add explicit issuer validation.

## Alternatives considered

**Spring Authorization Server:** Pure Spring implementation, no external process. Rejected because it adds Spring application complexity, requires the same configuration effort as Keycloak, and lacks Keycloak's admin UI and realm-management features that will be useful in Phase 3+ (user management, social login, MFA).

**Auth0 / Okta (managed IdP):** No infrastructure to run locally. Rejected: requires a free-tier account and external network access for local dev — breaks the fully-local dev stack principle established in Phase 0.

**Fake/in-process JWT issuer for tests only:** Simpler for CI but does not verify that the actual Keycloak integration works. Rejected in favour of `KeycloakContainer`, which gives a genuine end-to-end gate (real token, real claim shape, real HTTP flow).
