#!/usr/bin/env bash
# Opens an SSH tunnel to VM1 so you can run k6 locally.
# Usage: ./scripts/tunnel.sh <utorid>
#
# First-time setup (run once to avoid password prompts):
#   ssh-keygen -t ed25519 -f ~/.ssh/csc301_vm -N ""
#   ssh-copy-id -i ~/.ssh/csc301_vm.pub <utorid>@dh2010pc44.utm.utoronto.ca
#
# Then the tunnel opens silently in the background.

set -euo pipefail

UTORID="${1:-}"
HOST="dh2010pc44.utm.utoronto.ca"
LOCAL_PORT=8080
REMOTE_PORT=8080

if [[ -z "$UTORID" ]]; then
  echo "Usage: $0 <utorid>"
  exit 1
fi

# Kill any existing tunnel on this port
pkill -f "ssh -fNT.*${LOCAL_PORT}:localhost:${REMOTE_PORT}" 2>/dev/null || true

SSH_OPTS="-o ServerAliveInterval=30 -o ServerAliveCountMax=3 -o ExitOnForwardFailure=yes -o StrictHostKeyChecking=accept-new"

# Use SSH key if available, otherwise fall back to password (will prompt)
if [[ -f ~/.ssh/csc301_vm ]]; then
  SSH_OPTS="$SSH_OPTS -i ~/.ssh/csc301_vm"
fi

ssh -fNT $SSH_OPTS -L "${LOCAL_PORT}:localhost:${REMOTE_PORT}" "${UTORID}@${HOST}"

echo "Tunnel open: localhost:${LOCAL_PORT} → ${HOST} → VM1:${REMOTE_PORT}"
echo ""
echo "Run k6:"
echo "  k6 run -e TARGET=http://localhost:${LOCAL_PORT} -e VUS=20 -e DURATION=30s load-gen.js"
echo ""
echo "Close tunnel:"
echo "  pkill -f 'ssh -fNT.*${LOCAL_PORT}:localhost'"
