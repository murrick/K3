#!/usr/bin/env bash
set -euo pipefail
export LC_ALL=C

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
EDITABLE_UI_DIR="${KANGER_EDITABLE_UI_DIR:-/home/murray/sites/kanger}"
PUBLIC_UI_LINK="${KANGER_PUBLIC_UI_LINK:-/var/www/html/kanger}"
SITE_ROOT="${KANGER_SITE_ROOT:-/home/murray/sites}"
EXPECTED_CURRENT_UI_TARGET="${KANGER_EXPECTED_CURRENT_UI_TARGET:-/home/murray/sites/kanger-3.7.0.5-vps-soak-20260810T125400Z}"
HEALTH_URL="${KANGER_HEALTH_URL:-http://127.0.0.1:1964/health}"
READY_URL="${KANGER_READY_URL:-http://127.0.0.1:1964/ready}"
PUBLIC_HEALTH_URL="${KANGER_PUBLIC_HEALTH_URL:-https://api.kanger.org/health}"
EXPECTED_CURRENT_SERVER="${KANGER_EXPECTED_CURRENT_SERVER_VERSION:-server-0.18}"
EXPECTED_CURRENT_JAR_SHA256="${KANGER_EXPECTED_CURRENT_JAR_SHA256:-5a25dbf5f563f9cd36647ca75566798a39335b076d5b123b8d5e5f3a7501e763}"
EXPECTED_CANDIDATE_SERVER="${KANGER_EXPECTED_CANDIDATE_SERVER_VERSION:-server-0.18}"
EXPECTED_SOURCE_HEAD="62fdb5d56fe5efe5e3cbe3ca8771d4f677644155"
EXPECTED_PACKAGING_BRANCH="ops/3.7.0.6-vps-soak"
STAMP="$(date -u +%Y%m%dT%H%M%SZ)"
RECORD_DIR="${KANGER_DEPLOYMENT_RECORD_ROOT:-/root/kanger-deployments}/3.7.0.6-vps-soak-${STAMP}"
CANDIDATE_UI="${SITE_ROOT}/kanger-3.7.0.6-vps-soak-${STAMP}"
CURRENT_JAR="/opt/kanger-server/kanger-server.jar"
PREVIOUS_JAR="/opt/kanger-server/kanger-server.jar.previous"

for command in systemctl curl sha256sum readlink install cp mv ln rm nginx grep awk find sort basename; do
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
[[ -f "${BUNDLE_DIR}/deploy/install.sh" ]]
[[ -f "${BUNDLE_DIR}/deploy/verify-installed.sh" ]]
[[ -f "${BUNDLE_DIR}/SOURCE.txt" ]]
[[ -f "${BUNDLE_DIR}/SHA256SUMS" ]]
[[ -d "${EDITABLE_UI_DIR}" && ! -L "${EDITABLE_UI_DIR}" ]] || {
  echo "Expected editable UI directory not found: ${EDITABLE_UI_DIR}" >&2
  exit 1
}
[[ -L "${PUBLIC_UI_LINK}" ]] || {
  echo "Expected public UI symlink not found: ${PUBLIC_UI_LINK}" >&2
  exit 1
}
prior_public_ui_target="$(readlink -f "${PUBLIC_UI_LINK}")"
[[ "${prior_public_ui_target}" = "${EXPECTED_CURRENT_UI_TARGET}" ]] || {
  echo "Unexpected current public UI target: ${prior_public_ui_target}" >&2
  exit 1
}
[[ -d "${prior_public_ui_target}" && -f "${prior_public_ui_target}/index.html" ]] || {
  echo "Current published UI target is incomplete: ${prior_public_ui_target}" >&2
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

grep -q '^schema=5$' "${BUNDLE_DIR}/SOURCE.txt"
grep -q '^artifact=3.7.0.6-vps-soak$' "${BUNDLE_DIR}/SOURCE.txt"
grep -q "^canonical_source_head=${EXPECTED_SOURCE_HEAD}$" "${BUNDLE_DIR}/SOURCE.txt"
grep -q "^packaging_branch=${EXPECTED_PACKAGING_BRANCH}$" "${BUNDLE_DIR}/SOURCE.txt"
grep -q '^packaging_generation=dev1$' "${BUNDLE_DIR}/SOURCE.txt"
grep -q '^server_version=server-0.18$' "${BUNDLE_DIR}/SOURCE.txt"
grep -q '^browser_files=22$' "${BUNDLE_DIR}/SOURCE.txt"
grep -q '^prior_development_soak=3.7.0.5$' "${BUNDLE_DIR}/SOURCE.txt"
grep -q "^prior_development_soak_ui_target=${EXPECTED_CURRENT_UI_TARGET}$" "${BUNDLE_DIR}/SOURCE.txt"
grep -q "^prior_development_soak_jar_sha256=${EXPECTED_CURRENT_JAR_SHA256}$" "${BUNDLE_DIR}/SOURCE.txt"
grep -q '^accepted_production_release=release/3.5.2$' "${BUNDLE_DIR}/SOURCE.txt"
grep -q '^ui_publication_mode=versioned-directory-atomic-symlink$' "${BUNDLE_DIR}/SOURCE.txt"
grep -q '^release_acceptance=NOT_PERFORMED$' "${BUNDLE_DIR}/SOURCE.txt"
(
  cd "${BUNDLE_DIR}"
  sha256sum -c SHA256SUMS
)

expected_browser_files="$(cat <<'EOF_FILES'
bottom-layout.js
codemirror.css
codemirror.js
config.js
console.html
containment.js
dialogue.js
editor-local-file.js
error.js
favicon.ico
file-download.js
gateway.js
index.html
javascript-mode-vendor.js
javascript-mode.js
javascript.js
jquery-3.6.0.min.js
layout-persistence.js
operation.js
presentation.css
presentation.js
workspace.js
EOF_FILES
)"
actual_browser_files="$(find "${BUNDLE_DIR}/html" -mindepth 1 -maxdepth 1 -type f -printf '%f\n' | sort)"
[[ "${actual_browser_files}" = "${expected_browser_files}" ]] || {
  echo "Unexpected browser inventory" >&2
  exit 1
}

