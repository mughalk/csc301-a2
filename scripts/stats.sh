#!/usr/bin/env bash
# Collect docker stats + JVM heap info from all VMs.
# Run this from VM1. SSHes to VM2 and VM3 automatically.
#
# Usage: bash scripts/stats.sh <utorid>
#
# Output: CPU%, memory usage, and JVM heap per container on each VM.

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

# Run stats on a remote VM
remote_stats() {
    local label="$1" host="$2"
    echo ""
    echo "════════════════════════════════════════════════════"
    echo "  $label  ($host)"
    echo "════════════════════════════════════════════════════"

    ssh $SSH_OPTS "${UTORID}@${host}" bash <<'REMOTE'
        echo "--- docker stats (one-shot) ---"
        docker stats --no-stream \
            --format "table {{.Name}}\t{{.CPUPerc}}\t{{.MemUsage}}\t{{.MemPerc}}\t{{.NetIO}}\t{{.BlockIO}}"

        echo ""
        echo "--- JVM heap (jcmd GC.heap_info) ---"
        for cid in $(docker ps -q 2>/dev/null); do
            name=$(docker inspect --format '{{.Name}}' "$cid" | sed 's|/||')
            echo "  [$name]"
            docker exec "$cid" jcmd 1 GC.heap_info 2>/dev/null \
                | sed 's/^/    /' \
                || echo "    (jcmd not available)"
        done

        echo ""
        echo "--- JVM threads (jcmd Thread.get_count) ---"
        for cid in $(docker ps -q 2>/dev/null); do
            name=$(docker inspect --format '{{.Name}}' "$cid" | sed 's|/||')
            count=$(docker exec "$cid" jcmd 1 Thread.print 2>/dev/null \
                | grep -c '"' || echo "?")
            echo "  [$name] ~$count threads"
        done
REMOTE
}

# --- VM1: local ---
echo ""
echo "════════════════════════════════════════════════════"
echo "  VM1 / OrderService  (local)"
echo "════════════════════════════════════════════════════"

echo "--- docker stats (one-shot) ---"
docker stats --no-stream \
    --format "table {{.Name}}\t{{.CPUPerc}}\t{{.MemUsage}}\t{{.MemPerc}}\t{{.NetIO}}\t{{.BlockIO}}"

echo ""
echo "--- JVM heap (jcmd GC.heap_info) ---"
for cid in $(docker ps -q 2>/dev/null); do
    name=$(docker inspect --format '{{.Name}}' "$cid" | sed 's|/||')
    echo "  [$name]"
    docker exec "$cid" jcmd 1 GC.heap_info 2>/dev/null \
        | sed 's/^/    /' \
        || echo "    (jcmd not available)"
done

# --- VM2, VM3 ---
remote_stats "VM2 / ISCS"                          10.128.2.113
remote_stats "VM3 / UserService + ProductService"  10.128.3.113

echo ""
echo "=== Done. ==="
