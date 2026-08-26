#!/usr/bin/env bash
set -Eeuo pipefail
export LC_ALL=C

BUNDLE_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=lib/common.sh
source "${BUNDLE_ROOT}/lib/common.sh"

usage() {
  cat <<USAGE
Usage: sudo ./install.sh DOMAIN [options]

DOMAIN is the Browser UI domain. The API domain defaults to api.<DOMAIN>.
Required prerequisite software is customer-managed: Java, nginx and systemd.
KANGER does not install or upgrade those components and does not obtain,
replace or renew TLS certificates.

Options:
  --api-domain DOMAIN      API domain (default: api.<DOMAIN>)
  --ui-cert PATH           UI TLS certificate chain
  --ui-key PATH            UI TLS private key
  --api-cert PATH          API TLS certificate chain
  --api-key PATH           API TLS private key
  --nginx-config PATH      nginx config destination
  -h, --help               Show this help

By default both UI and API use the customer-managed certificate pair:
  /etc/ssl/kanger/fullchain.pem
  /etc/ssl/kanger/privkey.pem
The certificate must cover both configured domain names. Certificate options
may be used when separate UI/API certificate material is required.
USAGE
}

option_value() {
  local option="$1"
  [[ $# -ge 2 && -n "${2:-}" ]] || fail "${option} requires a value"
  printf '%s\n' "$2"
}

UI_DOMAIN=""
API_DOMAIN=""
UI_CERT=""
UI_KEY=""
API_CERT=""
API_KEY=""

if [[ $# -gt 0 && ( "$1" == "-h" || "$1" == "--help" ) ]]; then
  usage
  exit 0
fi
[[ $# -gt 0 ]] || { usage >&2; fail "DOMAIN is required"; }
[[ "$1" != -* ]] || fail "First argument must be the Browser UI DOMAIN"
UI_DOMAIN="$1"
shift

while [[ $# -gt 0 ]]; do
  case "$1" in
    --api-domain) API_DOMAIN="$(option_value "$@")"; shift 2 ;;
    --ui-cert) UI_CERT="$(option_value "$@")"; shift 2 ;;
    --ui-key) UI_KEY="$(option_value "$@")"; shift 2 ;;
    --api-cert) API_CERT="$(option_value "$@")"; shift 2 ;;
    --api-key) API_KEY="$(option_value "$@")"; shift 2 ;;
    --nginx-config) KANGER_NGINX_FILE="$(option_value "$@")"; shift 2 ;;
    -h|--help) usage; exit 0 ;;
    *) fail "Unknown option: $1" ;;
  esac
done

require_root
validate_bundle_layout
verify_bundle_checksums
check_runtime_prerequisites

validate_domain "${UI_DOMAIN}"
if [[ -z "${API_DOMAIN}" ]]; then
  API_DOMAIN="api.${UI_DOMAIN}"
fi
validate_domain "${API_DOMAIN}"

DEFAULT_TLS_CERT="/etc/ssl/kanger/fullchain.pem"
DEFAULT_TLS_KEY="/etc/ssl/kanger/privkey.pem"
UI_CERT="${UI_CERT:-${DEFAULT_TLS_CERT}}"
UI_KEY="${UI_KEY:-${DEFAULT_TLS_KEY}}"
API_CERT="${API_CERT:-${DEFAULT_TLS_CERT}}"
API_KEY="${API_KEY:-${DEFAULT_TLS_KEY}}"
validate_path_value "${UI_CERT}"
validate_path_value "${UI_KEY}"
validate_path_value "${API_CERT}"
validate_path_value "${API_KEY}"
validate_path_value "${KANGER_NGINX_FILE}"
validate_tls_material

[[ ! -e "${KANGER_CURRENT_LINK}" ]] || fail "KANGER is already installed at ${KANGER_CURRENT_LINK}; use update.sh"
[[ ! -e "${KANGER_INSTANCE_FILE}" ]] || fail "KANGER installation identity already exists: ${KANGER_INSTANCE_FILE}"
[[ ! -e "${KANGER_UNIT_FILE}" ]] || fail "KANGER systemd unit already exists: ${KANGER_UNIT_FILE}"
[[ ! -e "${KANGER_NGINX_FILE}" ]] || fail "nginx destination already exists: ${KANGER_NGINX_FILE}"

RELEASE_DIR="${KANGER_RELEASES_DIR}/${VERSION}"
SERVER_CONFIG_PREEXISTED=false
[[ -f "${KANGER_SERVER_CONFIG}" ]] && SERVER_CONFIG_PREEXISTED=true
cleanup_needed=true
cleanup() {
  local status=$?
  trap - ERR INT TERM EXIT
  if [[ "${cleanup_needed}" == true ]]; then
    set +e
    systemctl stop kanger.service >/dev/null 2>&1
    systemctl disable kanger.service >/dev/null 2>&1
    rm -f -- "${KANGER_CURRENT_LINK}" "${KANGER_UNIT_FILE}" "${KANGER_NGINX_FILE}" "${KANGER_INSTANCE_FILE}"
    systemctl daemon-reload >/dev/null 2>&1
    rm -rf -- "${RELEASE_DIR}" "${RELEASE_DIR}.new.$$"
    if [[ "${SERVER_CONFIG_PREEXISTED}" != true ]]; then
      rm -f -- "${KANGER_SERVER_CONFIG}" "${KANGER_STATE_DIR}/kanger.conf"
    fi
  fi
  exit "${status}"
}
trap cleanup ERR INT TERM EXIT

ensure_service_identity
install_persistent_directories
install_server_config_if_absent
stage_release "${RELEASE_DIR}"
install_systemd_unit
atomic_current_link "${RELEASE_DIR}"
install_nginx_config
systemctl enable kanger.service >/dev/null
systemctl restart kanger.service
if ! wait_for_server; then
  systemctl status kanger.service --no-pager || true
  fail "KANGER failed loopback health/readiness checks"
fi
reload_nginx
write_instance_file

cleanup_needed=false
trap - ERR INT TERM EXIT

echo
echo "KANGER ${VERSION} installation complete"
echo "  UI  : https://${UI_DOMAIN}/"
echo "  API : https://${API_DOMAIN}/"
echo "  home: ${KANGER_CURRENT_LINK} -> ${RELEASE_DIR}"
echo "  admin: sudo ${KANGER_CURRENT_LINK}/bin/kanger-admin create-user"
