#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"
RUN_DIR="${KANGER_DUMB2_CONSOLE_RUN_DIR:-${REPO_ROOT}/target/dumb2-soak/console}"
HOME_DIR="${RUN_DIR}/home"
KANGER_ROOT="${KANGER_DUMB2_KANGER_HOME:-KANGER-DUMB2-CONSOLE}"

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
    mvn -B -ntp       -pl kanger-console,kanger-data-dumb2       -am       -Dkanger.build.branch.override=dumb2-soak       package
  )
fi

CONSOLE_TARGET="$REPO_ROOT/kanger-console/target"
RUNTIME_LIB="$CONSOLE_TARGET/runtime/lib"
RUNTIME_MODULES="$CONSOLE_TARGET/runtime/modules"
DUMB2_TARGET="$REPO_ROOT/kanger-data-dumb2/target"

[[ -d "$RUNTIME_LIB" ]] || fail "Console runtime/lib is missing; rebuild first"
[[ -d "$RUNTIME_MODULES" ]] || fail "Console runtime/modules is missing; rebuild first"
[[ -d "$DUMB2_TARGET" ]] || fail "DUMB2 target is missing; rebuild first"

CONSOLE_JAR="$(find_one "$CONSOLE_TARGET" '*-thin.jar' 'Console thin JAR')"
UDF_JAR="$(find_one "$RUNTIME_MODULES" 'kanger-udf-*.jar' 'UDF runtime module')"
DUMB2_JAR="$(find_one "$DUMB2_TARGET" 'kanger-data-dumb2-*.jar' 'DUMB2 runtime module')"

mkdir -p "$HOME_DIR"
CLASSPATH="$CONSOLE_JAR:$RUNTIME_LIB/*:$UDF_JAR:$DUMB2_JAR"

echo "KANGER DUMB2 Console manual soak"
echo "  user.home : $HOME_DIR"
echo "  KANGER_HOME: $KANGER_ROOT"
echo "  storage   : DUMB 2.0 only (stable DUMB excluded from classpath)"
echo

exec env KANGER_HOME="$KANGER_ROOT"   java -Duser.home="$HOME_DIR" -cp "$CLASSPATH" org.kanger.Kanger "$@"
