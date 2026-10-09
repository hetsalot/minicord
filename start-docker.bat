@echo off
echo ========================================================
echo   Starting MiniCord Distributed Computing Cluster
echo ========================================================
echo.
echo Checking for Docker...
where docker >nul 2>nul
if %errorlevel% neq 0 (
    echo [ERROR] Docker command not found.
    echo Please make sure Docker Desktop is installed and running,
    echo and added to your system PATH.
    echo Download: https://www.docker.com/products/docker-desktop/
    echo.
    pause
    exit /b 1
)

echo Building and starting all containers with Docker Compose...
docker compose up --build

pause
