#!/usr/bin/env bash
# Run this ONCE on VM3 (10.128.3.113) to install Docker and deploy UserService + ProductService.
# Usage: bash setup-vm3.sh
set -euo pipefail

echo "[setup-vm3] Installing Docker..."
sudo apt-get update -q
sudo apt-get install -y docker.io
sudo systemctl enable docker
sudo systemctl start docker
sudo usermod -aG docker "$USER"

echo "[setup-vm3] NOTE: You may need to log out and back in for docker group to take effect."
echo "  If docker commands fail, run: newgrp docker"

echo "[setup-vm3] Deploying UserService + ProductService..."
newgrp docker <<'EOF'
  cd ~/csc301-a2
  git pull
  docker compose -f docker-compose.vm3.yml up --build -d
  echo "[setup-vm3] Services started. Check status with:"
  echo "  docker compose -f docker-compose.vm3.yml ps"
  echo "  docker compose -f docker-compose.vm3.yml logs -f"
EOF
