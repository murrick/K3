#!/usr/bin/env bash
set -euo pipefail
export LC_ALL=C

PROGRAM_NAME="$(basename "$0")"
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"
VERSION_FILE="${SCRIPT_DIR}/VERSION"
PAYLOAD_DIR="${SCRIPT_DIR}/payload"
SERVER_POM="${REPO_ROOT}/kanger-server/pom.xml"
SERVER_THIN_JAR="${REPO_ROOT}/kanger-server/target/kanger-server-thin.jar"
SERVER_RUNTIME_LIB="${REPO_ROOT}/kanger-server/target/runtime/lib"
SERVER_RUNTIME_MODULES="${REPO_ROOT}/kanger-server/target/runtime/modules"
SERVER_CONFIG="${REPO_ROOT}/kanger-server/deploy/kanger.conf.example"
UI_DIR="${REPO_ROOT}/html"
OUTPUT_DIR="${KANGER_DISTRIBUTION_OUTPUT_DIR:-${REPO_ROOT}/target/distributions}"

fail() {
  printf 'ERROR: %s\n' "$*" >&2
  exit 1
}

log() {
  printf '[build-distribution] %s\n' "$*"
}

require_command() {
  command -v "$1" >/dev/null 2>&1 || fail "Required command not found: $1"
}

sha256_file() {
  local file="$1"
  if command -v sha256sum >/dev/null 2>&1; then
    sha256sum "${file}" | awk '{print $1}'
  elif command -v shasum >/dev/null 2>&1; then
    shasum -a 256 "${file}" | awk '{print $1}'
  elif command -v openssl >/dev/null 2>&1; then
    openssl dgst -sha256 "${file}" | awk '{print $NF}'
  else
    fail "No SHA-256 implementation found (sha256sum, shasum or openssl)"
  fi
}

property_from_stream() {
  local name="$1"
  awk -F= -v key="${name}" '$1 == key {sub(/^[^=]*=/, ""); print; exit}'
}

qualify_thin_runtime() (
  set -euo pipefail

  local bundle_dir="$1"
  local runtime="${STAGING_PARENT}/thin-runtime-smoke"
  local classpath="${bundle_dir}/server/kanger-server-thin.jar:${bundle_dir}/server/lib/*:${bundle_dir}/server/modules/*"
  local server_port="${KANGER_DISTRIBUTION_SMOKE_PORT:-29640}"
  local server_pid=""
  local shutdown_status
  local health_response
  local ready=false

  [[ "${server_port}" =~ ^[0-9]+$ ]] || fail "Invalid thin-runtime smoke port: ${server_port}"
  (( server_port >= 1024 && server_port <= 65535 )) \
    || fail "Thin-runtime smoke port is outside 1024..65535: ${server_port}"

  mkdir -p "${runtime}/probe" "${runtime}/probe-classes" "${runtime}/home"

  cat > "${runtime}/probe/RuntimeModuleProbe.java" <<'JAVA_PROBE'
import java.util.EnumMap;
import java.util.Map;
import java.util.ServiceLoader;

import org.kanger.bootstrap.RuntimeCapability;
import org.kanger.bootstrap.RuntimeModule;

public final class RuntimeModuleProbe {
    private RuntimeModuleProbe() {
    }

    public static void main(String[] args) {
        Map<RuntimeCapability, Integer> counts =
                new EnumMap<RuntimeCapability, Integer>(RuntimeCapability.class);
        for (RuntimeCapability capability : RuntimeCapability.values()) {
            counts.put(capability, Integer.valueOf(0));
        }

        for (RuntimeModule module : ServiceLoader.load(RuntimeModule.class)) {
            RuntimeCapability capability = module.getCapability();
            if (capability == null) {
                throw new IllegalStateException("Runtime module without capability: " + module.getClass().getName());
            }
            counts.put(capability, Integer.valueOf(counts.get(capability).intValue() + 1));
            System.out.println(capability + "=" + module.getId() + " (" + module.getClass().getName() + ")");
        }

        for (RuntimeCapability capability : RuntimeCapability.values()) {
            int count = counts.get(capability).intValue();
            if (count != 1) {
                throw new IllegalStateException(
                        "Expected exactly one " + capability + " runtime module; discovered " + count);
            }
        }
    }
}
JAVA_PROBE

  log "qualifying staged ServiceLoader runtime modules"
  javac -cp "${classpath}" \
    -d "${runtime}/probe-classes" \
    "${runtime}/probe/RuntimeModuleProbe.java"
  java -cp "${runtime}/probe-classes:${classpath}" RuntimeModuleProbe

  cat > "${runtime}/home/kanger.conf" <<EOF_CONFIG
server.bind.address=127.0.0.1
server.port=${server_port}
server.backlog=16
server.maxthreads=4
server.queue.capacity=16
server.request.max.body.bytes=1048576
server.watchdog.period=1000
server.admin.enabled=false
EOF_CONFIG

  cleanup_smoke() {
    local status=$?
    trap - EXIT
    if [[ -n "${server_pid}" ]] && kill -0 "${server_pid}" 2>/dev/null; then
      kill -TERM "${server_pid}" 2>/dev/null || true
      wait "${server_pid}" 2>/dev/null || true
    fi
    if [[ "${status}" -ne 0 ]]; then
      printf '%s\n' '--- staged thin-runtime smoke log ---' >&2
      cat "${runtime}/server.log" >&2 || true
    fi
    exit "${status}"
  }
  trap cleanup_smoke EXIT

  log "starting staged thin runtime on loopback port ${server_port}"
  java \
    -Duser.home="${runtime}/home" \
    -cp "${classpath}" \
    org.kanger.Kanger \
    >"${runtime}/server.log" 2>&1 &
  server_pid=$!

  for ((attempt = 1; attempt <= 30; attempt++)); do
    kill -0 "${server_pid}" 2>/dev/null \
      || fail "Staged thin Server exited before becoming healthy"
    if health_response="$(curl --fail --silent --show-error --max-time 2 \
      "http://127.0.0.1:${server_port}/health" 2>/dev/null)"; then
      ready=true
      break
    fi
    sleep 1
  done
  [[ "${ready}" == true ]] || fail "Staged thin Server did not become healthy"
  printf '%s\n' "${health_response}" | grep -q '"status":"UP"' \
    || fail "Staged thin Server health did not report UP"
  printf '%s\n' "${health_response}" | grep -q '"version":"3.7.0"' \
    || fail "Staged thin Server health has unexpected Core version"
  printf '%s\n' "${health_response}" | grep -q '"server_version":"server-0.18"' \
    || fail "Staged thin Server health has unexpected Server version"
  [[ -f "${runtime}/home/KANGER/kanger.active" ]] \
    || fail "Staged thin Server did not create its active marker"

  kill -TERM "${server_pid}"
  set +e
  wait "${server_pid}"
  shutdown_status=$?
  set -e
  server_pid=""

  [[ "${shutdown_status}" -eq 0 || "${shutdown_status}" -eq 143 ]] \
    || fail "Staged thin Server shutdown returned ${shutdown_status}"
  [[ ! -e "${runtime}/home/KANGER/kanger.active" ]] \
    || fail "Staged thin Server left its active marker after shutdown"
  ! curl --silent --max-time 1 \
    "http://127.0.0.1:${server_port}/health" >/dev/null 2>&1 \
    || fail "Staged thin Server still answers after shutdown"

  trap - EXIT
  log "staged thin runtime qualified"
)

