# 0006 — GitOps with ArgoCD

- **Status:** Accepted
- **Date:** 2026-05-20
- **Deciders:** Seemant

## Context

The project needs a deployment mechanism that makes Kubernetes cluster state auditable, reproducible, and automatically reconciled with the Git repository. The mechanism must work on a local kind cluster in Phase 0 and scale to Azure AKS in Phase 2.

## Decision

Use **ArgoCD** as the GitOps operator. The repo itself is the source of truth. The deployment model:

- ArgoCD watches `helm/auction-service/` on the `shukla` branch.
- `values-local.yaml` overrides are applied for the kind cluster (branch tag, reduced resources, host OTel endpoint).
- `automated.prune: true` removes Kubernetes resources deleted from Git. `selfHeal: true` reverts manual `kubectl` changes.
- The **app-of-apps** pattern (`k8s/argocd/app-of-apps.yaml`) bootstraps all ArgoCD `Application` resources from a single root application, so a fresh cluster needs only one `kubectl apply`.

The GitOps loop: CI commits a new image SHA to `helm/auction-service/values.yaml` → ArgoCD detects the change within 3 minutes (default poll interval) → ArgoCD applies the updated Deployment → Kubernetes performs a rolling update.

## Consequences

**Easier:**
- Every cluster state change has a corresponding Git commit — full audit trail with author and timestamp.
- Rolling back is `git revert` followed by a push; ArgoCD applies the revert automatically.
- Multi-cluster support (local kind + AKS) is a separate ArgoCD `Application` pointing at the same Helm chart with different values files.
- ArgoCD's UI shows diff between desired (Git) and live (cluster) state at a glance.

**Harder:**
- ArgoCD itself must be bootstrapped before it can manage other apps — chicken-and-egg for a fresh cluster. Solved by `bootstrap-cluster.ps1` / `.sh` which installs ArgoCD first, then applies the app-of-apps manifest.
- Secrets in Git are a security concern. Mitigated for Phase 0 by keeping only non-sensitive defaults in `values.yaml` and using Kubernetes Secrets (base64, not encrypted). Phase 3 will introduce Sealed Secrets or External Secrets Operator.
- The `[skip ci]` GitOps tag-update commit requires careful branch protection configuration — force-push must remain allowed for `github-actions[bot]` on the `shukla` branch.

## Alternatives considered

**Flux CD:** Functionally similar to ArgoCD; preferred by some teams for its GitOps-native design (no UI by default). Rejected because ArgoCD's web UI provides useful visual diff and sync status for a portfolio demo, and the lead engineer has more ArgoCD experience.

**Helm-only (no GitOps operator):** `helm upgrade` in CI. Simpler initially, but cluster state is not continuously reconciled — manual `kubectl` changes silently diverge from Git. Rejected for auditability reasons.

**Kustomize-only:** No templating. Works for simple cases but becomes verbose when managing multiple environments. The Helm chart is already written; ArgoCD supports both Helm and Kustomize, so this is not a blocking constraint.
