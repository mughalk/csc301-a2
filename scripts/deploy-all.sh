#!/usr/bin/env bash
# Redeploy all services. Run this FROM VM1.
# VM1 is deployed locally; VM2 and VM3 are reached via direct SSH.
#
# Usage: bash scripts/deploy-all.sh

set -euo pipefail

SSH_OPTS="-o StrictHostKeyChecking=accept-new -o ConnectTimeout=10"

deploy_remote() {
    local label="$1" vm_ip="$2" compose_file="$3"
    echo ""
    echo "=== Deploying $label ($vm_ip) ==="
    ssh $SSH_OPTS "student@${vm_ip}" bash <<EOF
        set -euo pipefail
        cd ~/csc301-a2
        git pull
        docker-compose -f $compose_file down --remove-orphans 2>/dev/null || true
        docker-compose -f $compose_file up --build -d
        docker-compose -f $compose_file ps
EOF
}

echo ""
echo "=== Deploying VM1 / OrderService (local) ==="
cd ~/csc301-a2
git pull
docker-compose -f docker-compose.vm1.yml down --remove-orphans 2>/dev/null || true
docker-compose -f docker-compose.vm1.yml up --build -d
docker-compose -f docker-compose.vm1.yml ps

deploy_remote "VM2 / ISCS"                         10.128.2.113  docker-compose.vm2.yml
deploy_remote "VM3 / UserService + ProductService"  10.128.3.113  docker-compose.vm3.yml

echo ""
echo "=== All VMs deployed ==="
