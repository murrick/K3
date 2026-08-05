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
[[ -f "${DEPLOYMENT_FILE}" ]] || {
  echo "Deployment record not found: ${DEPLOYMENT_FILE}" >&2
  exit 1
}

field() {
  local key="$1"
  awk -F= -v key="${key}" '$1 == key {sub($1 "=", ""); print; exit}' "${DEPLOYMENT_FILE}"
}

SERVICE="${KANGER_SERVICE:-kanger-server.service}"
HEALTH_URL="${KANGER_HEALTH_URL:-http://127.0.0.1:1964/health}"
READY_URL="${KANGER_READY_URL:-http://127.0.0.1:1964/ready}"
UI_LINK="$(field ui_link)"
PRIOR_UI_TARGET="$(field prior_ui_target)"
PRIOR_JAR_SHA="$(field prior_jar_sha256)"
PRIOR_SERVER_VERSION="$(field prior_server_version)"
CANDIDATE_SERVER_VERSION="$(field server_version)"
CURRENT_JAR="/opt/kanger-server/kanger-server.jar"
PREVIOUS_JAR="/opt/kanger-server/kanger-server.jar.previous"

[[ -n "${UI_LINK}" && -n "${PRIOR_UI_TARGET}" && -n "${PRIOR_JAR_SHA}" ]] || {
  echo "Incomplete deployment record" >&2
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
[[ -d "${PRIOR_UI_TARGET}" ]] || {
  echo "Previous UI target is missing: ${PRIOR_UI_TARGET}" >&2
  exit 1
}

health="$(curl --fail --silent --show-error --max-time 5 "${HEALTH_URL}")"
echo "${health}" | grep -q "\"server_version\":\"${CANDIDATE_SERVER_VERSION}\"" || {
  echo "Current server is not the recorded soak candidate" >&2
  exit 1
}

atomic_link() {
  local target="$1"
  local temporary="${UI_LINK}.rollback.$$"
  rm -f "${temporary}"
  ln -s "${target}" "${temporary}"
  mv -Tf "${temporary}" "${UI_LINK}"
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

atomic_link "${PRIOR_UI_TARGET}"
nginx -t
systemctl reload nginx

origin_index="$(curl --fail --silent --show-error --insecure --max-time 10 \
  --resolve kanger.org:443:127.0.0.1 https://kanger.org/)"
printf '%s\n' "${health_after}" > "${RECORD_DIR}/rollback-health-${STAMP}.json"
printf '%s\n' "${ready_after}" > "${RECORD_DIR}/rollback-ready-${STAMP}.json"
printf '%s\n' "${origin_index}" > "${RECORD_DIR}/rollback-origin-index-${STAMP}.html"
printf 'ROLLED_BACK utc=%s\n' "$(date -u +%Y-%m-%dT%H:%M:%SZ)" > "${RECORD_DIR}/STATUS"

cat > "${RECORD_DIR}/ROLLBACK-${STAMP}.txt" <<EOF
status=ROLLED_BACK
utc=${STAMP}
server_version=${PRIOR_SERVER_VERSION}
jar_sha256=$(sha256sum "${CURRENT_JAR}" | awk '{print $1}')
ui_target=$(readlink -f "${UI_LINK}")
state_restored=false
full_snapshot_untouched=true
EOF

echo "SOAK_ROLLBACK_OK"
echo "server=${PRIOR_SERVER_VERSION}"
echo "ui=$(readlink -f "${UI_LINK}")"
echo "The full state/config snapshot remains available for disaster recovery."
