#!/usr/bin/env bash
set -Eeuo pipefail
export LC_ALL=C

BUNDLE_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=lib/common.sh
source "${BUNDLE_ROOT}/lib/common.sh"

usage() {
  cat <<USAGE
Usage: sudo ./update.sh [--force]

Update the existing KANGER installation to the release contained in this
bundle. By default, updating an already-active product version is rejected.

Options:
  --force                  Allow an explicit same-version update
  -h, --help               Show this help

A forced same-version update archives an existing canonical release under a
versioned physical snapshot (for example, <version>.force.N) and installs the
new build at the canonical <version> release directory.
USAGE
}

FORCE_UPDATE=false
while [[ $# -gt 0 ]]; do
  case "$1" in
    --force) FORCE_UPDATE=true; shift ;;
    -h|--help) usage; exit 0 ;;
    *) fail "Unknown option: $1" ;;
  esac
done

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
[[ -f "${OLD_RELEASE}/RELEASE" ]] || fail "Current KANGER release identity is missing: ${OLD_RELEASE}/RELEASE"
OLD_VERSION="$(awk -F= '$1 == "version" {sub(/^[^=]*=/, ""); print; exit}' "${OLD_RELEASE}/RELEASE")"
[[ -n "${OLD_VERSION}" ]] || fail "Current KANGER release has no version: ${OLD_RELEASE}/RELEASE"

CANONICAL_RELEASE="${KANGER_RELEASES_DIR}/${VERSION}"
SAME_VERSION=false
CANONICAL_PREEXISTED=false
ARCHIVE_RELEASE=""
ARCHIVE_READY=false
CANDIDATE_RELEASE=""
NEW_RELEASE="${CANONICAL_RELEASE}"

if [[ "${OLD_VERSION}" == "${VERSION}" ]]; then
  [[ "${FORCE_UPDATE}" == true ]] || fail "KANGER ${VERSION} is already active; use --force for an explicit same-version update"
  SAME_VERSION=true

  if [[ -e "${CANONICAL_RELEASE}" ]]; then
    [[ -d "${CANONICAL_RELEASE}" ]] || fail "Canonical release path is not a directory: ${CANONICAL_RELEASE}"
    [[ -f "${CANONICAL_RELEASE}/RELEASE" ]] || fail "Canonical release identity is missing: ${CANONICAL_RELEASE}/RELEASE"
    CANONICAL_PREEXISTED=true
    force_index=1
    while :; do
      ARCHIVE_RELEASE="${KANGER_RELEASES_DIR}/${VERSION}.force.${force_index}"
      [[ -e "${ARCHIVE_RELEASE}" ]] || break
      ((force_index += 1))
    done
  fi

  CANDIDATE_RELEASE="${KANGER_RELEASES_DIR}/.${VERSION}.candidate.$$"
  [[ ! -e "${CANDIDATE_RELEASE}" ]] || fail "Candidate release path already exists: ${CANDIDATE_RELEASE}"
  log "forcing same-version update ${VERSION}; canonical active release will remain ${CANONICAL_RELEASE}"
else
  [[ ! -e "${NEW_RELEASE}" ]] || fail "Release directory already exists: ${NEW_RELEASE}"
fi

UNIT_BACKUP="$(mktemp "${TMPDIR:-/tmp}/kanger-unit.XXXXXX")"
NGINX_BACKUP="$(mktemp "${TMPDIR:-/tmp}/kanger-nginx.XXXXXX")"
cp "${KANGER_UNIT_FILE}" "${UNIT_BACKUP}"
cp "${KANGER_NGINX_FILE}" "${NGINX_BACKUP}"

