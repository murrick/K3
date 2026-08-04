#!/usr/bin/env bash
set -euo pipefail

PROGRAM_NAME="$(basename "$0")"
DEFAULT_REPO_URL="https://github.com/murrick/K3.git"
DEFAULT_REF="develop/server/0.13"
DEFAULT_CHECKOUT_DIR="${XDG_CACHE_HOME:-${HOME}/.cache}/kanger-server-updater/K3"
DEFAULT_SSH_TARGET="murray@94.103.94.41"
DEFAULT_SSH_PORT="4211"
DEFAULT_PUBLIC_API_URL="https://api.kanger.org"
DEFAULT_PUBLIC_UI_URL="https://kanger.org"
REMOTE_RECEIPT_PATH="/opt/kanger-server/deployment.properties"
REMOTE_LOCK_PATH="/run/lock/kanger-server-update.lock"

REPO_URL="${KANGER_REPO_URL:-${DEFAULT_REPO_URL}}"
REF="${KANGER_REF:-${DEFAULT_REF}}"
CHECKOUT_DIR="${KANGER_CHECKOUT_DIR:-${DEFAULT_CHECKOUT_DIR}}"
SSH_TARGET="${KANGER_SSH_TARGET:-${DEFAULT_SSH_TARGET}}"
SSH_PORT="${KANGER_SSH_PORT:-${DEFAULT_SSH_PORT}}"
PUBLIC_API_URL="${KANGER_PUBLIC_API_URL:-${DEFAULT_PUBLIC_API_URL}}"
PUBLIC_UI_URL="${KANGER_PUBLIC_UI_URL:-${DEFAULT_PUBLIC_UI_URL}}"
FORCE=false
DRY_RUN=false
PUBLIC_CHECKS=true
REMOTE_DIR=""
REMOTE_CREATED=false
LOCAL_CACHED_JAR=""
ARTIFACT_VERSION=""
JAR_SHA256=""
BUILD_DATE=""
OPERATION=""

usage() {
  cat <<USAGE
Usage: ${PROGRAM_NAME} [options]

Fetch, qualify and deploy the latest commit from a controlled KANGER release ref.
The default source is the stable shelf branch ${DEFAULT_REF}.

Options:
  --ref REF              Git branch, tag or commit to deploy
  --repo-url URL         Git repository URL
  --checkout DIR         Dedicated local deployment checkout/cache
  --target USER@HOST     SSH destination
  --port PORT            SSH port
  --api-url URL          Public API base URL
  --ui-url URL           Public UI URL
  --force                Rebuild and reinstall even when source commit matches
  --no-public-checks     Skip checks through Cloudflare/public nginx
  --dry-run              Print the resolved plan without changing anything
  -h, --help             Show this help

Environment equivalents:
  KANGER_REF, KANGER_REPO_URL, KANGER_CHECKOUT_DIR,
  KANGER_SSH_TARGET, KANGER_SSH_PORT,
  KANGER_PUBLIC_API_URL, KANGER_PUBLIC_UI_URL
USAGE
}

fail() {
  echo "ERROR: $*" >&2
  exit 1
}

log() {
  printf '[kanger-update] %s\n' "$*"
}

require_command() {
  command -v "$1" >/dev/null 2>&1 || fail "Required command not found: $1"
}

