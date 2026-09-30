#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
SERVER_DIR="$(cd "${SCRIPT_DIR}/.." && pwd)"
REPO_ROOT="$(cd "${SERVER_DIR}/.." && pwd)"
RUN_DIR="${KANGER_DUMB2_SERVER_RUN_DIR:-${REPO_ROOT}/target/dumb2-soak/server}"
HOME_DIR="${RUN_DIR}/home"
CONF_FILE="$HOME_DIR/kanger.conf"
KANGER_ROOT="${KANGER_DUMB2_KANGER_HOME:-KANGER-DUMB2-SERVER}"

fail() {
  printf 'ERROR: %s\n' "$*" >&2
  exit 1
}

find_one() {
  local dir="$1"
  local pattern="$2"
  local label="$3"
  local matches=()
  while IFS= read -r file; do
    matches+=("$file")
  done < <(find "$dir" -maxdepth 1 -type f -name "$pattern" -print | sort)
  [[ "${#matches[@]}" -eq 1 ]] || fail "Expected exactly one ${label}, found ${#matches[@]}"
  printf '%s\n' "${matches[0]}"
}

if [[ "${KANGER_DUMB2_REBUILD:-1}" == "1" ]]; then
  (
    cd "$REPO_ROOT"
    mvn -B -ntp       -pl kanger-server,kanger-data-dumb2       -am       -Dkanger.build.branch.override=dumb2-soak       package
  )
fi

SERVER_TARGET="$SERVER_DIR/target"
RUNTIME_LIB="$SERVER_TARGET/runtime/lib"
RUNTIME_MODULES="$SERVER_TARGET/runtime/modules"
DUMB2_TARGET="$REPO_ROOT/kanger-data-dumb2/target"

[[ -f "$SERVER_TARGET/kanger-server-thin.jar" ]] || fail "Server thin JAR is missing; rebuild first"
[[ -d "$RUNTIME_LIB" ]] || fail "Server runtime/lib is missing; rebuild first"
[[ -d "$RUNTIME_MODULES" ]] || fail "Server runtime/modules is missing; rebuild first"

UDF_JAR="$(find_one "$RUNTIME_MODULES" 'kanger-udf-*.jar' 'UDF runtime module')"
DUMB2_JAR="$(find_one "$DUMB2_TARGET" 'kanger-data-dumb2-*.jar' 'DUMB2 runtime module')"

mkdir -p "$HOME_DIR"
if [[ ! -f "$CONF_FILE" ]]; then
  cp "$SERVER_DIR/config/kanger.local.conf.example" "$CONF_FILE"
  echo "Created DUMB2 local configuration: $CONF_FILE"
fi

CLASSPATH="$SERVER_TARGET/kanger-server-thin.jar:$RUNTIME_LIB/*:$UDF_JAR:$DUMB2_JAR"

cleanup() {
  rm -f "$HOME_DIR/$KANGER_ROOT/kanger.active"
}
trap cleanup EXIT

echo "KANGER DUMB2 Server manual soak"
echo "  user.home : $HOME_DIR"
echo "  KANGER_HOME: $KANGER_ROOT"
echo "  config    : $CONF_FILE"
echo "  storage   : DUMB 2.0 only (stable DUMB excluded from classpath)"
echo "  health    : http://127.0.0.1:1964/health"
echo

env KANGER_HOME="$KANGER_ROOT"   java -Duser.home="$HOME_DIR" -cp "$CLASSPATH" org.kanger.Kanger
