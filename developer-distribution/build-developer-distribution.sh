#!/usr/bin/env bash
set -euo pipefail
export LC_ALL=C

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"
VERSION_FILE="${REPO_ROOT}/distribution/VERSION"
OUTPUT_DIR="${KANGER_DEVELOPER_OUTPUT_DIR:-${REPO_ROOT}/target/developer-distributions}"
CONSOLE_TARGET="${REPO_ROOT}/kanger-console/target"
RUNTIME_LIB="${CONSOLE_TARGET}/runtime/lib"
RUNTIME_MODULES="${CONSOLE_TARGET}/runtime/modules"
SDK_API_MANIFEST="${SCRIPT_DIR}/sdk-api-sources.txt"
SDK_JAVADOC_PREPARE="${SCRIPT_DIR}/prepare-sdk-javadoc.py"

fail() {
  printf 'ERROR: %s\n' "$*" >&2
  exit 1
}

log() {
  printf '[build-developer-distribution] %s\n' "$*"
}

require_command() {
  command -v "$1" >/dev/null 2>&1 || fail "Required command not found: $1"
}

copy_one() {
  local source_dir="$1"
  local pattern="$2"
  local target_name="$3"
  local matches=()
  shopt -s nullglob
  matches=("${source_dir}"/${pattern})
  shopt -u nullglob
  [[ "${#matches[@]}" -eq 1 ]] \
    || fail "Expected exactly one ${pattern} in ${source_dir}, found ${#matches[@]}"
  cp "${matches[0]}" "${BUNDLE_DIR}/lib/${target_name}"
}

for command in mvn javadoc python3 tar find grep tr mktemp cp chmod mkdir rm wc sort; do
  require_command "${command}"
done

[[ -f "${VERSION_FILE}" ]] || fail "Distribution VERSION not found: ${VERSION_FILE}"
[[ -f "${SDK_API_MANIFEST}" ]] || fail "SDK API manifest not found: ${SDK_API_MANIFEST}"
[[ -f "${SDK_JAVADOC_PREPARE}" ]] || fail "SDK JavaDoc source preparer not found: ${SDK_JAVADOC_PREPARE}"
VERSION="$(tr -d '[:space:]' < "${VERSION_FILE}")"
[[ "${VERSION}" =~ ^[0-9]+\.[0-9]+\.[0-9]+([.-][A-Za-z0-9._-]+)?$ ]] \
  || fail "Invalid KANGER version: ${VERSION}"

log "building thin Console runtime and SDK libraries"
(
  cd "${REPO_ROOT}"
  mvn --batch-mode --no-transfer-progress -pl kanger-console -am clean verify
)

[[ -d "${RUNTIME_LIB}" ]] || fail "Console runtime lib directory was not produced"
[[ -d "${RUNTIME_MODULES}" ]] || fail "Console runtime modules directory was not produced"

STAGING_PARENT="$(mktemp -d "${TMPDIR:-/tmp}/kanger-developer.XXXXXX")"
trap 'rm -rf -- "${STAGING_PARENT}"' EXIT

BUNDLE_NAME="kanger-developer-${VERSION}"
BUNDLE_DIR="${STAGING_PARENT}/${BUNDLE_NAME}"
BUILD_WORK="${STAGING_PARENT}/build-work"
JAVADOC_SOURCE="${BUILD_WORK}/javadoc-source"
JAVADOC_SOURCES="${BUILD_WORK}/javadoc-sources.txt"
API_DIR="${BUNDLE_DIR}/docs/api"
mkdir -p \
  "${BUNDLE_DIR}/bin" \
  "${BUNDLE_DIR}/lib" \
  "${BUNDLE_DIR}/examples" \
  "${API_DIR}" \
  "${BUILD_WORK}"

copy_one "${CONSOLE_TARGET}" '*-thin.jar' 'kanger-console.jar'
copy_one "${RUNTIME_LIB}" 'kanger-command-*.jar' 'kanger-command.jar'
copy_one "${RUNTIME_LIB}" 'kanger-core-*.jar' 'kanger-core.jar'
copy_one "${RUNTIME_LIB}" 'kanger-bootstrap-*.jar' 'kanger-bootstrap.jar'
copy_one "${RUNTIME_LIB}" 'jline-*.jar' 'jline.jar'
copy_one "${RUNTIME_MODULES}" 'kanger-udf-*.jar' 'kanger-udf.jar'
copy_one "${RUNTIME_MODULES}" 'kanger-data-dumb-*.jar' 'kanger-data-dumb.jar'

cp "${SCRIPT_DIR}/examples/BasicQuery.java" "${BUNDLE_DIR}/examples/"
cp "${SCRIPT_DIR}/examples/TransactionExample.java" "${BUNDLE_DIR}/examples/"
cp "${SCRIPT_DIR}/examples/StorageExample.java" "${BUNDLE_DIR}/examples/"
printf '%s\n' "${VERSION}" > "${BUNDLE_DIR}/VERSION"

cat > "${BUNDLE_DIR}/bin/kanger-console" <<'EOF_LAUNCHER'
#!/usr/bin/env bash
set -euo pipefail
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "${SCRIPT_DIR}/.." && pwd)"
exec java -cp "${ROOT_DIR}/lib/*" org.kanger.Kanger -S "$@"
EOF_LAUNCHER
chmod 0755 "${BUNDLE_DIR}/bin/kanger-console"

