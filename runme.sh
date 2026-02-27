#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

# Expected directories (per handout)
mkdir -p "$ROOT/compiled"/{UserService,ProductService,OrderService,ISCS,WorkloadParser,lib}

# Pick config.json from root first, else compiled/
CONFIG="$ROOT/config.json"
if [[ ! -f "$CONFIG" ]]; then
  CONFIG="$ROOT/compiled/config.json"
fi

# Build classpath
CP="$ROOT/compiled:$ROOT/lib/*:$ROOT/compiled/lib/*"

usage() {
  cat <<'EOF'
Usage:
  ./runme.sh -c
  ./runme.sh -u
  ./runme.sh -p
  ./runme.sh -i
  ./runme.sh -o
  ./runme.sh -a
  ./runme.sh -w workloadfile
  ./runme.sh -r

Flags:
  -c  compile all services
  -u  run UserService
  -p  run ProductService
  -i  run ISCS
  -o  run OrderService
  -a  run ALL microservices (foreground)
  -w  run WorkloadParser
  -r  remove users.db and products.db
EOF
}

run_java_main() {
  local main1="$1"
  local main2="$2"
  shift 2
  if java -cp "$CP" "$main1" "$@"; then
    return 0
  fi
  java -cp "$CP" "$main2" "$@"
}

compile_all() {
  echo "[runme] Compiling Java sources..."
  mapfile -t JAVA_FILES < <(find "$ROOT/src" -name "*.java" -type f 2>/dev/null || true)
  if [[ ${#JAVA_FILES[@]} -eq 0 ]]; then
    echo "[runme] ERROR: No Java files found"
    exit 1
  fi

  find "$ROOT/compiled" -name "*.class" -type f -delete 2>/dev/null || true
  javac -cp "$CP" -d "$ROOT/compiled" "${JAVA_FILES[@]}"
  echo "[runme] Compile complete."
}

reset_dbs() {
  echo "[runme] Resetting databases..."

  rm -f "$ROOT/users.db" "$ROOT/products.db" "$ROOT/orders.db"
  echo "  databases removed (if they existed)"
}

start_user() {
  run_java_main "UserService" "UserService.UserService" "$CONFIG"
}

start_product() {
  run_java_main "ProductService" "ProductService.ProductService" "$CONFIG"
}

start_iscs() {
  run_java_main "ISCS" "ISCS.ISCS" "$CONFIG"
}

start_order() {
  run_java_main "OrderService" "OrderService.OrderService" "$CONFIG"
}

start_all() {
  [[ -f "$CONFIG" ]] || { echo "[runme] ERROR: config.json not found"; exit 1; }

  if ! find "$ROOT/compiled" -name "*.class" -print -quit >/dev/null 2>&1; then
    compile_all
  fi

  echo "[runme] Starting all microservices (Ctrl+C to stop)..."

  trap 'echo; echo "[runme] Shutting down services..."; kill 0; exit 0' INT TERM

  run_java_main "UserService"    "UserService.UserService"    "$CONFIG" &
  run_java_main "ProductService" "ProductService.ProductService" "$CONFIG" &
  run_java_main "ISCS"           "ISCS.ISCS"                 "$CONFIG" &
  run_java_main "OrderService"   "OrderService.OrderService" "$CONFIG" &

  wait
}

start_workload() {
  local workload="${1:-}"
  if [[ -z "$workload" ]]; then
    echo "[runme] ERROR: missing workload file"
    exit 1
  fi
  [[ -f "$workload" ]] || { echo "[runme] ERROR: workload not found"; exit 1; }

  java -cp "$CP" WorkloadParser.WorkloadParser "$CONFIG" "$workload"
}

if [[ $# -lt 1 ]]; then
  usage
  exit 1
fi

case "$1" in
  -c) compile_all ;;
  -u) start_user ;;
  -p) start_product ;;
  -i) start_iscs ;;
  -o) start_order ;;
  -a) start_all ;;
  -w) shift; start_workload "${1:-}" ;;
  -r) reset_dbs ;;
  *) usage; exit 1 ;;
esac

