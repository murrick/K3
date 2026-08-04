#!/usr/bin/env bash
set -euo pipefail

PROGRAM_NAME="$(basename "$0")"
DEFAULT_REPO_URL="https://github.com/murrick/K3.git"
DEFAULT_REF="develop/server/0.12"
DEFAULT_CHECKOUT_DIR="${XDG_CACHE_HOME:-${HOME}/.cache}/kanger-server-updater/K3"
DEFAULT_SSH_TARGET="murray@94.103.94.41"
DEFAULT_SSH_PORT="4211"
DEFAULT_PUBLIC_API_URL="https://api.kanger.org"
DEFAULT_PUBLIC_UI_URL="https://kanger.org"

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
  --force                Reinstall even when the remote JAR checksum matches
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
  exit "${status}"
}
trap cleanup EXIT

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

for command in git mvn java jar unzip ssh scp curl awk grep sed mktemp; do
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
[[ -f "${POM}" ]] || fail "Server POM not found at ${POM}"
[[ -f "${DEPLOY_DIR}/install.sh" ]] || fail "Installer not found in ${DEPLOY_DIR}"
[[ -f "${DEPLOY_DIR}/verify-installed.sh" ]] || fail "Verifier not found in ${DEPLOY_DIR}"

log "building and qualifying KANGER Server"
mvn -B -ntp \
  -f "${POM}" \
  -Dkanger.build.branch.override="${REF}" \
  clean verify

[[ -f "${JAR_FILE}" ]] || fail "Qualified JAR was not produced: ${JAR_FILE}"
jar tf "${JAR_FILE}" | grep -q '^org/kanger/Kanger.class$' \
  || fail "JAR does not contain org/kanger/Kanger.class"

BUILD_PROPERTIES="$(unzip -p "${JAR_FILE}" org/kanger/build.properties)"
ARTIFACT_VERSION="$(printf '%s\n' "${BUILD_PROPERTIES}" | awk -F= '$1 == "server.version" {print $2; exit}')"
DISPLAY_BRANCH="$(printf '%s\n' "${BUILD_PROPERTIES}" | awk -F= '$1 == "branch" {print $2; exit}')"
SOURCE_BRANCH="$(printf '%s\n' "${BUILD_PROPERTIES}" | awk -F= '$1 == "source.branch" {print $2; exit}')"
BUILD_DATE="$(printf '%s\n' "${BUILD_PROPERTIES}" | awk -F= '$1 == "date" {print $2; exit}')"

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

SSH_ARGS=(-p "${SSH_PORT}" "${SSH_TARGET}")
REMOTE_SHA256="$(ssh "${SSH_ARGS[@]}" \
  "if test -f /opt/kanger-server/kanger-server.jar; then sha256sum /opt/kanger-server/kanger-server.jar | awk '{print \$1}'; fi")"

REMOTE_DIR="/tmp/kanger-update-${SHORT_COMMIT}-$$"
log "preparing remote staging directory ${REMOTE_DIR}"
ssh "${SSH_ARGS[@]}" \
  "rm -rf -- '${REMOTE_DIR}' && mkdir -m 700 -- '${REMOTE_DIR}' '${REMOTE_DIR}/deploy'"
REMOTE_CREATED=true

scp -P "${SSH_PORT}" -r "${DEPLOY_DIR}/." \
  "${SSH_TARGET}:${REMOTE_DIR}/deploy/"

if [[ -n "${REMOTE_SHA256}" && "${REMOTE_SHA256}" == "${JAR_SHA256}" && "${FORCE}" != true ]]; then
  log "remote already contains this exact JAR; skipping restart"
else
  scp -P "${SSH_PORT}" "${JAR_FILE}" \
    "${SSH_TARGET}:${REMOTE_DIR}/kanger-server.jar"

  ssh "${SSH_ARGS[@]}" \
    "test \"\$(sha256sum '${REMOTE_DIR}/kanger-server.jar' | awk '{print \$1}')\" = '${JAR_SHA256}'"

  log "installing qualified JAR; install.sh owns rollback"
  ssh -tt "${SSH_ARGS[@]}" \
    "sudo bash '${REMOTE_DIR}/deploy/install.sh' '${REMOTE_DIR}/kanger-server.jar'"
fi

log "verifying installed service and nginx boundary"
ssh -tt "${SSH_ARGS[@]}" \
  "sudo bash '${REMOTE_DIR}/deploy/verify-installed.sh' && \
   curl --fail --silent --show-error http://127.0.0.1:1964/health | grep -q '\"version\":\"${ARTIFACT_VERSION}\"' && \
   curl --fail --silent --show-error http://127.0.0.1:1964/ready | grep -q '\"version\":\"${ARTIFACT_VERSION}\"'"

if [[ "${PUBLIC_CHECKS}" == true ]]; then
  log "checking public API and UI"
  PUBLIC_HEALTH="$(curl --fail --silent --show-error --max-time 15 \
    "${PUBLIC_API_URL%/}/health")"
  printf '%s\n' "${PUBLIC_HEALTH}" | grep -q '"status":"UP"' \
    || fail "Public /health is not UP"
  printf '%s\n' "${PUBLIC_HEALTH}" | grep -q "\"version\":\"${ARTIFACT_VERSION}\"" \
    || fail "Public /health does not expose ${ARTIFACT_VERSION}"

  READY_STATUS="$(curl --silent --show-error --max-time 15 \
    --output /dev/null --write-out '%{http_code}' \
    "${PUBLIC_API_URL%/}/ready")"
  [[ "${READY_STATUS}" == "403" ]] \
    || fail "Public /ready returned HTTP ${READY_STATUS}, expected 403"

  UI_STATUS="$(curl --silent --show-error --max-time 15 \
    --output /dev/null --write-out '%{http_code}' \
    "${PUBLIC_UI_URL}")"
  [[ "${UI_STATUS}" =~ ^(200|301|302|307|308)$ ]] \
    || fail "Public UI returned HTTP ${UI_STATUS}"
fi

cat <<RECEIPT

KANGER Server update completed.
  artifact : ${ARTIFACT_VERSION}
  source   : ${REF}
  commit   : ${COMMIT}
  SHA-256  : ${JAR_SHA256}
  target   : ${SSH_TARGET}:${SSH_PORT}

Persistent configuration and state were retained by install.sh.
Registration/login continuity should be checked with the existing production user.
RECEIPT
