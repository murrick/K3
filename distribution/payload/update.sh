#!/usr/bin/env bash
set -Eeuo pipefail
export LC_ALL=C

BUNDLE_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=lib/common.sh
source "${BUNDLE_ROOT}/lib/common.sh"

require_root
validate_bundle_layout
verify_bundle_checksums
check_runtime_prerequisites

[[ -L "${KANGER_CURRENT_LINK}" ]] || fail "No KANGER installation at ${KANGER_CURRENT_LINK}; use install.sh"
[[ -f "${KANGER_UNIT_FILE}" ]] || fail "KANGER systemd unit is missing: ${KANGER_UNIT_FILE}"
load_instance_file
validate_tls_material

OLD_RELEASE="$(readlink -f "${KANGER_CURRENT_LINK}")"
[[ -d "${OLD_RELEASE}" ]] || fail "Current KANGER release is invalid: ${OLD_RELEASE}"
NEW_RELEASE="${KANGER_RELEASES_DIR}/${VERSION}"
[[ "${OLD_RELEASE}" != "${NEW_RELEASE}" ]] || fail "KANGER ${VERSION} is already active"
[[ ! -e "${NEW_RELEASE}" ]] || fail "Release directory already exists: ${NEW_RELEASE}"

UNIT_BACKUP="$(mktemp "${TMPDIR:-/tmp}/kanger-unit.XXXXXX")"
NGINX_BACKUP="$(mktemp "${TMPDIR:-/tmp}/kanger-nginx.XXXXXX")"
cp "${KANGER_UNIT_FILE}" "${UNIT_BACKUP}"
cp "${KANGER_NGINX_FILE}" "${NGINX_BACKUP}"

stage_release "${NEW_RELEASE}"

rollback_needed=true
rollback() {
  local status=$?
  trap - ERR INT TERM EXIT
  if [[ "${rollback_needed}" == true ]]; then
    set +e
    log "update failed; restoring previous release ${OLD_RELEASE}"
    atomic_current_link "${OLD_RELEASE}"
    install -o root -g root -m 0644 "${UNIT_BACKUP}" "${KANGER_UNIT_FILE}"
    install -o root -g root -m 0644 "${NGINX_BACKUP}" "${KANGER_NGINX_FILE}"
    systemctl daemon-reload >/dev/null 2>&1
    systemctl restart kanger.service >/dev/null 2>&1
    nginx -t >/dev/null 2>&1 && nginx -s reload >/dev/null 2>&1
    rm -rf -- "${NEW_RELEASE}"
  fi
  rm -f -- "${UNIT_BACKUP}" "${NGINX_BACKUP}"
  exit "${status}"
}
trap rollback ERR INT TERM EXIT

install_systemd_unit
install_nginx_config
atomic_current_link "${NEW_RELEASE}"
systemctl restart kanger.service
if ! wait_for_server; then
  systemctl status kanger.service --no-pager || true
  fail "KANGER ${VERSION} failed loopback health/readiness checks"
fi
reload_nginx

rollback_needed=false
trap - ERR INT TERM EXIT
rm -f -- "${UNIT_BACKUP}" "${NGINX_BACKUP}"

echo
echo "KANGER update complete"
echo "  previous: ${OLD_RELEASE}"
echo "  current : ${KANGER_CURRENT_LINK} -> ${NEW_RELEASE}"
echo "  version : ${VERSION}"
