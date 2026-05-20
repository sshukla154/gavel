# 0005 — CI/CD with GitHub Actions and GHCR

- **Status:** Accepted
- **Date:** 2026-05-20
- **Deciders:** Seemant

## Context

The project needs a CI pipeline that compiles, tests, packages, and publishes the container image on every push. The pipeline also needs to feed the GitOps loop by writing the new image tag back to the Helm values file so ArgoCD can roll out the change automatically.

## Decision

Use **GitHub Actions** as the CI platform and **GitHub Container Registry (GHCR)** as the image registry. The pipeline has four jobs:

1. **build-and-test** — `mvn -B verify` on `ubuntu-latest` with Testcontainers (Docker socket available on GitHub-hosted runners). Uploads surefire/failsafe reports as artifacts on failure.
2. **docker** — Multi-stage Docker build via `docker/build-push-action`. GHA layer cache (`type=gha`) keeps subsequent builds under two minutes. Pushes to GHCR only on push events (not PRs).
3. **scan** — Trivy image scan on the freshly pushed GHCR image. Fails the pipeline on HIGH or CRITICAL CVEs.
4. **update-helm-tag** — Patches `helm/auction-service/values.yaml` with the short SHA tag and pushes a `[skip ci]` commit back to the branch. ArgoCD detects the change and rolls out.

## Consequences

**Easier:**
- Zero infrastructure to maintain — GitHub-hosted runners are free for public repositories.
- `GITHUB_TOKEN` is sufficient for both GHCR push and the GitOps commit; no external secrets required.
- Maven dependency cache (`actions/setup-java` with `cache: maven`) is keyed on `pom.xml` hash — cold builds are rare after the first run.
- The four-job structure maps directly to the delivery pipeline: test → publish → secure → deploy.

**Harder:**
- GitHub-hosted runners have a 6-hour job timeout and occasional queue delays under high load.
- The GitOps commit loop requires `contents: write` permission on the workflow, which is broader than ideal. Mitigated by the `[skip ci]` guard preventing re-trigger loops.
- Trivy scans the remote GHCR image (not the local build), so the scan job depends on the docker job completing successfully first.

## Alternatives considered

**GitLab CI:** The lead engineer's day job uses GitLab. Rejected here because the portfolio is hosted on GitHub and using the native CI avoids cross-platform complexity.

**Jenkins:** Requires self-hosted infrastructure. Rejected — the project goal is a zero-ops CI pipeline.

**Docker Hub instead of GHCR:** Docker Hub has pull-rate limits on anonymous pulls (100/6h per IP) and requires a separate paid account for private repositories. GHCR is free for public repos and integrates with `GITHUB_TOKEN` without extra credential management.
