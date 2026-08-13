# 0013 — Strimzi Kafka and bid-service on kind/ArgoCD

- **Status:** Accepted
- **Date:** 2026-08-13
- **Deciders:** Seemant

## Context

ADR 0010's consequences section admitted the gap directly: "No bid-service Helm
chart / k8s Kafka yet — the documented kind/ArgoCD path cannot deploy Phase 2."
Every service added since checkpoint 1.3 (bid-service) and 2.2 (Kafka) had a
Docker image and a CI pipeline, but only auction-service had a Helm chart and
an ArgoCD `Application`. This ADR closes that gap: a bid-service Helm chart and
an in-cluster Kafka cluster, both GitOps-managed like auction-service already is.

## Decision

**Strimzi (not Bitnami) for in-cluster Kafka; a bid-service Helm chart that
mirrors auction-service's exactly; everything in the single `gavel` namespace;
everything auto-applied via the existing app-of-apps mechanism.**

| Concern | Decision |
|---|---|
| Kafka operator | [Strimzi](https://strimzi.io) 1.1.0, installed via its OCI Helm chart (`oci://quay.io/strimzi-helm/strimzi-kafka-operator`) as an ArgoCD `Application` — not Bitnami's Kafka chart, whose public catalog was gutted in Aug 2025 (already known from the 2026-08-12 enhancement review) |
| Kafka mode | KRaft (no ZooKeeper), one combined controller+broker `KafkaNodePool` — matches the Compose environment's Kafka mode, sized for a laptop kind cluster (5Gi storage, not the upstream example's 100Gi) |
| CRD API version | `kafka.strimzi.io/v1` for `Kafka`/`KafkaNodePool`/`KafkaTopic` — verified against Strimzi's live docs during implementation; `v1beta2` (what almost every older tutorial shows) was removed as of Strimzi 1.0.0 |
| Namespace | Everything in `gavel` — the same namespace auction-service, bid-service, and Postgres already use. Kafka's bootstrap Service is then reachable at the bare name `gavel-kafka-kafka-bootstrap:9092`, no cross-namespace FQDN |
| Topic declaration | 8 `KafkaTopic` CRs (the 4 topics + their `.DLT`s from ADR 0010/0012) for cluster-admin visibility and GitOps parity — the apps still self-declare the same topics via Spring's `NewTopic` beans on startup either way, so this is belt-and-suspenders, not a hard runtime dependency |
| bid-service Helm chart | File-for-file mirror of `helm/auction-service/` (same `_helpers.tpl` shape, same probe paths, same secret pattern) — port 8082, its own `bids_db` datasource default, no OTel env (bid-service has none), `KAFKA_BOOTSTRAP_SERVERS` added |
| Operator/CR sync ordering | `k8s/argocd/kafka-operator-app.yaml` carries `sync-wave: "-1"` so the operator (and its CRDs) is fully synced before `kafka-app.yaml`'s `Kafka`/`KafkaTopic` CRs are applied |
| Fixed a pre-existing gap, found during this work | auction-service's Helm chart had **no** `KAFKA_BOOTSTRAP_SERVERS` or `BID_SERVICE_URL` env vars at all — both silently defaulted to `localhost` inside the pod, which cannot work in-cluster. Added both. |
| Fixed a second pre-existing gap | The k8s Postgres `Application` only provisioned `hello_db` (auction-service's database) via the bitnami chart's `auth.database` — `bids_db` never existed in-cluster. Added `primary.initdb.scripts` with the same `CREATE DATABASE bids_db;` that `infra/postgres/init.sql` already runs for Compose. |

## Alternatives considered

**Bitnami Kafka Helm chart** — the obvious mirror of how Postgres is already
deployed (`k8s/postgres/values.yaml` + the bitnami postgresql chart). Rejected:
Bitnami's free-tier image catalog was gutted in August 2025 (per the 2026-08-12
review), so a new dependency on it is a bad bet even though the existing
Postgres dependency on it predates that decision and is out of scope here.
An operator-managed Kafka (Strimzi) is also the stronger portfolio signal for
the same reason ADR 0010 chose Kafka over a simpler queue: it demonstrates
operating real infrastructure, not just consuming a container image.

**Kafka in its own `kafka` namespace** (Strimzi's own quickstart convention,
and what the first implementation pass actually built before this review
caught it) — rejected in favor of matching this repo's existing single-`gavel`-
namespace pattern, avoiding cross-namespace DNS and an extra namespace to
manage for no benefit at this scale.

**Manual `kubectl apply -f k8s/kafka/kafka-operator-app.yaml`** for the
operator (the first implementation pass's approach, to sidestep reasoning
about cross-Application sync ordering) — rejected in favor of ArgoCD sync
waves, which is the standard mechanism for exactly this CRD-then-CR dependency
and keeps the "one `kubectl apply` for a fresh cluster" promise from ADR 0006
actually true.

## Consequences

- **Not verified against a live cluster.** This development machine has no
  `kind` installed and no running cluster. Verification in this session was
  static only: `helm lint` and `helm template` (both prod and local value
  files) on both charts, and YAML syntax validation on every new/changed k8s
  manifest. The manifests are correct as far as static checks can prove;
  runtime correctness (operator install succeeding, the Kafka CR reaching
  `Ready`, bid-service actually connecting) is unverified. `docs/runbooks/local-kubernetes.md`
  says this explicitly.
- CI (GitHub Actions) still only builds/tests/scans Docker images — it does
  not deploy to any Kubernetes cluster, so this ADR's changes do not get an
  automated correctness signal from CI the way application code does.
- The 8 `KafkaTopic` CRs and the apps' own `NewTopic` beans are two sources of
  truth for the same topic configuration (partitions, replicas). They agree
  today because both were written from the same source; a future change to
  one without the other is a silent drift risk worth a lint/test someday, not
  solved here.
- `update-helm-tag` in CI now loops over both services sequentially in one job
  (not a matrix) specifically to avoid two parallel jobs racing to push commits
  to the same branch.
