# ADR 0009 — JWT relay for service-to-service calls

## Status

Accepted

## Context

auction-service needs to call bid-service on behalf of the authenticated user. Both services
are separate OAuth2 resource servers — they share the same Keycloak realm but have no shared
session. bid-service must receive the user's JWT so it can enforce its own authorization rules
without trusting the caller's identity assertion blindly.

## Decision

**JWT relay via `ClientHttpRequestInterceptor`.**

When auction-service calls bid-service, a `JwtRelayInterceptor` reads the access token from
`SecurityContextHolder` and injects it as `Authorization: Bearer <token>` on the outbound
`RestClient` request. bid-service then validates the token independently against the same
Keycloak JWK set.

| Concern | Decision |
|---|---|
| Token source | `AbstractOAuth2TokenAuthenticationToken` from `SecurityContextHolder` |
| Propagation mechanism | `ClientHttpRequestInterceptor` registered on `RestClient.Builder` in `BidClient` |
| Validation at target | bid-service is a full JWT resource server — no inter-service trust shortcut |
| Scoping | Interceptor added per-client (`BidClient`), not globally — other `RestClient` instances are unaffected |

## Alternatives considered

**Token exchange (RFC 8693)** — auction-service requests a new token scoped to bid-service.
Stricter, but requires Keycloak Token Exchange (a preview feature). Deferred to a future phase
if fine-grained per-service scoping becomes necessary.

**Service accounts / client credentials** — auction-service authenticates as itself. Loses
the user identity at bid-service; incompatible with per-user authorization rules.

**mTLS** — strong service identity, no token propagation. Requires a PKI. Deferred.

## Consequences

- The user's full JWT (including expiry and roles) reaches bid-service unchanged. If the token
  expires mid-call, bid-service returns 401, which propagates as a 5xx from auction-service.
  Mitigation: the Angular SPA already uses `withAutoRefreshToken` to keep tokens fresh.
- `JwtRelayInterceptor` lives in auction-service (not `gavel-common`) — extract when a second
  caller needs it.
- `SecurityContextHolder.MODE_THREADLOCAL` propagates correctly because Spring MVC processes
  the request synchronously on the same virtual thread.