# Verify the Browser presentation loader chain before any live mutation.
grep -q 'sandbox="allow-scripts"' "${BUNDLE_DIR}/html/index.html"
! grep -q 'allow-same-origin' "${BUNDLE_DIR}/html/index.html"
grep -q 'src="javascript.js"' "${BUNDLE_DIR}/html/console.html"
grep -q 'javascript-mode.js' "${BUNDLE_DIR}/html/javascript.js"
grep -q 'presentation.js' "${BUNDLE_DIR}/html/javascript-mode.js"
grep -q 'presentation.css' "${BUNDLE_DIR}/html/presentation.js"

health_before="$(curl --fail --silent --show-error --max-time 5 "${HEALTH_URL}")"
ready_before="$(curl --fail --silent --show-error --max-time 5 "${READY_URL}")"
echo "${health_before}" | grep -q "\"server_version\":\"${EXPECTED_CURRENT_SERVER}\""
echo "${ready_before}" | grep -q "\"server_version\":\"${EXPECTED_CURRENT_SERVER}\""
systemctl is-active --quiet "${SERVICE}"
current_jar_sha="$(sha256sum "${CURRENT_JAR}" | awk '{print $1}')"
[[ "${current_jar_sha}" = "${EXPECTED_CURRENT_JAR_SHA256}" ]] || {
  echo "Unexpected current JAR SHA-256: ${current_jar_sha}" >&2
  exit 1
}
candidate_jar_sha="$(sha256sum "${BUNDLE_DIR}/kanger-server.jar" | awk '{print $1}')"
[[ "${candidate_jar_sha}" != "${current_jar_sha}" ]] || {
  echo "Candidate JAR is byte-identical to current development soak; refusing ambiguous deployment" >&2
  exit 1
}
nginx -t

umask 077
mkdir -p "${RECORD_DIR}"
printf '%s\n' "${health_before}" > "${RECORD_DIR}/health-before.json"
printf '%s\n' "${ready_before}" > "${RECORD_DIR}/ready-before.json"
cp -f "${OFFHOST_RECEIPT}" "${RECORD_DIR}/offhost-receipt.txt"
cp -f "${BUNDLE_DIR}/SOURCE.txt" "${RECORD_DIR}/SOURCE.txt"
cp -f "${BUNDLE_DIR}/SHA256SUMS" "${RECORD_DIR}/SHA256SUMS"
printf '%s\n' "${expected_browser_files}" > "${RECORD_DIR}/managed-ui-files.txt"
printf '%s\n' "${prior_public_ui_target}" > "${RECORD_DIR}/prior-public-ui-target.txt"
printf '%s\n' "${current_jar_sha}" > "${RECORD_DIR}/prior-jar.sha256"
printf '%s\n' "${candidate_jar_sha}" > "${RECORD_DIR}/candidate-jar.sha256"
printf '%s\n' "${SNAPSHOT_ARCHIVE}" > "${RECORD_DIR}/snapshot-archive.txt"
find "${prior_public_ui_target}" -type f -print0 | sort -z | xargs -0 -r sha256sum > "${RECORD_DIR}/prior-published-ui.sha256"

