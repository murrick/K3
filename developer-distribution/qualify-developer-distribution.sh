#!/usr/bin/env bash
set -euo pipefail
export LC_ALL=C

ARCHIVE="${1:-}"

fail() {
  printf 'ERROR: %s\n' "$*" >&2
  exit 1
}

log() {
  printf '[qualify-developer-distribution] %s\n' "$*"
}

require_command() {
  command -v "$1" >/dev/null 2>&1 || fail "Required command not found: $1"
}

for command in java javac python3 tar find grep tr mktemp mkdir rm wc; do
  require_command "${command}"
done

[[ -n "${ARCHIVE}" ]] || fail "Usage: $0 <kanger-developer-*.tar.gz>"
[[ -f "${ARCHIVE}" ]] || fail "Developer distribution archive not found: ${ARCHIVE}"

log "runtime: $(java -version 2>&1 | head -n 1)"

STAGING_PARENT="$(mktemp -d "${TMPDIR:-/tmp}/kanger-developer-qualify.XXXXXX")"
trap 'rm -rf -- "${STAGING_PARENT}"' EXIT

tar -xzf "${ARCHIVE}" -C "${STAGING_PARENT}"

bundle_dirs=()
while IFS= read -r dir; do
  bundle_dirs+=("${dir}")
done < <(find "${STAGING_PARENT}" -mindepth 1 -maxdepth 1 -type d -print)
[[ "${#bundle_dirs[@]}" -eq 1 ]] \
  || fail "Expected exactly one top-level bundle directory, found ${#bundle_dirs[@]}"

BUNDLE_DIR="${bundle_dirs[0]}"
[[ -f "${BUNDLE_DIR}/VERSION" ]] || fail "Bundle VERSION file is missing"
VERSION="$(tr -d '[:space:]' < "${BUNDLE_DIR}/VERSION")"
[[ "$(basename "${BUNDLE_DIR}")" == "kanger-developer-${VERSION}" ]] \
  || fail "Bundle directory/version mismatch: $(basename "${BUNDLE_DIR}") vs ${VERSION}"

[[ -d "${BUNDLE_DIR}/lib" ]] || fail "Bundle lib directory is missing"
[[ -d "${BUNDLE_DIR}/examples" ]] || fail "Bundle examples directory is missing"
[[ -x "${BUNDLE_DIR}/bin/kanger-console" ]] || fail "Bundle Console launcher is missing or not executable"
[[ -f "${BUNDLE_DIR}/README.md" ]] || fail "Developer README is missing"
[[ -f "${BUNDLE_DIR}/docs/SDK.md" ]] || fail "Developer SDK guide is missing"
[[ -f "${BUNDLE_DIR}/docs/CONSOLE.md" ]] || fail "Developer Console guide is missing"
[[ -f "${BUNDLE_DIR}/examples/README.md" ]] || fail "Developer examples guide is missing"
[[ -f "${BUNDLE_DIR}/docs/api/index.html" ]] || fail "SDK JavaDoc index is missing"
[[ -f "${BUNDLE_DIR}/docs/api/org/kanger/interfaces/IMind.html" ]] || fail "IMind JavaDoc page is missing"
[[ -f "${BUNDLE_DIR}/docs/api/org/kanger/User.html" ]] || fail "User JavaDoc page is missing"

grep -Fq 'IUser user = new User();' "${BUNDLE_DIR}/docs/SDK.md" \
  || fail "SDK guide does not document the qualified User entry path"
grep -Fq 'IMind mind = new Mind(user);' "${BUNDLE_DIR}/docs/SDK.md" \
  || fail "SDK guide does not document the qualified Mind entry path"
grep -Fq 'org.kanger:kanger-sdk:3.7.0' "${BUNDLE_DIR}/docs/SDK.md" \
  || fail "SDK guide does not document the canonical Maven coordinate"
grep -Fq 'BinaryDataExample.java' "${BUNDLE_DIR}/docs/SDK.md" \
  || fail "SDK guide does not link the qualified binary-data example"
grep -Fq 'reindexStorage' "${BUNDLE_DIR}/docs/SDK.md" \
  || fail "SDK guide does not document storage maintenance"
grep -Fq 'api/org/kanger/interfaces/IMind.html' "${BUNDLE_DIR}/docs/SDK.md" \
  || fail "SDK guide does not link the IMind JavaDoc reference"
grep -Fq 'storage use <name>' "${BUNDLE_DIR}/docs/CONSOLE.md" \
  || fail "Console guide does not contain canonical storage syntax"