for command in mvn java javac jar unzip tar find sort awk grep tr date mktemp cp chmod mkdir rm curl sleep; do
  require_command "${command}"
done

[[ -f "${VERSION_FILE}" ]] || fail "Distribution VERSION not found: ${VERSION_FILE}"
[[ -d "${PAYLOAD_DIR}" ]] || fail "Distribution payload not found: ${PAYLOAD_DIR}"
[[ -f "${SERVER_POM}" ]] || fail "KANGER Server POM not found: ${SERVER_POM}"
[[ -f "${SERVER_CONFIG}" ]] || fail "KANGER Server configuration template not found: ${SERVER_CONFIG}"
[[ -f "${UI_DIR}/index.html" ]] || fail "Browser UI root not found: ${UI_DIR}"
[[ -f "${PAYLOAD_DIR}/bin/kanger-admin" ]] || fail "KANGER admin launcher not found in distribution payload"

VERSION="$(tr -d '[:space:]' < "${VERSION_FILE}")"
[[ "${VERSION}" =~ ^[0-9]+\.[0-9]+\.[0-9]+([.-][A-Za-z0-9._-]+)?$ ]] \
  || fail "Invalid distribution version: ${VERSION}"

log "qualifying canonical reactor build"
(
  cd "${REPO_ROOT}"
  mvn -B clean verify
)

[[ -f "${SERVER_THIN_JAR}" ]] || fail "Expected thin Server artifact not found: ${SERVER_THIN_JAR}"
[[ -d "${SERVER_RUNTIME_LIB}" ]] || fail "Expected Server runtime libraries not found: ${SERVER_RUNTIME_LIB}"
[[ -d "${SERVER_RUNTIME_MODULES}" ]] || fail "Expected Server runtime modules not found: ${SERVER_RUNTIME_MODULES}"

shopt -s nullglob
udf_modules=("${SERVER_RUNTIME_MODULES}"/kanger-udf-*.jar)
storage_modules=("${SERVER_RUNTIME_MODULES}"/kanger-data-dumb-*.jar)
leaked_udf=("${SERVER_RUNTIME_LIB}"/kanger-udf-*.jar)
leaked_storage=("${SERVER_RUNTIME_LIB}"/kanger-data-dumb-*.jar)
shopt -u nullglob
[[ "${#udf_modules[@]}" -eq 1 ]] || fail "Expected exactly one UDF runtime module"
[[ "${#storage_modules[@]}" -eq 1 ]] || fail "Expected exactly one DUMB storage runtime module"
[[ "${#leaked_udf[@]}" -eq 0 ]] || fail "UDF provider leaked into runtime/lib"
[[ "${#leaked_storage[@]}" -eq 0 ]] || fail "DUMB storage provider leaked into runtime/lib"

