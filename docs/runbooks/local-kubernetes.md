# Local Kubernetes — kind + Helm + ArgoCD

This runbook walks through standing up the full GitOps stack locally using kind.

## Prerequisites

Install these tools before you begin:

| Tool | Install |
|---|---|
| [kind](https://kind.sigs.k8s.io/) | `scoop install kind` or `brew install kind` |
| [kubectl](https://kubernetes.io/docs/tasks/tools/) | `scoop install kubectl` |
| [Helm 3](https://helm.sh/docs/intro/install/) | `scoop install helm` |
| [ArgoCD CLI](https://argo-cd.readthedocs.io/en/stable/cli_installation/) | `scoop install argocd` |

Docker Desktop (or equivalent) must be running.

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

## 2. Load the auction-service image into kind

kind clusters are isolated from the host Docker daemon. Pull and load the image manually so ArgoCD can deploy it without internet access:

```bash
docker pull ghcr.io/sshukla154/gavel/auction-service:shukla
kind load docker-image ghcr.io/sshukla154/gavel/auction-service:shukla --name gavel
```

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

---

## 6. Deploy via ArgoCD

Apply both Application manifests. ArgoCD will sync the desired state from git:

```bash
kubectl apply -f k8s/argocd/postgres-app.yaml
kubectl apply -f k8s/argocd/auction-service-app.yaml
```

Watch sync progress:

```bash
argocd app list
argocd app get auction-service
argocd app get postgres
```

Or from the ArgoCD UI if you opened the port-forward above.

---

## 7. Verify the deployment

Wait for the auction-service pod to be ready:

```bash
kubectl wait --for=condition=ready pod -l app.kubernetes.io/name=auction-service \
  -n gavel --timeout=120s
```

Port-forward the service and smoke-test the ping endpoint:

```bash
kubectl port-forward svc/auction-service -n gavel 8081:8081
curl http://localhost:8081/api/v1/ping
```

Expected response:

```json
{"data":{"status":"ok","service":"auction-service","totalVisits":1},"timestamp":"..."}
```

Check liveness and readiness probes:

```bash
curl http://localhost:8081/actuator/health/liveness
curl http://localhost:8081/actuator/health/readiness
```

---

## 8. GitOps loop in action

Every push to the `shukla` branch:
1. CI builds and pushes a new image tagged `sha-<short>` to GHCR.
2. The `update-helm-tag` CI job updates `helm/auction-service/values.yaml` with the new tag and pushes a `[skip ci]` commit.
3. ArgoCD detects the values.yaml change (polling every 3 minutes by default) and rolls out the new image.

To force an immediate sync:

```bash
argocd app sync auction-service
```

---

## 9. Tear down

Delete just the applications (leaves the cluster running):

```bash
kubectl delete -f k8s/argocd/auction-service-app.yaml
kubectl delete -f k8s/argocd/postgres-app.yaml
```

Delete the entire kind cluster:

```bash
kind delete cluster --name gavel
```