grep -Fq 'xplain mode on' "${BUNDLE_DIR}/docs/CONSOLE.md" \
  || fail "Console guide does not contain Console-local xplain syntax"
if grep -R -Fq '3.3-SNAPSHOT' \
  "${BUNDLE_DIR}/README.md" \
  "${BUNDLE_DIR}/docs/SDK.md" \
  "${BUNDLE_DIR}/docs/CONSOLE.md" \
  "${BUNDLE_DIR}/examples/README.md"; then
  fail "Internal reactor version leaked into Developer documentation"
fi

python3 - "${BUNDLE_DIR}/docs/api" <<'PY'
from pathlib import Path
import re
import sys

root = Path(sys.argv[1])
cyrillic = re.compile(r"[\u0400-\u04FF]")
checked = 0
leaks = []
for path in sorted(root.rglob("*")):
    if not path.is_file() or path.suffix.lower() not in {".html", ".js", ".css", ".txt"}:
        continue
    checked += 1
    if cyrillic.search(path.read_text(encoding="utf-8", errors="replace")):
        leaks.append(str(path.relative_to(root)))
if checked == 0:
    raise SystemExit("ERROR: unpacked SDK JavaDoc has no text files")
if leaks:
    raise SystemExit("ERROR: Cyrillic text leaked into unpacked SDK JavaDoc: " + ", ".join(leaks[:10]))
print("SDK_JAVADOC_ENGLISH_QUALIFICATION_PASS files=%d" % checked)
PY

log "SDK_DOCUMENTATION_QUALIFICATION_PASS version=${VERSION}"

jar_count="$(find "${BUNDLE_DIR}/lib" -maxdepth 1 -type f -name '*.jar' | wc -l | tr -d ' ')"
[[ "${jar_count}" -eq 7 ]] || fail "Expected exactly seven Developer runtime JARs, found ${jar_count}"
[[ ! -e "${BUNDLE_DIR}/server" ]] || fail "Server content leaked into Developer distribution"
[[ ! -e "${BUNDLE_DIR}/ui" ]] || fail "UI content leaked into Developer distribution"
[[ -z "$(find "${BUNDLE_DIR}" -type l -print -quit)" ]] || fail "Developer distribution must not contain symbolic links"

examples=(
  BasicQuery
  ValuesExample
  ParametersExample
  HypothesesExample
  TransactionExample
  StorageExample
  BinaryDataExample
)
for example in "${examples[@]}"; do
  [[ -f "${BUNDLE_DIR}/examples/${example}.java" ]] \
    || fail "Shipped example missing: ${example}.java"
done

QUALIFICATION="${STAGING_PARENT}/qualification"
CLASSES="${QUALIFICATION}/classes"
mkdir -p "${CLASSES}"
CLASSPATH="${BUNDLE_DIR}/lib/*"

log "compiling shipped Java examples from canonical artifact"
javac -cp "${CLASSPATH}" -d "${CLASSES}" \
  "${BUNDLE_DIR}/examples/BasicQuery.java" \
  "${BUNDLE_DIR}/examples/ValuesExample.java" \
  "${BUNDLE_DIR}/examples/ParametersExample.java" \
  "${BUNDLE_DIR}/examples/HypothesesExample.java" \
  "${BUNDLE_DIR}/examples/TransactionExample.java" \
  "${BUNDLE_DIR}/examples/StorageExample.java" \
  "${BUNDLE_DIR}/examples/BinaryDataExample.java"

for example in "${examples[@]}"; do
  home="${QUALIFICATION}/home-${example}"
  mkdir -p "${home}"
  log "running shipped example ${example}"
  JAVA_TOOL_OPTIONS="-Duser.home=${home}" \
    java -cp "${CLASSES}:${CLASSPATH}" "${example}"
done

log "launching shipped Console without Server/UI"
console_home="${QUALIFICATION}/console-home"
mkdir -p "${console_home}"
printf 'quit\n' | \
  JAVA_TOOL_OPTIONS="-Duser.home=${console_home}" \
  "${BUNDLE_DIR}/bin/kanger-console" \
  >"${QUALIFICATION}/console.log" 2>&1
grep -Fq 'KANGER III Session closed' "${QUALIFICATION}/console.log" \
  || { cat "${QUALIFICATION}/console.log" >&2; fail "Shipped Console did not close cleanly"; }

log "DEVELOPER_RUNTIME_QUALIFICATION_PASS version=${VERSION} examples=${#examples[@]}"
