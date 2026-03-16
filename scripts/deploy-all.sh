#!/usr/bin/env bash
# Redeploy all services after initial setup (git pull + docker compose up --build).
# Run this from VM1. SSHes to VM2 and VM3 automatically.
#
# Usage: bash scripts/deploy-all.sh <utorid>
#
# Example: bash scripts/deploy-all.sh mughalka

set -euo pipefail

UTORID="${1:-}"
if [[ -z "$UTORID" ]]; then
    echo "Usage: $0 <utorid>"
    exit 1
fi

SSH_OPTS="-o StrictHostKeyChecking=accept-new -o ConnectTimeout=10 -o BatchMode=no"
if [[ -f ~/.ssh/csc301_vm ]]; then
    SSH_OPTS="$SSH_OPTS -i ~/.ssh/csc301_vm"
fi

remote() {
    local label="$1" host="$2" compose_file="$3"
    echo ""
    echo "=== Deploying $label ($host) ==="
    ssh $SSH_OPTS "${UTORID}@${host}" bash <<EOF
        set -euo pipefail
        cd ~/csc301-a2
        git pull
        docker compose -f $compose_file down --remove-orphans 2>/dev/null || true
        docker compose -f $compose_file up --build -d
        echo "[$label] containers:"
        docker compose -f $compose_file ps
EOF
}

# --- VM1: OrderService (local) ---
echo "=== Deploying VM1 / OrderService (local) ==="
cd ~/csc301-a2
git pull
docker compose -f docker-compose.vm1.yml down --remove-orphans 2>/dev/null || true
docker compose -f docker-compose.vm1.yml up --build -d
echo "[VM1] containers:"
docker compose -f docker-compose.vm1.yml ps

# --- VM2: ISCS ---
remote "VM2 / ISCS"                          10.128.2.113  docker-compose.vm2.yml

# --- VM3: UserService + ProductService ---
remote "VM3 / UserService + ProductService"  10.128.3.113  docker-compose.vm3.yml

echo ""
echo "=== All VMs deployed. ==="
echo "  VM1 (OrderService):              http://10.128.1.113:8080"
echo "  VM2 (ISCS):                      http://10.128.2.113:14000"
echo "  VM3 (UserService):               http://10.128.3.113:8081"
echo "  VM3 (ProductService):            http://10.128.3.113:8082"
echo "  VM4 (PostgreSQL):                10.128.4.113:5432"
