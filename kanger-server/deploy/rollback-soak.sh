#!/usr/bin/env bash
set -euo pipefail

[[ "${EUID}" -eq 0 ]] || {
  echo "Run as root" >&2
  exit 1
}
[[ $# -eq 1 ]] || {
  echo "Usage: sudo bash $0 DEPLOYMENT_RECORD_DIR" >&2
  exit 1
}

RECORD_DIR="$(readlink -f "$1")"
DEPLOYMENT_FILE="${RECORD_DIR}/DEPLOYMENT.txt"
MANAGED_UI_FILES="${RECORD_DIR}/managed-ui-files.txt"
[[ -f "${DEPLOYMENT_FILE}" ]] || {
  echo "Deployment record not found: ${DEPLOYMENT_FILE}" >&2
  exit 1
}
[[ -f "${MANAGED_UI_FILES}" ]] || {
  echo "Managed UI inventory not found: ${MANAGED_UI_FILES}" >&2
  exit 1
}

field() {
  local key="$1"
  awk -F= -v key="${key}" '$1 == key {sub($1 "=", ""); print; exit}' "${DEPLOYMENT_FILE}"
}

SERVICE="${KANGER_SERVICE:-kanger-server.service}"
HEALTH_URL="${KANGER_HEALTH_URL:-http://127.0.0.1:1964/health}"
READY_URL="${KANGER_READY_URL:-http://127.0.0.1:1964/ready}"
UI_DIR="$(field ui_directory)"
PUBLIC_UI_LINK="$(field public_ui_link)"
PRIOR_UI_BACKUP="$(field prior_ui_backup)"
PRIOR_JAR_SHA="$(field prior_jar_sha256)"
PRIOR_SERVER_VERSION="$(field prior_server_version)"
CANDIDATE_SERVER_VERSION="$(field server_version)"
CURRENT_JAR="/opt/kanger-server/kanger-server.jar"
PREVIOUS_JAR="/opt/kanger-server/kanger-server.jar.previous"

[[ -n "${UI_DIR}" && -n "${PUBLIC_UI_LINK}" && -n "${PRIOR_UI_BACKUP}" && -n "${PRIOR_JAR_SHA}" ]] || {
  echo "Incomplete deployment record" >&2
  exit 1
}
[[ -d "${UI_DIR}" && ! -L "${UI_DIR}" ]] || {
  echo "Editable UI directory is missing: ${UI_DIR}" >&2
  exit 1
}
[[ -L "${PUBLIC_UI_LINK}" && "$(readlink -f "${PUBLIC_UI_LINK}")" = "${UI_DIR}" ]] || {
  echo "Public UI symlink no longer resolves to ${UI_DIR}" >&2
  exit 1
}
[[ -d "${PRIOR_UI_BACKUP}" ]] || {
  echo "Previous UI backup is missing: ${PRIOR_UI_BACKUP}" >&2
  exit 1
}
[[ -f "${PREVIOUS_JAR}" ]] || {
  echo "Previous JAR is missing: ${PREVIOUS_JAR}" >&2
  exit 1
}
[[ "$(sha256sum "${PREVIOUS_JAR}" | awk '{print $1}')" = "${PRIOR_JAR_SHA}" ]] || {
  echo "Previous JAR no longer matches deployment record" >&2
  exit 1
}

health="$(curl --fail --silent --show-error --max-time 5 "${HEALTH_URL}")"
echo "${health}" | grep -q "\"server_version\":\"${CANDIDATE_SERVER_VERSION}\"" || {
  echo "Current server is not the recorded soak candidate" >&2
  exit 1
}

restore_prior_ui() {
  local file
  while IFS= read -r file; do
    [[ -n "${file}" ]] || continue
    rm -f "${UI_DIR}/${file}"
  done < "${MANAGED_UI_FILES}"
  cp -a "${PRIOR_UI_BACKUP}/." "${UI_DIR}/"
}

STAMP="$(date -u +%Y%m%dT%H%M%SZ)"
systemctl stop "${SERVICE}"
cp -f "${PREVIOUS_JAR}" "${CURRENT_JAR}"
chown root:kanger "${CURRENT_JAR}"
chmod 0640 "${CURRENT_JAR}"
systemctl start "${SERVICE}"

for attempt in $(seq 1 30); do
  if curl --fail --silent --max-time 2 "${HEALTH_URL}" >/dev/null 2>&1 \
      && curl --fail --silent --max-time 2 "${READY_URL}" >/dev/null 2>&1; then
    break
  fi
  sleep 1
done

health_after="$(curl --fail --silent --show-error --max-time 5 "${HEALTH_URL}")"
ready_after="$(curl --fail --silent --show-error --max-time 5 "${READY_URL}")"
echo "${health_after}" | grep -q "\"server_version\":\"${PRIOR_SERVER_VERSION}\""
echo "${ready_after}" | grep -q "\"server_version\":\"${PRIOR_SERVER_VERSION}\""

restore_prior_ui
nginx -t
systemctl reload nginx

[[ "$(readlink -f "${PUBLIC_UI_LINK}")" = "${UI_DIR}" ]]
origin_index="$(curl --fail --silent --show-error --insecure --max-time 10 \
  --resolve kanger.org:443:127.0.0.1 https://kanger.org/)"
printf '%s\n' "${health_after}" > "${RECORD_DIR}/rollback-health-${STAMP}.json"
printf '%s\n' "${ready_after}" > "${RECORD_DIR}/rollback-ready-${STAMP}.json"
printf '%s\n' "${origin_index}" > "${RECORD_DIR}/rollback-origin-index-${STAMP}.html"
find "${UI_DIR}" -type f -print0 \
  | sort -z \
  | xargs -0 -r sha256sum > "${RECORD_DIR}/rollback-ui-${STAMP}.sha256"
printf 'ROLLED_BACK utc=%s\n' "$(date -u +%Y-%m-%dT%H:%M:%SZ)" > "${RECORD_DIR}/STATUS"

cat > "${RECORD_DIR}/ROLLBACK-${STAMP}.txt" <<EOF
status=ROLLED_BACK
utc=${STAMP}
server_version=${PRIOR_SERVER_VERSION}
jar_sha256=$(sha256sum "${CURRENT_JAR}" | awk '{print $1}')
ui_directory=${UI_DIR}
public_ui_link=${PUBLIC_UI_LINK}
public_ui_target=$(readlink -f "${PUBLIC_UI_LINK}")
ui_update_mode=managed-file-overlay
unmanaged_ui_files_preserved=true
state_restored=false
full_snapshot_untouched=true
EOF

echo "SOAK_ROLLBACK_OK"
echo "server=${PRIOR_SERVER_VERSION}"
echo "ui_directory=${UI_DIR}"
echo "public_ui_link=${PUBLIC_UI_LINK}"
echo "The full state/config snapshot remains available for disaster recovery."
