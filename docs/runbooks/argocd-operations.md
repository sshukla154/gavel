# ArgoCD operations runbook

Operational procedures for the ArgoCD GitOps setup in the `gavel` kind cluster.

---

## Prerequisites

ArgoCD CLI installed and logged in:

```bash
argocd login localhost:8080 --username admin --password <password> --insecure
```

Get the initial admin password:

```bash
kubectl -n argocd get secret argocd-initial-admin-secret \
  -o jsonpath="{.data.password}" | base64 -d
```

---

## Add a new application

1. Create a Helm chart under `helm/<service-name>/` following the `auction-service` chart as a template.
2. Create a Kubernetes ArgoCD `Application` manifest under `k8s/argocd/<service-name>-app.yaml`.
3. Register it in `k8s/argocd/app-of-apps.yaml` by adding a new `Application` entry pointing at the new manifest path.
4. Commit and push. The app-of-apps ArgoCD application will detect the new manifest within 3 minutes and create the child application automatically.

Verify:

```bash
argocd app list
argocd app get <service-name>
```

---

## Roll back a deployment

Find the history of sync operations:

```bash
argocd app history auction-service
```

Roll back to a previous revision (use the `ID` column from the history output):

```bash
argocd app rollback auction-service <revision-id>
```

This deploys the manifests from the Git commit at that revision. To make the rollback permanent, revert the commit in Git and push — ArgoCD will sync the revert automatically.

---

## Investigate OutOfSync status

When ArgoCD shows an application as `OutOfSync`:

```bash
# Show the diff between live cluster state and Git
argocd app diff auction-service

# Show full resource tree
argocd app get auction-service --show-managed-fields
```

Common causes:

| Symptom | Likely cause |
|---|---|
| Deployment image tag differs | CI pushed a new tag; ArgoCD is about to sync |
| ConfigMap key added manually | Someone ran `kubectl edit`; selfHeal will revert it |
| CRD version mismatch | Helm chart upgraded a CRD; run `argocd app sync --replace` |
| Namespace missing | Sync option `CreateNamespace=true` not set; add it to the Application |

---

## Handle a failed sync

```bash
# See the sync error
argocd app get auction-service

# Retry the sync
argocd app sync auction-service --retry-limit 3

# Force replace resources if normal apply fails
argocd app sync auction-service --force
```

If the sync fails on a resource that must be deleted first:

```bash
# Delete the offending resource manually, then re-sync
kubectl delete <resource-type> <name> -n gavel
argocd app sync auction-service
```

---

## Pause automated sync

Useful when debugging a rollout or running manual kubectl experiments:

```bash
# Disable automated sync
argocd app set auction-service --sync-policy none

# Re-enable when done
argocd app set auction-service --sync-policy automated \
  --auto-prune --self-heal
```

---

## Hard refresh (bypass ArgoCD cache)

Forces ArgoCD to re-read the Git repo and re-compare with cluster state:

```bash
argocd app get auction-service --hard-refresh
```

---

## Delete an application (without deleting cluster resources)

```bash
argocd app delete auction-service --cascade=false
```

To delete both the ArgoCD Application and the cluster resources it manages:

```bash
argocd app delete auction-service --cascade=true
```