jar tf "${SERVER_THIN_JAR}" | grep -qx 'org/kanger/build.properties' \
  || fail "Thin Server artifact does not contain org/kanger/build.properties"

BUILD_PROPERTIES="$(unzip -p "${SERVER_THIN_JAR}" org/kanger/build.properties)"
SERVER_VERSION="$(printf '%s\n' "${BUILD_PROPERTIES}" | property_from_stream server.version)"
SERVER_BUILD_DATE="$(printf '%s\n' "${BUILD_PROPERTIES}" | property_from_stream date)"
[[ -n "${SERVER_VERSION}" ]] || fail "Thin Server artifact has no server.version metadata"
[[ -n "${SERVER_BUILD_DATE}" ]] || fail "Thin Server artifact has no build date metadata"

STAGING_PARENT="$(mktemp -d "${TMPDIR:-/tmp}/kanger-distribution.XXXXXX")"
trap 'rm -rf -- "${STAGING_PARENT}"' EXIT
BUNDLE_NAME="kanger-${VERSION}"
BUNDLE_DIR="${STAGING_PARENT}/${BUNDLE_NAME}"
mkdir -p \
  "${BUNDLE_DIR}/server/lib" \
  "${BUNDLE_DIR}/server/modules" \
  "${BUNDLE_DIR}/ui" \
  "${BUNDLE_DIR}/bin"

cp -a "${PAYLOAD_DIR}/." "${BUNDLE_DIR}/"
cp "${SERVER_THIN_JAR}" "${BUNDLE_DIR}/server/kanger-server-thin.jar"
cp -a "${SERVER_RUNTIME_LIB}/." "${BUNDLE_DIR}/server/lib/"
cp -a "${SERVER_RUNTIME_MODULES}/." "${BUNDLE_DIR}/server/modules/"
cp "${SERVER_CONFIG}" "${BUNDLE_DIR}/server/kanger.conf.example"
cp -a "${UI_DIR}/." "${BUNDLE_DIR}/ui/"
[[ -z "$(find "${BUNDLE_DIR}" -type l -print -quit)" ]] || fail "Distribution payload must not contain symbolic links"
chmod 0755 "${BUNDLE_DIR}/install.sh" "${BUNDLE_DIR}/update.sh" "${BUNDLE_DIR}/bin/kanger-admin"
chmod 0644 "${BUNDLE_DIR}/lib/common.sh" \
  "${BUNDLE_DIR}/systemd/kanger.service.template" \
  "${BUNDLE_DIR}/nginx/kanger.conf.template" \
  "${BUNDLE_DIR}/server/kanger.conf.example"
find "${BUNDLE_DIR}/server" -type f -name '*.jar' -exec chmod 0644 {} +

qualify_thin_runtime "${BUNDLE_DIR}"

PACKAGED_AT="$(date -u +%Y-%m-%dT%H:%M:%SZ)"
cat > "${BUNDLE_DIR}/RELEASE" <<RELEASE
product=KANGER
version=${VERSION}
server.version=${SERVER_VERSION}
server.build.date=${SERVER_BUILD_DATE}
packaged.at=${PACKAGED_AT}
RELEASE

(
  cd "${BUNDLE_DIR}"
  : > SHA256SUMS
  while IFS= read -r file; do
    hash="$(sha256_file "${file}")"
    printf '%s  %s\n' "${hash}" "${file}" >> SHA256SUMS
  done < <(find . -type f ! -name SHA256SUMS -print | sort)

  while read -r hash file; do
    [[ -n "${hash}" && -n "${file}" ]] || fail "Malformed SHA256SUMS entry"
    actual="$(sha256_file "${file}")"
    [[ "${actual}" == "${hash}" ]] || fail "Self-check failed for ${file}"
  done < SHA256SUMS
)

mkdir -p "${OUTPUT_DIR}"
ARCHIVE="${OUTPUT_DIR}/${BUNDLE_NAME}.tar.gz"
rm -f -- "${ARCHIVE}"
tar -C "${STAGING_PARENT}" -czf "${ARCHIVE}" "${BUNDLE_NAME}"

log "distribution ready"
printf '  version      : %s\n' "${VERSION}"
printf '  server       : %s\n' "${SERVER_VERSION}"
printf '  server build : %s\n' "${SERVER_BUILD_DATE}"
printf '  archive      : %s\n' "${ARCHIVE}"
printf '  SHA-256      : %s\n' "$(sha256_file "${ARCHIVE}")"
