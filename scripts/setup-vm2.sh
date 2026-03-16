#!/usr/bin/env bash
# Run this ONCE on VM2 (10.128.2.113) to install Docker and deploy ISCS.
# Usage: bash setup-vm2.sh
set -euo pipefail

echo "[setup-vm2] Installing Docker..."
sudo apt-get update -q
sudo apt-get install -y docker.io docker-compose
sudo systemctl enable docker
sudo systemctl start docker
sudo usermod -aG docker "$USER"

echo "[setup-vm2] NOTE: You may need to log out and back in for docker group to take effect."
echo "  If docker commands fail, run: newgrp docker"

echo "[setup-vm2] Deploying ISCS..."
newgrp docker <<'EOF'
  cd ~/csc301-a2
  git pull
  docker-compose -f docker-compose.vm2.yml up --build -d
  echo "[setup-vm2] ISCS started. Check status with:"
  echo "  docker-compose -f docker-compose.vm2.yml ps"
  echo "  docker-compose -f docker-compose.vm2.yml logs -f"
EOF