install -d -o murray -g www-data -m 0755 "${CANDIDATE_UI}"
cp -a "${prior_public_ui_target}/." "${CANDIDATE_UI}/"
while IFS= read -r file; do
  [[ -n "${file}" ]] || continue
  install -o murray -g www-data -m 0644 "${BUNDLE_DIR}/html/${file}" "${CANDIDATE_UI}/${file}"
done <<< "${expected_browser_files}"
find "${CANDIDATE_UI}" -type f -print0 | sort -z | xargs -0 -r sha256sum > "${RECORD_DIR}/candidate-ui.sha256"

server_installed=false
ui_switched=false
deployment_complete=false

switch_public_ui() {
  local target="$1"
  local temporary="${PUBLIC_UI_LINK}.new.$$"
  rm -f "${temporary}"
  ln -s "${target}" "${temporary}"
  mv -Tf "${temporary}" "${PUBLIC_UI_LINK}"
}

rollback_on_error() {
  local status=$?
  if [[ "${deployment_complete}" = true ]]; then
    return
  fi
  echo "Deployment failed; restoring prior 3.7.0.5 development soak Server/UI" >&2
  set +e
  if [[ "${server_installed}" = true && -f "${PREVIOUS_JAR}" ]]; then
    systemctl stop "${SERVICE}"
    cp -f "${PREVIOUS_JAR}" "${CURRENT_JAR}"
    chown root:kanger "${CURRENT_JAR}"
    chmod 0640 "${CURRENT_JAR}"
    systemctl start "${SERVICE}"
  fi
  if [[ "${ui_switched}" = true ]]; then
    switch_public_ui "${prior_public_ui_target}"
    nginx -t && systemctl reload nginx
  fi
  printf 'FAILED_ROLLED_BACK status=%s utc=%s\n' "${status}" "$(date -u +%Y-%m-%dT%H:%M:%SZ)" > "${RECORD_DIR}/STATUS"
  exit "${status}"
}
trap rollback_on_error EXIT

bash "${BUNDLE_DIR}/deploy/install.sh" "${BUNDLE_DIR}/kanger-server.jar"
server_installed=true
[[ -f "${PREVIOUS_JAR}" ]]
[[ "$(sha256sum "${PREVIOUS_JAR}" | awk '{print $1}')" = "${current_jar_sha}" ]]
[[ "$(sha256sum "${CURRENT_JAR}" | awk '{print $1}')" = "${candidate_jar_sha}" ]]
bash "${BUNDLE_DIR}/deploy/verify-installed.sh" | tee "${RECORD_DIR}/verify-installed.txt"

switch_public_ui "${CANDIDATE_UI}"
ui_switched=true
nginx -t
systemctl reload nginx

[[ "$(readlink -f "${PUBLIC_UI_LINK}")" = "${CANDIDATE_UI}" ]]

# Verify that nginx serves the exact candidate Browser bytes and that the
# presentation authority remains reachable through its real loader chain.
for file in index.html console.html javascript.js javascript-mode.js presentation.js presentation.css; do
  curl --fail --silent --show-error --insecure --max-time 10 \
    --resolve kanger.org:443:127.0.0.1 \
    "https://kanger.org/${file}" \
    -o "${RECORD_DIR}/origin-${file}"
  [[ "$(sha256sum "${RECORD_DIR}/origin-${file}" | awk '{print $1}')" = \
      "$(sha256sum "${BUNDLE_DIR}/html/${file}" | awk '{print $1}')" ]]
done

