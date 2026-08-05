#!/usr/bin/env bash
set -euo pipefail

usage() {
  echo "Usage: sudo bash $0 BUNDLE_DIR SNAPSHOT_ARCHIVE OFFHOST_RECEIPT" >&2
  exit 1
}

[[ "${EUID}" -eq 0 ]] || {
  echo "Run as root" >&2
  exit 1
}
[[ $# -eq 3 ]] || usage

BUNDLE_DIR="$(readlink -f "$1")"
SNAPSHOT_ARCHIVE="$(readlink -f "$2")"
OFFHOST_RECEIPT="$(readlink -f "$3")"
SERVICE="${KANGER_SERVICE:-kanger-server.service}"
UI_DIR="${KANGER_UI_DIR:-/home/murray/sites/kanger}"
PUBLIC_UI_LINK="${KANGER_PUBLIC_UI_LINK:-/var/www/html/kanger}"
SITE_ROOT="${KANGER_SITE_ROOT:-/home/murray/sites}"
HEALTH_URL="${KANGER_HEALTH_URL:-http://127.0.0.1:1964/health}"
READY_URL="${KANGER_READY_URL:-http://127.0.0.1:1964/ready}"
PUBLIC_HEALTH_URL="${KANGER_PUBLIC_HEALTH_URL:-https://api.kanger.org/health}"
EXPECTED_CURRENT_SERVER="${KANGER_EXPECTED_CURRENT_SERVER_VERSION:-server-0.14}"
EXPECTED_CANDIDATE_SERVER="${KANGER_EXPECTED_CANDIDATE_SERVER_VERSION:-server-0.17}"
EXPECTED_SOURCE_HEAD="7946d3969302aa198fea506f419a885565db118a"
STAMP="$(date -u +%Y%m%dT%H%M%SZ)"
RECORD_DIR="${KANGER_DEPLOYMENT_RECORD_ROOT:-/root/kanger-deployments}/3.5.2-soak-${STAMP}"
CANDIDATE_UI="${SITE_ROOT}/kanger-server-0.17-soak-${STAMP}"
PRIOR_UI_BACKUP="${SITE_ROOT}/kanger-server-0.14-before-3.5.2-${STAMP}"
CURRENT_JAR="/opt/kanger-server/kanger-server.jar"
PREVIOUS_JAR="/opt/kanger-server/kanger-server.jar.previous"

for command in systemctl curl sha256sum readlink install cp mv nginx grep awk find sort; do
  command -v "${command}" >/dev/null 2>&1 || {
    echo "Required command not found: ${command}" >&2
    exit 1
  }
done

[[ -d "${BUNDLE_DIR}" ]] || usage
[[ -f "${SNAPSHOT_ARCHIVE}" ]] || usage
[[ -f "${OFFHOST_RECEIPT}" ]] || usage
[[ -f "${BUNDLE_DIR}/kanger-server.jar" ]]
[[ -d "${BUNDLE_DIR}/html" ]]
[[ -x "${BUNDLE_DIR}/deploy/install.sh" || -f "${BUNDLE_DIR}/deploy/install.sh" ]]
[[ -f "${BUNDLE_DIR}/deploy/verify-installed.sh" ]]
[[ -f "${BUNDLE_DIR}/SOURCE.txt" ]]
[[ -f "${BUNDLE_DIR}/SHA256SUMS" ]]
[[ -d "${UI_DIR}" && ! -L "${UI_DIR}" ]] || {
  echo "Expected editable UI directory not found: ${UI_DIR}" >&2
  exit 1
}
[[ -L "${PUBLIC_UI_LINK}" ]] || {
  echo "Expected public UI symlink not found: ${PUBLIC_UI_LINK}" >&2
  exit 1
}
[[ "$(readlink -f "${PUBLIC_UI_LINK}")" = "${UI_DIR}" ]] || {
  echo "Public UI symlink does not resolve to ${UI_DIR}" >&2
  exit 1
}

receipt_archive="$(awk -F= '$1 == "archive_basename" {print $2}' "${OFFHOST_RECEIPT}")"
receipt_sha="$(awk -F= '$1 == "sha256" {print $2}' "${OFFHOST_RECEIPT}")"
[[ -n "${receipt_archive}" && -n "${receipt_sha}" ]] || {
  echo "Invalid off-host receipt" >&2
  exit 1
}
[[ "${receipt_archive}" = "$(basename "${SNAPSHOT_ARCHIVE}")" ]] || {
  echo "Receipt names another archive" >&2
  exit 1
}
actual_snapshot_sha="$(sha256sum "${SNAPSHOT_ARCHIVE}" | awk '{print $1}')"
[[ "${actual_snapshot_sha}" = "${receipt_sha}" ]] || {
  echo "Snapshot SHA-256 does not match off-host receipt" >&2
  exit 1
}

grep -q "^canonical_source_head=${EXPECTED_SOURCE_HEAD}$" "${BUNDLE_DIR}/SOURCE.txt"
grep -q '^server_version=server-0.17$' "${BUNDLE_DIR}/SOURCE.txt"
(
  cd "${BUNDLE_DIR}"
  sha256sum -c SHA256SUMS
)

expected_browser_files="$(cat <<'EOF_FILES'
codemirror.css
codemirror.js
config.js
console.html
containment.js
error.js
favicon.ico
gateway.js
index.html
javascript-mode-vendor.js
javascript-mode.js
javascript.js
jquery-3.6.0.min.js
operation.js
workspace.js
EOF_FILES
)"
actual_browser_files="$(find "${BUNDLE_DIR}/html" -mindepth 1 -maxdepth 1 -type f -printf '%f\n' | sort)"
[[ "${actual_browser_files}" = "${expected_browser_files}" ]] || {
  echo "Unexpected browser inventory" >&2
  exit 1
}

health_before="$(curl --fail --silent --show-error --max-time 5 "${HEALTH_URL}")"
ready_before="$(curl --fail --silent --show-error --max-time 5 "${READY_URL}")"
echo "${health_before}" | grep -q "\"server_version\":\"${EXPECTED_CURRENT_SERVER}\""
echo "${ready_before}" | grep -q "\"server_version\":\"${EXPECTED_CURRENT_SERVER}\""
systemctl is-active --quiet "${SERVICE}"
nginx -t

umask 077
mkdir -p "${RECORD_DIR}"
printf '%s\n' "${health_before}" > "${RECORD_DIR}/health-before.json"
printf '%s\n' "${ready_before}" > "${RECORD_DIR}/ready-before.json"
cp -f "${OFFHOST_RECEIPT}" "${RECORD_DIR}/offhost-receipt.txt"
cp -f "${BUNDLE_DIR}/SOURCE.txt" "${RECORD_DIR}/SOURCE.txt"
cp -f "${BUNDLE_DIR}/SHA256SUMS" "${RECORD_DIR}/SHA256SUMS"
printf '%s\n' "${expected_browser_files}" > "${RECORD_DIR}/managed-ui-files.txt"

prior_public_ui_target="$(readlink -f "${PUBLIC_UI_LINK}")"
prior_jar_sha="$(sha256sum "${CURRENT_JAR}" | awk '{print $1}')"
candidate_jar_sha="$(sha256sum "${BUNDLE_DIR}/kanger-server.jar" | awk '{print $1}')"
printf '%s\n' "${prior_public_ui_target}" > "${RECORD_DIR}/prior-public-ui-target.txt"
printf '%s\n' "${prior_jar_sha}" > "${RECORD_DIR}/prior-jar.sha256"
printf '%s\n' "${candidate_jar_sha}" > "${RECORD_DIR}/candidate-jar.sha256"
printf '%s\n' "${SNAPSHOT_ARCHIVE}" > "${RECORD_DIR}/snapshot-archive.txt"
find "${UI_DIR}" -type f -print0 \
  | sort -z \
  | xargs -0 -r sha256sum > "${RECORD_DIR}/prior-ui.sha256"

install -d -o murray -g www-data -m 0755 "${PRIOR_UI_BACKUP}"
cp -a "${UI_DIR}/." "${PRIOR_UI_BACKUP}/"
install -d -o murray -g www-data -m 0755 "${CANDIDATE_UI}"
while IFS= read -r file; do
  [[ -n "${file}" ]] || continue
  install -o murray -g www-data -m 0644 \
    "${BUNDLE_DIR}/html/${file}" "${CANDIDATE_UI}/${file}"
done <<< "${expected_browser_files}"
find "${CANDIDATE_UI}" -maxdepth 1 -type f -print0 \
  | sort -z \
  | xargs -0 -r sha256sum > "${RECORD_DIR}/candidate-ui.sha256"

server_installed=false
ui_updated=false
deployment_complete=false

restore_prior_ui() {
  while IFS= read -r file; do
    [[ -n "${file}" ]] || continue
    rm -f "${UI_DIR}/${file}"
  done < "${RECORD_DIR}/managed-ui-files.txt"
  cp -a "${PRIOR_UI_BACKUP}/." "${UI_DIR}/"
}

install_candidate_ui() {
  local file temporary
  while IFS= read -r file; do
    [[ -n "${file}" ]] || continue
    temporary="${UI_DIR}/.${file}.new.$$"
    install -o murray -g www-data -m 0644 \
      "${CANDIDATE_UI}/${file}" "${temporary}"
    mv -f "${temporary}" "${UI_DIR}/${file}"
  done < "${RECORD_DIR}/managed-ui-files.txt"
}

rollback_on_error() {
  local status=$?
  if [[ "${deployment_complete}" = true ]]; then
    return
  fi
  echo "Deployment failed; restoring matched Server 0.14/UI pair" >&2
  set +e
  if [[ "${server_installed}" = true && -f "${PREVIOUS_JAR}" ]]; then
    systemctl stop "${SERVICE}"
    cp -f "${PREVIOUS_JAR}" "${CURRENT_JAR}"
    chown root:kanger "${CURRENT_JAR}"
    chmod 0640 "${CURRENT_JAR}"
    systemctl start "${SERVICE}"
  fi
  if [[ "${ui_updated}" = true ]]; then
    restore_prior_ui
    nginx -t && systemctl reload nginx
  fi
  printf 'FAILED_ROLLED_BACK status=%s utc=%s\n' "${status}" "$(date -u +%Y-%m-%dT%H:%M:%SZ)" \
    > "${RECORD_DIR}/STATUS"
  exit "${status}"
}
trap rollback_on_error EXIT

bash "${BUNDLE_DIR}/deploy/install.sh" "${BUNDLE_DIR}/kanger-server.jar"
server_installed=true
[[ -f "${PREVIOUS_JAR}" ]]
[[ "$(sha256sum "${PREVIOUS_JAR}" | awk '{print $1}')" = "${prior_jar_sha}" ]]
bash "${BUNDLE_DIR}/deploy/verify-installed.sh" \
  | tee "${RECORD_DIR}/verify-installed.txt"

ui_updated=true
install_candidate_ui
nginx -t
systemctl reload nginx

[[ "$(readlink -f "${PUBLIC_UI_LINK}")" = "${UI_DIR}" ]]
origin_index="$(curl --fail --silent --show-error --insecure --max-time 10 \
  --resolve kanger.org:443:127.0.0.1 https://kanger.org/)"
printf '%s\n' "${origin_index}" > "${RECORD_DIR}/origin-index.html"
echo "${origin_index}" | grep -q 'containment.js'
echo "${origin_index}" | grep -q 'sandbox="allow-scripts"'
! echo "${origin_index}" | grep -q 'allow-same-origin'

health_after="$(curl --fail --silent --show-error --max-time 5 "${HEALTH_URL}")"
ready_after="$(curl --fail --silent --show-error --max-time 5 "${READY_URL}")"
echo "${health_after}" | grep -q "\"server_version\":\"${EXPECTED_CANDIDATE_SERVER}\""
echo "${ready_after}" | grep -q "\"server_version\":\"${EXPECTED_CANDIDATE_SERVER}\""
printf '%s\n' "${health_after}" > "${RECORD_DIR}/health-after.json"
printf '%s\n' "${ready_after}" > "${RECORD_DIR}/ready-after.json"
find "${UI_DIR}" -type f -print0 \
  | sort -z \
  | xargs -0 -r sha256sum > "${RECORD_DIR}/active-ui.sha256"

if public_health="$(curl --fail --silent --show-error --max-time 10 "${PUBLIC_HEALTH_URL}" 2>/dev/null)"; then
  printf '%s\n' "${public_health}" > "${RECORD_DIR}/public-health.json"
else
  printf '%s\n' "PUBLIC_HEALTH_UNAVAILABLE" > "${RECORD_DIR}/public-health.json"
fi

cat > "${RECORD_DIR}/DEPLOYMENT.txt" <<EOF
schema=2
status=SOAK_ACTIVE
started_utc=${STAMP}
canonical_source_head=${EXPECTED_SOURCE_HEAD}
server_version=${EXPECTED_CANDIDATE_SERVER}
candidate_jar_sha256=${candidate_jar_sha}
prior_server_version=${EXPECTED_CURRENT_SERVER}
prior_jar_sha256=${prior_jar_sha}
ui_directory=${UI_DIR}
public_ui_link=${PUBLIC_UI_LINK}
public_ui_target=${prior_public_ui_target}
prior_ui_backup=${PRIOR_UI_BACKUP}
candidate_ui_staging=${CANDIDATE_UI}
ui_update_mode=managed-file-overlay
unmanaged_ui_files_preserved=true
snapshot_archive=${SNAPSHOT_ARCHIVE}
snapshot_sha256=${actual_snapshot_sha}
rollback_command=sudo bash ${BUNDLE_DIR}/deploy/rollback-soak.sh ${RECORD_DIR}
EOF
printf 'SOAK_ACTIVE utc=%s\n' "$(date -u +%Y-%m-%dT%H:%M:%SZ)" > "${RECORD_DIR}/STATUS"

deployment_complete=true
trap - EXIT

echo "SOAK_DEPLOYMENT_OK"
echo "record=${RECORD_DIR}"
echo "server=${EXPECTED_CANDIDATE_SERVER}"
echo "ui_directory=${UI_DIR}"
echo "public_ui_link=${PUBLIC_UI_LINK}"
echo "candidate_staging=${CANDIDATE_UI}"
echo "rollback=sudo bash ${BUNDLE_DIR}/deploy/rollback-soak.sh ${RECORD_DIR}"
