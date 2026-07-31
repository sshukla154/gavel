# ADR 0008 — Angular 20 SPA with Keycloak OIDC

## Status

Accepted

## Context

The platform needs a browser-based client. The backend already exposes a JWT-protected REST API
(`/api/v1/ping`) secured by Keycloak. The frontend must:

- Authenticate users against the same Keycloak realm
- Attach the access token to all API calls without manual wiring per request
- Guard routes so unauthenticated users are redirected to the Keycloak login page
- Be trivially served in Docker alongside the rest of the stack

## Decision

**Angular 20** was chosen as the SPA framework. Key integration choices:

| Concern | Choice | Reason |
|---|---|---|
| Auth library | `keycloak-angular@20.1.0` | First-class Angular 20 support; `provideKeycloak()` is a single `ApplicationConfig` call |
| Auth flow | PKCE authorization code | No client secret in browser; `keycloak-js` handles PKCE natively |
| Token injection | `includeBearerTokenInterceptor` | Functional interceptor; no boilerplate class needed |
| Route protection | `createAuthGuard<CanActivateFn>` | Typed, functional guard; redirects to Keycloak login on 401 |
| Dev proxy | `proxy.conf.json` (ng serve) | Forwards `/api/*` to `localhost:8081`; avoids CORS during local development |
| Production serving | `nginx:alpine` | Handles SPA routing via `try_files $uri /index.html`, proxies `/api/` to backend |

## Consequences

- All `/api/*` HTTP requests automatically carry the `Authorization: Bearer …` header; no per-call
  token retrieval needed
- Adding protected routes only requires `canActivate: [authGuard]` — zero extra configuration
- A new Keycloak client `gavel-spa` was added to `gavel-realm.json` (public, PKCE S256, redirect
  `http://localhost:4200/*`). The test realm JSON is kept in sync so `KeycloakAuthIT` is not affected
- Angular build output lands in `dist/ui/browser/` (default for `@angular/build:application`); the
  Dockerfile copies this path into nginx
- Angular CLI 22 requires Node 22.22.3+ or 24.15.0+; CLI v20 was pinned instead to match Node 24.14.x
  on the development machine