cat > "${BUNDLE_DIR}/bin/kanger-console.cmd" <<'EOF_CMD'
@echo off
set "ROOT_DIR=%~dp0.."
java -cp "%ROOT_DIR%\lib\*" org.kanger.Kanger -S %*
EOF_CMD
chmod 0644 "${BUNDLE_DIR}/bin/kanger-console.cmd"
find "${BUNDLE_DIR}/lib" -type f -name '*.jar' -exec chmod 0644 {} +
find "${BUNDLE_DIR}/examples" -type f -name '*.java' -exec chmod 0644 {} +

jar_count="$(find "${BUNDLE_DIR}/lib" -maxdepth 1 -type f -name '*.jar' | wc -l | tr -d ' ')"
[[ "${jar_count}" -eq 7 ]] || fail "Expected exactly seven Developer runtime JARs, found ${jar_count}"
[[ ! -e "${BUNDLE_DIR}/server" ]] || fail "Server content leaked into Developer distribution"
[[ ! -e "${BUNDLE_DIR}/ui" ]] || fail "UI content leaked into Developer distribution"
[[ -z "$(find "${BUNDLE_DIR}" -type l -print -quit)" ]] || fail "Developer distribution must not contain symbolic links"

CLASSPATH="${BUNDLE_DIR}/lib/*"

log "preparing curated SDK JavaDoc source view"
python3 "${SDK_JAVADOC_PREPARE}" \
  "${REPO_ROOT}" \
  "${SDK_API_MANIFEST}" \
  "${JAVADOC_SOURCE}"
find "${JAVADOC_SOURCE}" -type f -name '*.java' -print | sort > "${JAVADOC_SOURCES}"
[[ -s "${JAVADOC_SOURCES}" ]] || fail "SDK JavaDoc source list is empty"

log "generating SDK-only JavaDoc reference"
javadoc \
  -quiet \
  -public \
  -nodeprecated \
  -Xdoclint:none \
  -source 8 \
  -encoding UTF-8 \
  -charset UTF-8 \
  -docencoding UTF-8 \
  -classpath "${CLASSPATH}" \
  -windowtitle "KANGER ${VERSION} Developer SDK API" \
  -doctitle "KANGER ${VERSION} Developer SDK API" \
  -d "${API_DIR}" \
  @"${JAVADOC_SOURCES}"

required_api=(
  'org/kanger/User.html'
  'org/kanger/Mind.html'
  'org/kanger/ValuesOrder.html'
  'org/kanger/interfaces/IUser.html'
  'org/kanger/interfaces/IMind.html'
  'org/kanger/interfaces/ITerm.html'
  'org/kanger/enums/ArgumentType.html'
  'org/kanger/enums/DataType.html'
  'org/kanger/enums/LibMode.html'
  'org/kanger/enums/LogMode.html'
  'org/kanger/enums/StorageLifecycleErrorCode.html'
  'org/kanger/exception/RuntimeErrorException.html'
  'org/kanger/exception/StorageLifecycleException.html'
  'org/kanger/bootstrap/RuntimeBootstrap.html'
  'org/kanger/bootstrap/RuntimeBootstrapResult.html'
  'org/kanger/bootstrap/RuntimeCapability.html'
)
for page in "${required_api[@]}"; do
  [[ -f "${API_DIR}/${page}" ]] || fail "Required SDK JavaDoc page missing: ${page}"
done

for forbidden_dir in \
  'org/kanger/interfaces/internal' \
  'org/kanger/factory' \
  'org/kanger/stores' \
  'org/kanger/units' \
  'org/kanger/storage' \
  'org/kanger/compiler' \
  'org/kanger/calculator'; do
  [[ ! -d "${API_DIR}/${forbidden_dir}" ]] \
    || fail "Implementation package leaked into SDK JavaDoc: ${forbidden_dir}"
done

JAVADOC_LEAKS="${BUILD_WORK}/javadoc-leaks.log"
if grep -R -n -E \
  'org\.kanger\.interfaces\.internal|org/kanger/interfaces/internal|org\.kanger\.factory|org/kanger/factory|org\.kanger\.stores|org/kanger/stores|org\.kanger\.units|org/kanger/units|org\.kanger\.storage|org/kanger/storage|IBase|IData|IUnit' \
  "${API_DIR}" >"${JAVADOC_LEAKS}" 2>&1; then
  cat "${JAVADOC_LEAKS}" >&2
  fail "Implementation-only Core API leaked into SDK JavaDoc"
fi
log "SDK_JAVADOC_PASS curated-surface"

mkdir -p "${OUTPUT_DIR}"
rm -rf "${OUTPUT_DIR:?}/${BUNDLE_NAME}" "${OUTPUT_DIR}/${BUNDLE_NAME}.tar.gz"
cp -a "${BUNDLE_DIR}" "${OUTPUT_DIR}/${BUNDLE_NAME}"
tar -czf "${OUTPUT_DIR}/${BUNDLE_NAME}.tar.gz" -C "${STAGING_PARENT}" "${BUNDLE_NAME}"

log "Developer distribution built: ${OUTPUT_DIR}/${BUNDLE_NAME}.tar.gz"
