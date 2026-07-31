#!/usr/bin/env bash
# Bootstrap the Gavel kind cluster from scratch.
#
# Usage:
#   ./bootstrap-cluster.sh              # full bootstrap including image pre-load
#   ./bootstrap-cluster.sh --skip-image-load
#
# Requires: kind, kubectl, docker, argocd CLI (optional — only needed for login)

set -euo pipefail

CLUSTER_NAME="gavel"
ARGOCD_NAMESPACE="argocd"
ARGOCD_VERSION="v2.13.3"
IMAGE_TAG="shukla"
IMAGE_REF="ghcr.io/sshukla154/gavel/auction-service:${IMAGE_TAG}"
SKIP_IMAGE_LOAD=false

for arg in "$@"; do
  case $arg in
    --skip-image-load) SKIP_IMAGE_LOAD=true ;;
    *) echo "Unknown argument: $arg"; exit 1 ;;
  esac
done

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "${SCRIPT_DIR}/../.." && pwd)"

echo "==> Creating kind cluster '${CLUSTER_NAME}'"
kind create cluster --config "${REPO_ROOT}/k8s/kind/cluster-config.yaml"

echo "==> Waiting for cluster to be ready"
kubectl wait --for=condition=Ready node --all --timeout=120s

echo "==> Installing ingress-nginx (kind-specific manifest)"
kubectl apply -f https://raw.githubusercontent.com/kubernetes/ingress-nginx/main/deploy/static/provider/kind/deploy.yaml

echo "==> Waiting for ingress-nginx controller to be ready"
kubectl wait --namespace ingress-nginx \
  --for=condition=ready pod \
  --selector=app.kubernetes.io/component=controller \
  --timeout=120s

echo "==> Installing ArgoCD ${ARGOCD_VERSION}"
kubectl create namespace "${ARGOCD_NAMESPACE}" --dry-run=client -o yaml | kubectl apply -f -
kubectl apply -n "${ARGOCD_NAMESPACE}" \
  -f "https://raw.githubusercontent.com/argoproj/argo-cd/${ARGOCD_VERSION}/manifests/install.yaml"

echo "==> Waiting for ArgoCD server to be ready"
kubectl wait --for=condition=Available deployment/argocd-server \
  -n "${ARGOCD_NAMESPACE}" --timeout=300s

if [ "${SKIP_IMAGE_LOAD}" = "false" ]; then
  echo "==> Pulling image ${IMAGE_REF}"
  docker pull "${IMAGE_REF}"

  echo "==> Loading image into kind cluster"
  kind load docker-image "${IMAGE_REF}" --name "${CLUSTER_NAME}"
fi

echo "==> Applying app-of-apps manifest"
kubectl apply -f "${REPO_ROOT}/k8s/argocd/app-of-apps.yaml"

echo ""
echo "==> Bootstrap complete."
echo ""
echo "Add to hosts file (Windows: C:\Windows\System32\drivers\etc\hosts):"
echo "  127.0.0.1 auction-service.local"
echo ""
echo "Get the ArgoCD admin password:"
echo "  kubectl -n argocd get secret argocd-initial-admin-secret \\"
echo "    -o jsonpath=\"{.data.password}\" | base64 -d"
echo ""
echo "Port-forward the ArgoCD UI:"
echo "  kubectl port-forward svc/argocd-server -n argocd 8080:443"
echo ""
echo "Verify via ingress (after ArgoCD syncs):"
echo "  curl http://auction-service.local/api/v1/ping"
