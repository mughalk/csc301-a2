#!/usr/bin/env bash
# Run this ONCE on VM4 (10.128.4.113) to set up PostgreSQL.
# Usage: bash setup-vm4.sh
set -euo pipefail

echo "[setup-vm4] Installing PostgreSQL..."
sudo apt-get update -q
sudo apt-get install -y postgresql

echo "[setup-vm4] Creating database and user..."
sudo -u postgres psql -c "CREATE USER a2user WITH PASSWORD 'a2pass';" 2>/dev/null || echo "  user already exists"
sudo -u postgres psql -c "CREATE DATABASE a2db OWNER a2user;" 2>/dev/null || echo "  database already exists"

echo "[setup-vm4] Configuring remote access from all lab VMs..."
PG_CONF=$(sudo -u postgres psql -t -c "SHOW config_file;" | tr -d ' ')
PG_HBA=$(sudo -u postgres psql -t -c "SHOW hba_file;" | tr -d ' ')

sudo sed -i "s/#listen_addresses = 'localhost'/listen_addresses = '*'/" "$PG_CONF"
sudo sed -i "s/listen_addresses = 'localhost'/listen_addresses = '*'/" "$PG_CONF"

# Allow VM1 (OrderService) and VM3 (UserService, ProductService) to connect
grep -q "10.128.1.0/24" "$PG_HBA" || \
  echo "host a2db a2user 10.128.1.0/24 md5" | sudo tee -a "$PG_HBA"
grep -q "10.128.3.0/24" "$PG_HBA" || \
  echo "host a2db a2user 10.128.3.0/24 md5" | sudo tee -a "$PG_HBA"

sudo systemctl restart postgresql
echo "[setup-vm4] Done. PostgreSQL accepting connections from VM1 and VM3 on port 5432."
