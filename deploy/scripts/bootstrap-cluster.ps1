<#
.SYNOPSIS
    Bootstrap the Gavel kind cluster from scratch.

.DESCRIPTION
    Creates the kind cluster, installs ArgoCD, pre-loads the auction-service
    image, and applies the app-of-apps manifest. Run once on a fresh machine
    or after destroying the cluster.

.PARAMETER SkipImageLoad
    Skip pre-loading the Docker image into kind (useful when ArgoCD will pull
    from GHCR directly on a machine with good internet access).

.EXAMPLE
    .\bootstrap-cluster.ps1
    .\bootstrap-cluster.ps1 -SkipImageLoad
#>
param(
    [switch]$SkipImageLoad
)

$ErrorActionPreference = "Stop"

$ClusterName   = "gavel"
$Namespace     = "argocd"
$ArgoCDVersion = "v2.13.3"
$ImageTag      = "shukla"
$ImageRef      = "ghcr.io/sshukla154/gavel/auction-service:$ImageTag"
$ScriptRoot    = Split-Path -Parent $MyInvocation.MyCommand.Definition
$RepoRoot      = Resolve-Path (Join-Path $ScriptRoot "..\..")

Write-Host "==> Creating kind cluster '$ClusterName'" -ForegroundColor Cyan
kind create cluster --config "$RepoRoot\k8s\kind\cluster-config.yaml"

Write-Host "==> Waiting for cluster to be ready" -ForegroundColor Cyan
kubectl wait --for=condition=Ready node --all --timeout=120s

Write-Host "==> Installing ArgoCD $ArgoCDVersion" -ForegroundColor Cyan
kubectl create namespace $Namespace --dry-run=client -o yaml | kubectl apply -f -
kubectl apply -n $Namespace -f "https://raw.githubusercontent.com/argoproj/argo-cd/$ArgoCDVersion/manifests/install.yaml"

Write-Host "==> Waiting for ArgoCD server to be ready" -ForegroundColor Cyan
kubectl wait --for=condition=Available deployment/argocd-server -n $Namespace --timeout=300s

if (-not $SkipImageLoad) {
    Write-Host "==> Pulling image $ImageRef" -ForegroundColor Cyan
    docker pull $ImageRef

    Write-Host "==> Loading image into kind cluster" -ForegroundColor Cyan
    kind load docker-image $ImageRef --name $ClusterName
}

Write-Host "==> Applying app-of-apps manifest" -ForegroundColor Cyan
kubectl apply -f "$RepoRoot\k8s\argocd\app-of-apps.yaml"

Write-Host ""
Write-Host "==> Bootstrap complete." -ForegroundColor Green
Write-Host ""
Write-Host "Get the ArgoCD admin password:" -ForegroundColor Yellow
Write-Host "  kubectl -n argocd get secret argocd-initial-admin-secret -o jsonpath=`"{.data.password}`" | base64 -d"
Write-Host ""
Write-Host "Port-forward the ArgoCD UI:" -ForegroundColor Yellow
Write-Host "  kubectl port-forward svc/argocd-server -n argocd 8080:443"
Write-Host ""
Write-Host "Port-forward auction-service:" -ForegroundColor Yellow
Write-Host "  kubectl port-forward svc/auction-service -n gavel 8081:8081"
