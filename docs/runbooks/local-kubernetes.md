# Local Kubernetes — kind + Helm + ArgoCD

This runbook walks through standing up the full GitOps stack locally using kind:
Postgres, Keycloak-external, auction-service, bid-service, and an in-cluster
Kafka (Strimzi).

## Prerequisites

Install these tools before you begin:

| Tool | Install |
|---|---|
| [kind](https://kind.sigs.k8s.io/) | `scoop install kind` or `brew install kind` |
| [kubectl](https://kubernetes.io/docs/tasks/tools/) | `scoop install kubectl` |
| [Helm 3](https://helm.sh/docs/intro/install/) | `scoop install helm` |
| [ArgoCD CLI](https://argo-cd.readthedocs.io/en/stable/cli_installation/) | `scoop install argocd` |

Docker Desktop (or equivalent) must be running, with at least 4GB RAM / 2 CPUs
allocated (Settings → Resources) — Strimzi's operator and broker pods are the
first workloads in this cluster with real memory requirements.

---

## 1. Create the kind cluster

```bash
kind create cluster --config k8s/kind/cluster-config.yaml
```

Verify it is up:

```bash
kubectl cluster-info --context kind-gavel
kubectl get nodes
```

---

## 2. Load service images into kind

kind clusters are isolated from the host Docker daemon. Pull and load both
service images manually so ArgoCD can deploy them without depending on GHCR
being reachable from inside the cluster:

```bash
docker pull ghcr.io/sshukla154/gavel/auction-service:master
kind load docker-image ghcr.io/sshukla154/gavel/auction-service:master --name gavel

docker pull ghcr.io/sshukla154/gavel/bid-service:master
kind load docker-image ghcr.io/sshukla154/gavel/bid-service:master --name gavel
```

The Strimzi operator and Kafka images (`quay.io/strimzi/*`) are pulled directly
by the cluster from `quay.io` — no manual load step, but the cluster does need
outbound internet access for that pull to succeed.

---

## 3. Install ArgoCD

```bash
kubectl create namespace argocd
kubectl apply -n argocd -f https://raw.githubusercontent.com/argoproj/argo-cd/stable/manifests/install.yaml
```

Wait for all pods to be ready:

```bash
kubectl wait --for=condition=ready pod --all -n argocd --timeout=120s
```

---

## 4. Access the ArgoCD UI (optional)

```bash
kubectl port-forward svc/argocd-server -n argocd 8080:443
```

Retrieve the initial admin password:

```bash
argocd admin initial-password -n argocd
```

Open `https://localhost:8080` in a browser (accept the self-signed cert).

---

## 5. Add the bitnami Helm repo

The postgres ArgoCD Application pulls from the bitnami chart repository:

```bash
helm repo add bitnami https://charts.bitnami.com/bitnami
helm repo update
```

The Strimzi operator's ArgoCD Application (`kafka-operator-app.yaml`) uses an
OCI Helm source instead (`quay.io/strimzi-helm`) — no `helm repo add` needed for
it. If ArgoCD reports a repo-resolution error syncing that Application, register
the OCI registry explicitly:

```bash
argocd repo add quay.io/strimzi-helm --type helm --name strimzi-helm --enable-oci
```

---

## 6. Deploy via ArgoCD

Apply the single app-of-apps root — it discovers and syncs every `*-app.yaml`
under `k8s/argocd/` automatically (postgres, auction-service, bid-service, the
Strimzi operator, and the Kafka cluster/topics):

```bash
kubectl apply -f k8s/argocd/app-of-apps.yaml
```

Watch sync progress:

```bash
argocd app list
argocd app get gavel-apps
argocd app get auction-service
argocd app get bid-service
argocd app get kafka
argocd app get strimzi-kafka-operator
argocd app get postgres
```

Or from the ArgoCD UI if you opened the port-forward above.

The Strimzi operator (`sync-wave: "-1"`) syncs before the `kafka` Application's
`Kafka`/`KafkaTopic` custom resources — those need the operator's CRDs and
running controller to reconcile at all. Give the operator a minute before
expecting the `kafka` app to go healthy.

---

## 7. Verify the deployment

Wait for the Kafka cluster to be ready (this is the slowest step — broker
startup plus the Topic Operator reconciling all 8 `KafkaTopic` CRs):

```bash
kubectl wait kafka/gavel-kafka --for=condition=Ready --timeout=300s -n gavel
```

Wait for both services:

```bash
kubectl wait --for=condition=ready pod -l app.kubernetes.io/name=auction-service \
  -n gavel --timeout=120s
kubectl wait --for=condition=ready pod -l app.kubernetes.io/name=bid-service \
  -n gavel --timeout=120s
```

Port-forward and smoke-test:

```bash
kubectl port-forward svc/auction-service -n gavel 8081:8081 &
kubectl port-forward svc/bid-service -n gavel 8082:8082 &

curl http://localhost:8081/actuator/health/liveness
curl http://localhost:8082/actuator/health/liveness
```

`/api/v1/auctions` and `/api/v1/auctions/{id}/bids` require a valid Keycloak
JWT (see `docs/OPERATIONS.md` for obtaining one) — the plain ping-style
unauthenticated smoke test from earlier phases no longer applies now that
Phase 1.1's security config is in place.

Confirm bid-service can actually reach the in-cluster Kafka (this is the whole
point of this runbook — the documented path did not previously deploy Phase 2
at all):

```bash
kubectl logs -n gavel -l app.kubernetes.io/name=bid-service --tail=50 | grep -i kafka
```

Look for consumer group join messages (`auction-service-stream-*` groups from
auction-service, `bid-service` group from bid-service) rather than connection
errors.

---

## 8. GitOps loop in action

Every push to the `master` branch:
1. CI builds and pushes new images tagged `sha-<short>` to GHCR for **both**
   `auction-service` and `bid-service`.
2. The `update-helm-tag` CI job updates both `helm/auction-service/values.yaml`
   and `helm/bid-service/values.yaml` with the new tag (one commit per service,
   both `[skip ci]`) and pushes.
3. ArgoCD detects each values.yaml change (polling every 3 minutes by default)
   and rolls out the new image for that service independently.

To force an immediate sync:

```bash
argocd app sync auction-service
argocd app sync bid-service
```

---

## 9. Tear down

Delete everything the app-of-apps root manages (cascades to every child
Application and its cluster resources, per the finalizer on `gavel-apps`):

```bash
kubectl delete -f k8s/argocd/app-of-apps.yaml
```

Delete the entire kind cluster:

```bash
kind delete cluster --name gavel
```

---

## Known limitations of this runbook

This sequence has been written and statically validated (`helm lint`,
`helm template`, YAML syntax checks on every manifest) but **not exercised
against a live kind cluster** — this development machine has no `kind`
installed and no cluster running. Treat step-by-step correctness as reviewed,
not proven; the first person to actually run this end to end should expect to
fix at least minor issues (timing, an overlooked probe path, an OCI repo
registration quirk) and is encouraged to update this note once verified.
