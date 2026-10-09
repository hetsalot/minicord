Write-Host "========================================================" -ForegroundColor Cyan
Write-Host "   Starting MiniCord Distributed Computing Cluster      " -ForegroundColor Cyan
Write-Host "========================================================" -ForegroundColor Cyan
Write-Host ""

if (-not (Get-Command docker -ErrorAction SilentlyContinue)) {
    Write-Host "[ERROR] Docker is not found in your PATH." -ForegroundColor Red
    Write-Host "Please install Docker Desktop and start the Docker daemon." -ForegroundColor Yellow
    Write-Host "Website: https://www.docker.com/products/docker-desktop/" -ForegroundColor Yellow
    Exit 1
}

Write-Host "Starting cluster with Docker Compose..." -ForegroundColor Green
docker compose up --build