grep -q 'containment.js' "${RECORD_DIR}/origin-index.html"
grep -q 'sandbox="allow-scripts"' "${RECORD_DIR}/origin-index.html"
! grep -q 'allow-same-origin' "${RECORD_DIR}/origin-index.html"
grep -q 'src="javascript.js"' "${RECORD_DIR}/origin-console.html"
grep -q 'javascript-mode.js' "${RECORD_DIR}/origin-javascript.js"
grep -q 'presentation.js' "${RECORD_DIR}/origin-javascript-mode.js"
grep -q 'presentation.css' "${RECORD_DIR}/origin-presentation.js"

health_after="$(curl --fail --silent --show-error --max-time 5 "${HEALTH_URL}")"
ready_after="$(curl --fail --silent --show-error --max-time 5 "${READY_URL}")"
echo "${health_after}" | grep -q "\"server_version\":\"${EXPECTED_CANDIDATE_SERVER}\""
echo "${ready_after}" | grep -q "\"server_version\":\"${EXPECTED_CANDIDATE_SERVER}\""
[[ "$(sha256sum "${CURRENT_JAR}" | awk '{print $1}')" = "${candidate_jar_sha}" ]]
printf '%s\n' "${health_after}" > "${RECORD_DIR}/health-after.json"
printf '%s\n' "${ready_after}" > "${RECORD_DIR}/ready-after.json"
find "${CANDIDATE_UI}" -type f -print0 | sort -z | xargs -0 -r sha256sum > "${RECORD_DIR}/active-ui.sha256"

if public_health="$(curl --fail --silent --show-error --max-time 10 "${PUBLIC_HEALTH_URL}" 2>/dev/null)"; then
  printf '%s\n' "${public_health}" > "${RECORD_DIR}/public-health.json"
else
  printf '%s\n' "PUBLIC_HEALTH_UNAVAILABLE" > "${RECORD_DIR}/public-health.json"
fi

cat > "${RECORD_DIR}/DEPLOYMENT.txt" <<EOF
schema=5
status=DEVELOPMENT_SOAK_ACTIVE
started_utc=${STAMP}
artifact=3.7.0.6-vps-soak
canonical_source_head=${EXPECTED_SOURCE_HEAD}
packaging_branch=${EXPECTED_PACKAGING_BRANCH}
packaging_generation=dev1
server_version=${EXPECTED_CANDIDATE_SERVER}
candidate_jar_sha256=${candidate_jar_sha}
prior_development_soak=3.7.0.5
prior_server_version=${EXPECTED_CURRENT_SERVER}
prior_jar_sha256=${current_jar_sha}
editable_ui_directory=${EDITABLE_UI_DIR}
public_ui_link=${PUBLIC_UI_LINK}
prior_public_ui_target=${prior_public_ui_target}
candidate_public_ui_target=${CANDIDATE_UI}
ui_publication_mode=versioned-directory-atomic-symlink
unmanaged_ui_files_preserved_from_prior_target=true
snapshot_archive=${SNAPSHOT_ARCHIVE}
snapshot_sha256=${actual_snapshot_sha}
accepted_production_release=release/3.5.2
accepted_production_release_head=9c8b7dd2c9ef347cea6af6a6faef9cfa48030306
accepted_production_jar_sha256=9a8fb1a0f1505d74fb15343ed0519782abb33b53097d6c0e46fbad7bad962718
accepted_production_ui_target=/home/murray/sites/kanger-server-0.18-soak-r3-20260807T100726Z
release_acceptance=NOT_PERFORMED
rollback_command=sudo bash ${BUNDLE_DIR}/deploy/rollback-soak.sh ${RECORD_DIR}
EOF
printf 'DEVELOPMENT_SOAK_ACTIVE utc=%s\n' "$(date -u +%Y-%m-%dT%H:%M:%SZ)" > "${RECORD_DIR}/STATUS"

deployment_complete=true
trap - EXIT

echo "DEVELOPMENT_SOAK_DEPLOYMENT_OK"
echo "record=${RECORD_DIR}"
echo "server=${EXPECTED_CANDIDATE_SERVER}"
echo "candidate_jar_sha256=${candidate_jar_sha}"
echo "public_ui_link=${PUBLIC_UI_LINK}"
echo "prior_public_ui_target=${prior_public_ui_target}"
echo "candidate_public_ui_target=${CANDIDATE_UI}"
echo "rollback=sudo bash ${BUNDLE_DIR}/deploy/rollback-soak.sh ${RECORD_DIR}"
