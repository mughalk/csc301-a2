#!/usr/bin/env bash
# Redeploy all services. Run this FROM dh2010pc44 (the host).
# OrderService runs directly on the host (no Docker) so it can bind to the public IP.
# ISCS (VM2) and UserService+ProductService (VM3) are deployed via SSH+Docker.
#
# Usage: bash scripts/deploy-all.sh

set -euo pipefail

SSH_OPTS="-o StrictHostKeyChecking=accept-new -o ConnectTimeout=10"
# VM2/VM3 are only reachable by jumping through VM1 (ssh -p 2222 student@localhost)
JUMP="-J student@localhost:2222"
REPO=~/csc301-a2

deploy_remote() {
    local label="$1" vm_ip="$2" compose_file="$3"
    echo ""
    echo "=== Deploying $label ($vm_ip) ==="
    ssh $SSH_OPTS $JUMP "student@${vm_ip}" bash <<EOF
        set -euo pipefail
        cd ~/csc301-a2
        git pull
        docker-compose -f $compose_file down --remove-orphans 2>/dev/null || true
        docker-compose -f $compose_file up --build -d
        docker-compose -f $compose_file ps
EOF
}

# Deploy ISCS and UserService+ProductService on VMs first
deploy_remote "VM2 / ISCS"                         10.128.2.113  docker-compose.vm2.yml
deploy_remote "VM3 / UserService + ProductService"  10.128.3.113  docker-compose.vm3.yml

# Start OrderService on the host, binding to the public IP
echo ""
echo "=== Deploying OrderService (host, 142.1.46.113:8080) ==="
cd "$REPO"
git pull

# Kill any existing OrderService on this host
pkill -f "OrderService.OrderService" 2>/dev/null || true
sleep 1

DB_URL=jdbc:postgresql://10.128.4.113:5432/a2db \
DB_USER=a2user \
DB_PASS=a2pass \
nohup java -cp "compiled:lib/*" OrderService.OrderService config.host.json \
    > "$REPO/orderservice.log" 2>&1 &

echo "OrderService started (pid $!), logs: $REPO/orderservice.log"

echo ""
echo "=== All services deployed ==="
echo "Entry point: http://142.1.46.113:8080"