rollback_needed=true
rollback() {
  local status=$?
  trap - ERR INT TERM EXIT
  if [[ "${rollback_needed}" == true ]]; then
    set +e
    log "update failed; restoring previous release ${OLD_RELEASE}"

    if [[ "${SAME_VERSION}" == true ]]; then
      if [[ "${OLD_RELEASE}" == "${CANONICAL_RELEASE}" ]]; then
        if [[ "${ARCHIVE_READY}" == true && -d "${ARCHIVE_RELEASE}" ]]; then
          # Keep current on a complete old snapshot while canonical is rebuilt.
          atomic_current_link "${ARCHIVE_RELEASE}"
          rm -rf -- "${CANONICAL_RELEASE}"
          if cp -a "${ARCHIVE_RELEASE}" "${CANONICAL_RELEASE}"; then
            atomic_current_link "${CANONICAL_RELEASE}"
            rm -rf -- "${ARCHIVE_RELEASE}"
            ARCHIVE_READY=false
          fi
        else
          atomic_current_link "${OLD_RELEASE}"
        fi
      else
        # A legacy .force.N (or other same-version physical release) can remain
        # the rollback target while the canonical name is reconstructed.
        atomic_current_link "${OLD_RELEASE}"
        if [[ "${CANONICAL_PREEXISTED}" == true && "${ARCHIVE_READY}" == true \
            && -d "${ARCHIVE_RELEASE}" ]]; then
          rm -rf -- "${CANONICAL_RELEASE}"
          if cp -a "${ARCHIVE_RELEASE}" "${CANONICAL_RELEASE}"; then
            rm -rf -- "${ARCHIVE_RELEASE}"
            ARCHIVE_READY=false
          fi
        elif [[ "${CANONICAL_PREEXISTED}" != true ]]; then
          rm -rf -- "${CANONICAL_RELEASE}"
        fi
      fi
      [[ -z "${CANDIDATE_RELEASE}" ]] || rm -rf -- "${CANDIDATE_RELEASE}" "${CANDIDATE_RELEASE}.new.$$"
      if [[ "${ARCHIVE_READY}" != true && -n "${ARCHIVE_RELEASE}" && -e "${ARCHIVE_RELEASE}" ]]; then
        rm -rf -- "${ARCHIVE_RELEASE}"
      fi
    else
      # Preserve the original atomic rollback invariant: repoint current before
      # deleting the failed new release.
      atomic_current_link "${OLD_RELEASE}"
      rm -rf -- "${NEW_RELEASE}"
    fi

    install -o root -g root -m 0644 "${UNIT_BACKUP}" "${KANGER_UNIT_FILE}"
    install -o root -g root -m 0644 "${NGINX_BACKUP}" "${KANGER_NGINX_FILE}"
    systemctl daemon-reload >/dev/null 2>&1
    systemctl restart kanger.service >/dev/null 2>&1
    nginx -t >/dev/null 2>&1 && nginx -s reload >/dev/null 2>&1
  fi
  rm -f -- "${UNIT_BACKUP}" "${NGINX_BACKUP}"
  exit "${status}"
}
trap rollback ERR INT TERM EXIT

if [[ "${SAME_VERSION}" == true ]]; then
  stage_release "${CANDIDATE_RELEASE}"

  if [[ "${CANONICAL_PREEXISTED}" == true ]]; then
    # Snapshot first. If canonical is also active, current can then move to the
    # complete snapshot atomically before the canonical physical name is freed.
    cp -a "${CANONICAL_RELEASE}" "${ARCHIVE_RELEASE}"
    ARCHIVE_READY=true
    log "archived previous canonical release as ${ARCHIVE_RELEASE}"
    if [[ "${OLD_RELEASE}" == "${CANONICAL_RELEASE}" ]]; then
      atomic_current_link "${ARCHIVE_RELEASE}"
    fi
    rm -rf -- "${CANONICAL_RELEASE}"
  fi

  mv "${CANDIDATE_RELEASE}" "${CANONICAL_RELEASE}"
  CANDIDATE_RELEASE=""
else
  stage_release "${NEW_RELEASE}"
fi

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
if [[ -n "${ARCHIVE_RELEASE}" ]]; then
  echo "  archived: ${ARCHIVE_RELEASE}"
fi
echo "  current : ${KANGER_CURRENT_LINK} -> ${NEW_RELEASE}"
echo "  version : ${VERSION}"
