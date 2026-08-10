#!/usr/bin/env bash
set -euo pipefail
export LC_ALL=C

if [[ "${EUID}" -ne 0 ]]; then
  echo "Run as root: sudo bash $0" >&2
  exit 1
fi

SERVICE="${KANGER_SERVICE:-kanger-server.service}"
HEALTH_URL="${KANGER_HEALTH_URL:-http://127.0.0.1:1964/health}"
READY_URL="${KANGER_READY_URL:-http://127.0.0.1:1964/ready}"
EDITABLE_UI_DIR="${KANGER_EDITABLE_UI_DIR:-/home/murray/sites/kanger}"
PUBLIC_UI_LINK="${KANGER_PUBLIC_UI_LINK:-/var/www/html/kanger}"
EXPECTED_PUBLIC_UI_TARGET="${KANGER_EXPECTED_PUBLIC_UI_TARGET:-/home/murray/sites/kanger-3.7.0.6-vps-soak-20260810T163710Z}"
EXPECTED_SERVER_VERSION="${KANGER_EXPECTED_CURRENT_SERVER_VERSION:-server-0.18}"
EXPECTED_JAR_SHA256="${KANGER_EXPECTED_CURRENT_JAR_SHA256:-afb72c6569e3496be972cd56ce974998132cf49586948669c8b4e2b8c634d0fe}"
SNAPSHOT_ROOT="${KANGER_SNAPSHOT_ROOT:-/root/kanger-snapshots}"
STAMP="$(date -u +%Y%m%dT%H%M%SZ)"
NAME="kanger-vps-before-3.7.0.7-${STAMP}"
WORK_DIR="${SNAPSHOT_ROOT}/${NAME}"
ARCHIVE="${SNAPSHOT_ROOT}/${NAME}.tar.gz"
PAYLOAD="${WORK_DIR}/host-root.tar"
EVIDENCE="${WORK_DIR}/evidence"
PATH_LIST="${WORK_DIR}/payload-paths.txt"

for command in systemctl curl sha256sum tar readlink find sort ss nginx java awk xargs; do
  command -v "${command}" >/dev/null 2>&1 || {
    echo "Required command not found: ${command}" >&2
    exit 1
  }
done

umask 077
mkdir -p "${EVIDENCE}"

service_was_active=false
service_stopped=false
if systemctl is-active --quiet "${SERVICE}"; then
  service_was_active=true
fi

recover_service() {
  if [[ "${service_was_active}" = true && "${service_stopped}" = true ]]; then
    systemctl start "${SERVICE}" || true
  fi
}
trap recover_service EXIT

capture_http() {
  local url="$1"
  local output="$2"
  curl --fail --silent --show-error --max-time 5 "${url}" | tee "${output}"
}

health_before="$(capture_http "${HEALTH_URL}" "${EVIDENCE}/health-before.json")"
ready_before="$(capture_http "${READY_URL}" "${EVIDENCE}/ready-before.json")"
echo "${health_before}" | grep -q "\"server_version\":\"${EXPECTED_SERVER_VERSION}\""
echo "${ready_before}" | grep -q "\"server_version\":\"${EXPECTED_SERVER_VERSION}\""

CURRENT_JAR="/opt/kanger-server/kanger-server.jar"
[[ -f "${CURRENT_JAR}" ]] || {
  echo "Current JAR not found: ${CURRENT_JAR}" >&2
  exit 1
}
current_jar_sha="$(sha256sum "${CURRENT_JAR}" | awk '{print $1}')"
[[ "${current_jar_sha}" = "${EXPECTED_JAR_SHA256}" ]] || {
  echo "Unexpected current JAR SHA-256: ${current_jar_sha}" >&2
  exit 1
}
printf '%s  %s\n' "${current_jar_sha}" "${CURRENT_JAR}" > "${EVIDENCE}/current-jar.sha256"

[[ -d "${EDITABLE_UI_DIR}" && ! -L "${EDITABLE_UI_DIR}" ]] || {
  echo "Expected editable UI directory not found: ${EDITABLE_UI_DIR}" >&2
  exit 1
}
[[ -L "${PUBLIC_UI_LINK}" ]] || {
  echo "Expected public UI symlink not found: ${PUBLIC_UI_LINK}" >&2
  exit 1
}
public_ui_target="$(readlink -f "${PUBLIC_UI_LINK}")"
[[ "${public_ui_target}" = "${EXPECTED_PUBLIC_UI_TARGET}" ]] || {
  echo "Unexpected public UI target: ${public_ui_target}" >&2
  exit 1
}
[[ -d "${public_ui_target}" && -f "${public_ui_target}/index.html" ]] || {
  echo "Published UI target is incomplete: ${public_ui_target}" >&2
  exit 1
}