property_value() {
  local property_name="$1"
  awk -F= -v name="${property_name}" '$1 == name {sub(/^[^=]*=/, ""); print; exit}'
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

cleanup() {
  local status=$?
  trap - EXIT
  if [[ "${REMOTE_CREATED}" == true && -n "${REMOTE_DIR}" ]]; then
    ssh -p "${SSH_PORT}" "${SSH_TARGET}" \
      "rm -rf -- '${REMOTE_DIR}'" >/dev/null 2>&1 || true
  fi
  if [[ -n "${LOCAL_CACHED_JAR}" ]]; then
    rm -f -- "${LOCAL_CACHED_JAR}" >/dev/null 2>&1 || true
  fi
  exit "${status}"
}
trap cleanup EXIT

stage_remote_assets() {
  REMOTE_DIR="/tmp/kanger-update-${SHORT_COMMIT}-$$"
  log "preparing remote staging directory ${REMOTE_DIR}"
  ssh "${SSH_ARGS[@]}" \
    "rm -rf -- '${REMOTE_DIR}' && mkdir -m 700 -- '${REMOTE_DIR}' '${REMOTE_DIR}/deploy'"
  REMOTE_CREATED=true

  scp -P "${SSH_PORT}" -r "${DEPLOY_DIR}/." \
    "${SSH_TARGET}:${REMOTE_DIR}/deploy/"
}

verify_remote() {
  log "verifying installed service and nginx boundary"
  ssh -tt "${SSH_ARGS[@]}" \
    "sudo bash '${REMOTE_DIR}/deploy/verify-installed.sh' && \
     curl --fail --silent --show-error http://127.0.0.1:1964/health | grep -Eq '\"(server_version|version)\":\"${ARTIFACT_VERSION}\"' && \
     curl --fail --silent --show-error http://127.0.0.1:1964/ready | grep -Eq '\"(server_version|version)\":\"${ARTIFACT_VERSION}\"'"
}

verify_public_api() {
  if [[ "${PUBLIC_CHECKS}" != true ]]; then
    return
  fi

  log "checking public API"
  local public_health ready_status
  public_health="$(curl --fail --silent --show-error --max-time 15 \
    "${PUBLIC_API_URL%/}/health")"
  printf '%s\n' "${public_health}" | grep -q '"status":"UP"' \
    || fail "Public /health is not UP"
  printf '%s\n' "${public_health}" | grep -Eq "\"(server_version|version)\":\"${ARTIFACT_VERSION}\"" \
    || fail "Public /health does not expose server version ${ARTIFACT_VERSION}"

  ready_status="$(curl --silent --show-error --max-time 15 \
    --output /dev/null --write-out '%{http_code}' \
    "${PUBLIC_API_URL%/}/ready")"
  [[ "${ready_status}" == "403" ]] \
    || fail "Public /ready returned HTTP ${ready_status}, expected 403"
}

check_public_ui_advisory() {
  if [[ "${PUBLIC_CHECKS}" != true ]]; then
    return
  fi

  log "checking public UI (advisory)"
  local ui_result ui_status
  if ! ui_result="$(curl --head --silent --show-error \
      --connect-timeout 5 --max-time 10 \
      --output /dev/null --write-out '%{http_code}' \
      "${PUBLIC_UI_URL}" 2>&1)"; then
    log "WARNING: public UI check did not complete: ${ui_result}"
    return
  fi

  ui_status="${ui_result}"
  if [[ ! "${ui_status}" =~ ^(200|301|302|307|308)$ ]]; then
    log "WARNING: public UI returned HTTP ${ui_status}; backend deployment remains valid"
  fi
}

write_receipt() {
  mkdir -p "$(dirname "${LOCAL_RECEIPT_FILE}")"
  cat > "${LOCAL_RECEIPT_FILE}" <<RECEIPT
artifact.version=${ARTIFACT_VERSION}
source.ref=${REF}
source.commit=${COMMIT}
jar.sha256=${JAR_SHA256}
build.date=${BUILD_DATE}
deployed.at=$(date -u +%Y-%m-%dT%H:%M:%SZ)
RECEIPT

  scp -P "${SSH_PORT}" "${LOCAL_RECEIPT_FILE}" \
    "${SSH_TARGET}:${REMOTE_DIR}/deployment.properties"
  ssh -tt "${SSH_ARGS[@]}" \
    "sudo install -o root -g root -m 0644 \
       '${REMOTE_DIR}/deployment.properties' '${REMOTE_RECEIPT_PATH}'"
}

print_receipt() {
  cat <<RECEIPT

KANGER Server update completed.
  operation: ${OPERATION}
  artifact : ${ARTIFACT_VERSION}
  source   : ${REF}
  commit   : ${COMMIT}
  SHA-256  : ${JAR_SHA256:-recorded on VPS}
  target   : ${SSH_TARGET}:${SSH_PORT}

Persistent configuration and state were retained by install.sh.
Registration/login continuity should be checked with the existing production user.
RECEIPT
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --ref)
      [[ $# -ge 2 ]] || fail "--ref requires a value"
      REF="$2"
      shift 2
      ;;
    --repo-url)
      [[ $# -ge 2 ]] || fail "--repo-url requires a value"
      REPO_URL="$2"
      shift 2
      ;;
    --checkout)
      [[ $# -ge 2 ]] || fail "--checkout requires a value"
      CHECKOUT_DIR="$2"
      shift 2
      ;;
    --target)
      [[ $# -ge 2 ]] || fail "--target requires a value"
      SSH_TARGET="$2"
      shift 2
      ;;
    --port)
      [[ $# -ge 2 ]] || fail "--port requires a value"
      SSH_PORT="$2"
      shift 2
      ;;
    --api-url)
      [[ $# -ge 2 ]] || fail "--api-url requires a value"
      PUBLIC_API_URL="$2"
      shift 2
      ;;
    --ui-url)
      [[ $# -ge 2 ]] || fail "--ui-url requires a value"
      PUBLIC_UI_URL="$2"
      shift 2
      ;;
    --force)
      FORCE=true
      shift
      ;;
    --no-public-checks)
      PUBLIC_CHECKS=false
      shift
      ;;
    --dry-run)
      DRY_RUN=true
      shift
      ;;
    -h|--help)
      usage
      exit 0
      ;;
    *)
      fail "Unknown option: $1"
      ;;
  esac
done

[[ "${REF}" =~ ^[A-Za-z0-9._/@+-]+$ ]] || fail "Unsafe Git ref: ${REF}"
[[ "${SSH_PORT}" =~ ^[0-9]+$ ]] || fail "SSH port must be numeric: ${SSH_PORT}"
(( SSH_PORT >= 1 && SSH_PORT <= 65535 )) || fail "SSH port out of range: ${SSH_PORT}"
[[ -n "${SSH_TARGET}" ]] || fail "SSH target must not be empty"

log "repository : ${REPO_URL}"
log "source ref : ${REF}"
log "checkout   : ${CHECKOUT_DIR}"
log "target     : ${SSH_TARGET}:${SSH_PORT}"
log "public API : ${PUBLIC_API_URL}"
log "public UI  : ${PUBLIC_UI_URL}"

if [[ "${DRY_RUN}" == true ]]; then
  log "dry-run: no repository, build, SSH or HTTP action was performed"
  exit 0
fi

for command in git mvn java jar unzip ssh scp curl awk grep find date; do
  require_command "${command}"
done

mkdir -p "$(dirname "${CHECKOUT_DIR}")"
if [[ ! -d "${CHECKOUT_DIR}/.git" ]]; then
  [[ ! -e "${CHECKOUT_DIR}" || -d "${CHECKOUT_DIR}" ]] \
    || fail "Checkout path exists and is not a directory: ${CHECKOUT_DIR}"
  if [[ -d "${CHECKOUT_DIR}" && -n "$(find "${CHECKOUT_DIR}" -mindepth 1 -maxdepth 1 -print -quit)" ]]; then
    fail "Checkout directory exists but is not a Git repository: ${CHECKOUT_DIR}"
  fi
  log "cloning deployment checkout"
  git clone --no-tags "${REPO_URL}" "${CHECKOUT_DIR}"
else
  actual_origin="$(git -C "${CHECKOUT_DIR}" remote get-url origin)"
  [[ "${actual_origin}" == "${REPO_URL}" ]] \
    || fail "Checkout origin is ${actual_origin}, expected ${REPO_URL}"
fi

PREVIOUS_CHECKOUT_COMMIT="$(git -C "${CHECKOUT_DIR}" rev-parse --verify HEAD 2>/dev/null || true)"
PREVIOUS_JAR_FILE="${CHECKOUT_DIR}/kanger-server/target/kanger-server.jar"
if [[ -f "${PREVIOUS_JAR_FILE}" ]]; then
  LOCAL_CACHED_JAR="$(mktemp "${TMPDIR:-/tmp}/kanger-update-cached.XXXXXX.jar")"
  cp -- "${PREVIOUS_JAR_FILE}" "${LOCAL_CACHED_JAR}"
fi

log "fetching latest repository state"
git -C "${CHECKOUT_DIR}" fetch --prune --tags origin

resolved_ref=""
if git -C "${CHECKOUT_DIR}" show-ref --verify --quiet "refs/remotes/origin/${REF}"; then
  resolved_ref="refs/remotes/origin/${REF}"
elif git -C "${CHECKOUT_DIR}" show-ref --verify --quiet "refs/tags/${REF}"; then
  resolved_ref="refs/tags/${REF}"
elif git -C "${CHECKOUT_DIR}" rev-parse --verify --quiet "${REF}^{commit}" >/dev/null; then
  resolved_ref="${REF}"
else
  fail "Unable to resolve Git ref after fetch: ${REF}"
fi

COMMIT="$(git -C "${CHECKOUT_DIR}" rev-parse "${resolved_ref}^{commit}")"
SHORT_COMMIT="$(git -C "${CHECKOUT_DIR}" rev-parse --short=12 "${COMMIT}")"
log "resolved commit: ${COMMIT}"

git -C "${CHECKOUT_DIR}" checkout --detach --force "${COMMIT}"
git -C "${CHECKOUT_DIR}" reset --hard "${COMMIT}"
git -C "${CHECKOUT_DIR}" clean -ffd

POM="${CHECKOUT_DIR}/kanger-server/pom.xml"
JAR_FILE="${CHECKOUT_DIR}/kanger-server/target/kanger-server.jar"
DEPLOY_DIR="${CHECKOUT_DIR}/kanger-server/deploy"
LOCAL_RECEIPT_FILE="${CHECKOUT_DIR}/kanger-server/target/deployment.properties"
[[ -f "${POM}" ]] || fail "Server POM not found at ${POM}"
[[ -f "${DEPLOY_DIR}/install.sh" ]] || fail "Installer not found in ${DEPLOY_DIR}"
[[ -f "${DEPLOY_DIR}/verify-installed.sh" ]] || fail "Verifier not found in ${DEPLOY_DIR}"

SSH_ARGS=(-p "${SSH_PORT}" "${SSH_TARGET}")
REMOTE_RECEIPT="$(ssh "${SSH_ARGS[@]}" \
  "command -v sha256sum >/dev/null && command -v flock >/dev/null; \
   if test -r '${REMOTE_RECEIPT_PATH}'; then cat '${REMOTE_RECEIPT_PATH}'; fi")"
REMOTE_COMMIT="$(printf '%s\n' "${REMOTE_RECEIPT}" | property_value source.commit)"
REMOTE_ARTIFACT_VERSION="$(printf '%s\n' "${REMOTE_RECEIPT}" | property_value artifact.version)"
REMOTE_RECORDED_SHA="$(printf '%s\n' "${REMOTE_RECEIPT}" | property_value jar.sha256)"
REMOTE_SHA256="$(ssh "${SSH_ARGS[@]}" \
  "if test -f /opt/kanger-server/kanger-server.jar; then sha256sum /opt/kanger-server/kanger-server.jar | awk '{print \$1}'; fi")"

if [[ "${FORCE}" != true && -n "${REMOTE_COMMIT}" && "${REMOTE_COMMIT}" == "${COMMIT}" ]]; then
  [[ -n "${REMOTE_ARTIFACT_VERSION}" ]] \
    || fail "Deployment receipt has source.commit but no artifact.version"
  ARTIFACT_VERSION="${REMOTE_ARTIFACT_VERSION}"
  JAR_SHA256="${REMOTE_RECORDED_SHA}"
  OPERATION="no-op (source commit already deployed)"
  log "source commit is already deployed; skipping build and restart"
  stage_remote_assets
  verify_remote
  verify_public_api
  check_public_ui_advisory
  print_receipt
  exit 0
fi

if [[ "${FORCE}" != true && \
      -n "${PREVIOUS_CHECKOUT_COMMIT}" && \
      "${PREVIOUS_CHECKOUT_COMMIT}" == "${COMMIT}" && \
      -n "${REMOTE_SHA256}" && \
      -n "${LOCAL_CACHED_JAR}" && \
      -f "${LOCAL_CACHED_JAR}" ]]; then
  CACHED_JAR_SHA256="$(sha256_file "${LOCAL_CACHED_JAR}")"
  if [[ "${CACHED_JAR_SHA256}" == "${REMOTE_SHA256}" ]]; then
    log "recovering deployment receipt from the previously qualified installed JAR"
    jar tf "${LOCAL_CACHED_JAR}" | grep -q '^org/kanger/Kanger.class$' \
      || fail "Cached JAR does not contain org/kanger/Kanger.class"

    BUILD_PROPERTIES="$(unzip -p "${LOCAL_CACHED_JAR}" org/kanger/build.properties)"
    ARTIFACT_VERSION="$(printf '%s\n' "${BUILD_PROPERTIES}" | property_value server.version)"
    DISPLAY_BRANCH="$(printf '%s\n' "${BUILD_PROPERTIES}" | property_value branch)"
    SOURCE_BRANCH="$(printf '%s\n' "${BUILD_PROPERTIES}" | property_value source.branch)"
    BUILD_DATE="$(printf '%s\n' "${BUILD_PROPERTIES}" | property_value date)"

    [[ -n "${ARTIFACT_VERSION}" ]] || fail "Cached JAR has no server.version metadata"
    [[ "${DISPLAY_BRANCH}" == "${ARTIFACT_VERSION}" ]] \
      || fail "Cached JAR public branch/version metadata disagree"
    [[ "${SOURCE_BRANCH}" == "${REF}" ]] \
      || fail "Cached JAR provenance is ${SOURCE_BRANCH}, expected ${REF}"

    JAR_SHA256="${CACHED_JAR_SHA256}"
    OPERATION="receipt recovery (qualified JAR already installed)"
    stage_remote_assets
    verify_remote
    verify_public_api
    write_receipt
    check_public_ui_advisory
    print_receipt
    exit 0
  fi
fi

log "building and qualifying KANGER Server"
mvn -B -ntp \
  -f "${POM}" \
  -Dkanger.build.branch.override="${REF}" \
  clean verify

[[ -f "${JAR_FILE}" ]] || fail "Qualified JAR was not produced: ${JAR_FILE}"
jar tf "${JAR_FILE}" | grep -q '^org/kanger/Kanger.class$' \
  || fail "JAR does not contain org/kanger/Kanger.class"

BUILD_PROPERTIES="$(unzip -p "${JAR_FILE}" org/kanger/build.properties)"
ARTIFACT_VERSION="$(printf '%s\n' "${BUILD_PROPERTIES}" | property_value server.version)"
DISPLAY_BRANCH="$(printf '%s\n' "${BUILD_PROPERTIES}" | property_value branch)"
SOURCE_BRANCH="$(printf '%s\n' "${BUILD_PROPERTIES}" | property_value source.branch)"
BUILD_DATE="$(printf '%s\n' "${BUILD_PROPERTIES}" | property_value date)"

[[ -n "${ARTIFACT_VERSION}" ]] || fail "JAR has no server.version metadata"
[[ "${DISPLAY_BRANCH}" == "${ARTIFACT_VERSION}" ]] \
  || fail "Public branch/version metadata disagree: ${DISPLAY_BRANCH} vs ${ARTIFACT_VERSION}"
[[ "${SOURCE_BRANCH}" == "${REF}" ]] \
  || fail "JAR provenance is ${SOURCE_BRANCH}, expected ${REF}"
case "${ARTIFACT_VERSION}" in
  deployment|first-vps-deploy|*deployment*)
    fail "Operational label leaked into public artifact version: ${ARTIFACT_VERSION}"
    ;;
esac

JAR_SHA256="$(sha256_file "${JAR_FILE}")"
log "artifact     : ${ARTIFACT_VERSION}"
log "build date   : ${BUILD_DATE}"
log "JAR SHA-256  : ${JAR_SHA256}"

stage_remote_assets

if [[ -n "${REMOTE_SHA256}" && "${REMOTE_SHA256}" == "${JAR_SHA256}" && "${FORCE}" != true ]]; then
  OPERATION="receipt adoption (exact JAR already installed)"
  log "remote already contains this exact JAR; skipping restart"
else
  OPERATION="installed"
  scp -P "${SSH_PORT}" "${JAR_FILE}" \
    "${SSH_TARGET}:${REMOTE_DIR}/kanger-server.jar"

  ssh "${SSH_ARGS[@]}" \
    "test \"\$(sha256sum '${REMOTE_DIR}/kanger-server.jar' | awk '{print \$1}')\" = '${JAR_SHA256}'"

  log "installing qualified JAR; install.sh owns rollback"
  ssh -tt "${SSH_ARGS[@]}" \
    "sudo flock -n '${REMOTE_LOCK_PATH}' \
       bash '${REMOTE_DIR}/deploy/install.sh' '${REMOTE_DIR}/kanger-server.jar'"
fi

verify_remote
verify_public_api
write_receipt
check_public_ui_advisory
print_receipt
