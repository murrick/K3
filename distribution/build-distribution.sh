#!/usr/bin/env bash
set -euo pipefail
export LC_ALL=C

PROGRAM_NAME="$(basename "$0")"
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"
VERSION_FILE="${SCRIPT_DIR}/VERSION"
PAYLOAD_DIR="${SCRIPT_DIR}/payload"
SERVER_POM="${REPO_ROOT}/kanger-server/pom.xml"
SERVER_JAR="${REPO_ROOT}/kanger-server/target/kanger-server.jar"
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

for command in mvn java jar unzip tar find sort awk grep tr date mktemp cp chmod mkdir rm; do
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

[[ -f "${SERVER_JAR}" ]] || fail "Expected Server artifact not found: ${SERVER_JAR}"
jar tf "${SERVER_JAR}" | grep -qx 'org/kanger/build.properties' \
  || fail "Server artifact does not contain org/kanger/build.properties"

BUILD_PROPERTIES="$(unzip -p "${SERVER_JAR}" org/kanger/build.properties)"
SERVER_VERSION="$(printf '%s\n' "${BUILD_PROPERTIES}" | property_from_stream server.version)"
SERVER_BUILD_DATE="$(printf '%s\n' "${BUILD_PROPERTIES}" | property_from_stream date)"
[[ -n "${SERVER_VERSION}" ]] || fail "Server artifact has no server.version metadata"
[[ -n "${SERVER_BUILD_DATE}" ]] || fail "Server artifact has no build date metadata"

STAGING_PARENT="$(mktemp -d "${TMPDIR:-/tmp}/kanger-distribution.XXXXXX")"
trap 'rm -rf -- "${STAGING_PARENT}"' EXIT
BUNDLE_NAME="kanger-${VERSION}"
BUNDLE_DIR="${STAGING_PARENT}/${BUNDLE_NAME}"
mkdir -p "${BUNDLE_DIR}/server" "${BUNDLE_DIR}/ui" "${BUNDLE_DIR}/bin"

cp -a "${PAYLOAD_DIR}/." "${BUNDLE_DIR}/"
cp "${SERVER_JAR}" "${BUNDLE_DIR}/server/kanger-server.jar"
cp "${SERVER_CONFIG}" "${BUNDLE_DIR}/server/kanger.conf.example"
cp -a "${UI_DIR}/." "${BUNDLE_DIR}/ui/"
[[ -z "$(find "${BUNDLE_DIR}" -type l -print -quit)" ]] || fail "Distribution payload must not contain symbolic links"
chmod 0755 "${BUNDLE_DIR}/install.sh" "${BUNDLE_DIR}/update.sh" "${BUNDLE_DIR}/bin/kanger-admin"
chmod 0644 "${BUNDLE_DIR}/lib/common.sh" \
  "${BUNDLE_DIR}/systemd/kanger.service.template" \
  "${BUNDLE_DIR}/nginx/kanger.conf.template" \
  "${BUNDLE_DIR}/server/kanger.conf.example"

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