printf '%s\n' "${PUBLIC_UI_LINK} -> $(readlink "${PUBLIC_UI_LINK}")" > "${EVIDENCE}/public-ui-link.txt"
printf '%s\n' "${public_ui_target}" > "${EVIDENCE}/public-ui-target.txt"
find "${EDITABLE_UI_DIR}" -type f -print0 | sort -z | xargs -0 -r sha256sum > "${EVIDENCE}/editable-ui-files.sha256"
find "${EDITABLE_UI_DIR}" -printf '%M %u %g %p\n' | sort > "${EVIDENCE}/editable-ui-tree.txt"
find "${public_ui_target}" -type f -print0 | sort -z | xargs -0 -r sha256sum > "${EVIDENCE}/published-ui-files.sha256"
find "${public_ui_target}" -printf '%M %u %g %p\n' | sort > "${EVIDENCE}/published-ui-tree.txt"

systemctl --no-pager --full status "${SERVICE}" > "${EVIDENCE}/systemd-status-before.txt"
systemctl cat "${SERVICE}" > "${EVIDENCE}/systemd-unit.txt"
ss -ltnp > "${EVIDENCE}/listeners-before.txt"
nginx -T > "${EVIDENCE}/nginx-effective.conf" 2>&1
java -version > "${EVIDENCE}/java-version.txt" 2>&1

{
  echo "etc/kanger-server"
  echo "var/lib/kanger-server"
  echo "opt/kanger-server"
  echo "etc/systemd/system/kanger-server.service"
  echo "etc/nginx"
  echo "${EDITABLE_UI_DIR#/}"
  echo "${public_ui_target#/}"
  echo "${PUBLIC_UI_LINK#/}"
} | sort -u > "${PATH_LIST}"

while IFS= read -r path; do
  [[ -e "/${path}" || -L "/${path}" ]] || {
    echo "Snapshot path does not exist: /${path}" >&2
    exit 1
  }
done < "${PATH_LIST}"

systemctl stop "${SERVICE}"
service_stopped=true
for attempt in $(seq 1 30); do
  if ! systemctl is-active --quiet "${SERVICE}"; then
    break
  fi
  sleep 1
done
! systemctl is-active --quiet "${SERVICE}"

if [[ -e /var/lib/kanger-server/KANGER/kanger.active ]]; then
  echo "Active marker remained after shutdown" >&2
  exit 1
fi

tar --numeric-owner --acls --xattrs -C / -cf "${PAYLOAD}" -T "${PATH_LIST}"
sha256sum "${PAYLOAD}" > "${EVIDENCE}/host-root.tar.sha256"
tar -tf "${PAYLOAD}" > "${EVIDENCE}/host-root.tar.list"

systemctl start "${SERVICE}"
service_stopped=false
for attempt in $(seq 1 30); do
  if curl --fail --silent --max-time 2 "${HEALTH_URL}" >/dev/null 2>&1 && curl --fail --silent --max-time 2 "${READY_URL}" >/dev/null 2>&1; then
    break
  fi
  sleep 1
done

health_after="$(capture_http "${HEALTH_URL}" "${EVIDENCE}/health-after.json")"
ready_after="$(capture_http "${READY_URL}" "${EVIDENCE}/ready-after.json")"
echo "${health_after}" | grep -q "\"server_version\":\"${EXPECTED_SERVER_VERSION}\""
echo "${ready_after}" | grep -q "\"server_version\":\"${EXPECTED_SERVER_VERSION}\""
systemctl --no-pager --full status "${SERVICE}" > "${EVIDENCE}/systemd-status-after.txt"
ss -ltnp > "${EVIDENCE}/listeners-after.txt"

cat > "${WORK_DIR}/SNAPSHOT.txt" <<EOF
schema=5
created_utc=${STAMP}
host=$(hostname -f 2>/dev/null || hostname)
service=${SERVICE}
expected_server_version=${EXPECTED_SERVER_VERSION}
current_jar_sha256=${current_jar_sha}
editable_ui_directory=${EDITABLE_UI_DIR}
public_ui_link=${PUBLIC_UI_LINK}
public_ui_target=${public_ui_target}
ui_publication_mode=versioned-directory-atomic-symlink
payload=host-root.tar
contains_secrets=true
prior_development_soak=3.7.0.6
accepted_production_release=release/3.5.2
purpose=pre-3.7.0.7-development-soak rollback and disaster recovery
EOF

tar -C "${SNAPSHOT_ROOT}" -czf "${ARCHIVE}" "${NAME}"
archive_sha="$(sha256sum "${ARCHIVE}" | awk '{print $1}')"
printf '%s  %s\n' "${archive_sha}" "${ARCHIVE}" > "${ARCHIVE}.sha256"
chmod 0600 "${ARCHIVE}" "${ARCHIVE}.sha256"

trap - EXIT

echo "SNAPSHOT_OK"
echo "archive=${ARCHIVE}"
echo "sha256=${archive_sha}"
echo "editable_ui_directory=${EDITABLE_UI_DIR}"
echo "public_ui_link=${PUBLIC_UI_LINK}"
echo "public_ui_target=${public_ui_target}"
echo "contains_secrets=true"
echo "Copy the archive and .sha256 file off-host before deployment."
