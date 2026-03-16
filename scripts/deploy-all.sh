#!/usr/bin/env bash
# Redeploy all services after initial setup (git pull + docker-compose up --build).
# Run this FROM VM1. SSHes directly to VM2 and VM3 on the internal network.
#
# Usage: bash scripts/deploy-all.sh <utorid>
# Example: bash scripts/deploy-all.sh mughalka

set -euo pipefail

UTORID="${1:-}"
if [[ -z "$UTORID" ]]; then
    echo "Usage: $0 <utorid>"
    exit 1
fi

LAB_HOST="dh2010pc44.utm.utoronto.ca"
SSH_OPTS="-o StrictHostKeyChecking=accept-new -o ConnectTimeout=10"

deploy_remote() {
    local label="$1" vm_ip="$2" compose_file="$3"
    echo ""
    echo "=== Deploying $label ($vm_ip) ==="
    ssh $SSH_OPTS -J "${UTORID}@${LAB_HOST}" "student@${vm_ip}" bash <<EOF
        set -euo pipefail
        cd ~/csc301-a2
        git pull
        docker-compose -f $compose_file down --remove-orphans 2>/dev/null || true
        docker-compose -f $compose_file up --build -d
        docker-compose -f $compose_file ps
EOF
}

deploy_remote "VM1 / OrderService"                  10.128.1.113  docker-compose.vm1.yml

deploy_remote "VM2 / ISCS"                         10.128.2.113  docker-compose.vm2.yml
deploy_remote "VM3 / UserService + ProductService"  10.128.3.113  docker-compose.vm3.yml

echo ""
echo "=== All VMs deployed ==="
