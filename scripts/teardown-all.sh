#!/usr/bin/env bash
# Bring all services down. Run this FROM dh2010pc44 (the host).
#
# Usage: bash scripts/teardown-all.sh

set -euo pipefail

SSH_OPTS="-o StrictHostKeyChecking=accept-new -o ConnectTimeout=10"
JUMP="-J student@localhost:2222"

teardown_remote() {
    local label="$1" vm_ip="$2" compose_file="$3"
    echo ""
    echo "=== Tearing down $label ($vm_ip) ==="
    ssh $SSH_OPTS $JUMP "student@${vm_ip}" bash <<EOF
        set -euo pipefail
        cd ~/csc301-a2
        docker-compose -f $compose_file down --remove-orphans 2>/dev/null || true
        echo "Done."
EOF
}

echo ""
echo "=== Stopping OrderService (host) ==="
pkill -f "OrderService.OrderService" 2>/dev/null && echo "Stopped." || echo "Not running."

teardown_remote "VM2 / ISCS"                         10.128.2.113  docker-compose.vm2.yml
teardown_remote "VM3 / UserService + ProductService"  10.128.3.113  docker-compose.vm3.yml

echo ""
echo "=== All services torn down ==="
