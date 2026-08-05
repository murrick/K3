#!/usr/bin/env bash
set -Eeuo pipefail
export LC_ALL=C

EXPECTED_JAR_SHA256="e089497d0a8f041a872a3a5a09581f8d94f5962a277794747b7f54e209882a19"
SOURCE_JAR="/tmp/kanger-server.jar"
DEPLOY_DIR="/tmp/kanger-deploy"
CONFIG="/etc/kanger-server/kanger.conf"
TARGET_JAR="/opt/kanger-server/kanger-server.jar"
PREVIOUS_JAR="/opt/kanger-server/kanger-server.jar.previous"
UNIT_FILE="/etc/systemd/system/kanger-server.service"
ADMIN_BIN="/usr/local/bin/kanger-admin"
SERVICE="kanger-server.service"
HEALTH_URL="http://127.0.0.1:1964/health"
READY_URL="http://127.0.0.1:1964/ready"

if [[ "${EUID}" -ne 0 ]]; then
  echo "Run as root: sudo $0" >&2
  exit 1
fi

for command in awk curl date grep install mktemp mv rm seq sha256sum sleep systemctl unzip; do
  command -v "${command}" >/dev/null 2>&1 || {
    echo "Required command not found: ${command}" >&2
    exit 1
  }
done

for path in \
  "${SOURCE_JAR}" \
  "${DEPLOY_DIR}/install.sh" \
  "${DEPLOY_DIR}/verify-installed.sh" \
  "${DEPLOY_DIR}/kanger-admin" \
  "${DEPLOY_DIR}/systemd/kanger-server.service" \
  "${CONFIG}" \
  "${UNIT_FILE}"; do
  [[ -f "${path}" ]] || {
    echo "Required staged or installed file is absent: ${path}" >&2
    exit 1
  }
done

actual_sha256="$(sha256sum "${SOURCE_JAR}" | awk '{print $1}')"
[[ "${actual_sha256}" == "${EXPECTED_JAR_SHA256}" ]] || {
  echo "Staged JAR SHA-256 mismatch" >&2
  echo "expected=${EXPECTED_JAR_SHA256}" >&2
  echo "actual=${actual_sha256}" >&2
  exit 1
}

build_properties="$(unzip -p "${SOURCE_JAR}" org/kanger/build.properties)"
printf '%s\n' "${build_properties}"
grep -qx 'branch=server-0.14' <<<"${build_properties}"
grep -qx 'source.branch=develop/server/0.14' <<<"${build_properties}"
grep -qx 'server.version=server-0.14' <<<"${build_properties}"

pre_health="$(curl --fail --silent --show-error --max-time 5 "${HEALTH_URL}")"
echo "Pre-deployment health: ${pre_health}"
grep -q '"status":"UP"' <<<"${pre_health}"
grep -q '"server_version":"server-0.13"' <<<"${pre_health}"

stamp="$(date -u +%Y%m%dT%H%M%SZ)"
config_backup="${CONFIG}.pre-0.14-${stamp}"
unit_backup="${UNIT_FILE}.pre-0.14-${stamp}"
admin_backup="${ADMIN_BIN}.pre-0.14-${stamp}"
admin_preexisting=false

install -o root -g kanger -m 0640 "${CONFIG}" "${config_backup}"
install -o root -g root -m 0644 "${UNIT_FILE}" "${unit_backup}"
if [[ -f "${ADMIN_BIN}" ]]; then
  install -o root -g root -m 0755 "${ADMIN_BIN}" "${admin_backup}"
  admin_preexisting=true
fi

echo "Configuration rollback copy: ${config_backup}"
echo "Systemd rollback copy:      ${unit_backup}"

rollback() {
  local exit_code=$?
  trap - ERR INT TERM
  set +e

  echo "Server 0.14 deployment failed; restoring exact Server 0.13 boundary..." >&2
  systemctl stop "${SERVICE}" || true

  install -o root -g kanger -m 0640 "${config_backup}" "${CONFIG}"
  install -o root -g root -m 0644 "${unit_backup}" "${UNIT_FILE}"

  if [[ "${admin_preexisting}" == true && -f "${admin_backup}" ]]; then
    install -o root -g root -m 0755 "${admin_backup}" "${ADMIN_BIN}"
  else
    rm -f "${ADMIN_BIN}"
  fi

  if [[ -f "${TARGET_JAR}" ]]; then
    current_sha="$(sha256sum "${TARGET_JAR}" 2>/dev/null | awk '{print $1}')"
  else
    current_sha=""
  fi

  if [[ "${current_sha}" == "${EXPECTED_JAR_SHA256}" && -f "${PREVIOUS_JAR}" ]]; then
    install -o root -g kanger -m 0640 "${PREVIOUS_JAR}" "${TARGET_JAR}"
  fi

  systemctl daemon-reload || true
  systemctl restart "${SERVICE}" || true

  recovered_ok=false
  for _ in $(seq 1 30); do
    recovered="$(curl --silent --show-error --max-time 2 "${HEALTH_URL}" 2>/dev/null || true)"
    if grep -q '"server_version":"server-0.13"' <<<"${recovered}"; then
      recovered_ok=true
      echo "Rollback recovery: server-0.13 / UP" >&2
      break
    fi
    sleep 1
  done

  if [[ "${recovered_ok}" != true ]]; then
    echo "Rollback could not prove server-0.13 recovery." >&2
    systemctl status "${SERVICE}" --no-pager --full || true
  fi

  exit "${exit_code}"
}
trap rollback ERR INT TERM

upsert_property() {
  local key="$1"
  local value="$2"
  local tmp
  tmp="$(mktemp "${CONFIG}.XXXXXX")"

  awk -v target="${key}" -v replacement="${value}" '
    BEGIN { replaced = 0 }
    {
      line = $0
      equals = index(line, "=")
      if (equals > 0) {
        candidate = substr(line, 1, equals - 1)
        gsub(/^[[:space:]]+|[[:space:]]+$/, "", candidate)
      } else {
        candidate = ""
      }

      if (candidate == target) {
        if (!replaced) {
          print target "=" replacement
          replaced = 1
        }
      } else {
        print line
      }
    }
    END {
      if (!replaced) {
        print target "=" replacement
      }
    }
  ' "${CONFIG}" > "${tmp}"

  install -o root -g kanger -m 0640 "${tmp}" "${CONFIG}.new"
  mv -f "${CONFIG}.new" "${CONFIG}"
  rm -f "${tmp}"
}

# Preserve SMTP credentials and the current EMAIL_VERIFIED mode. Add only the
# release topology and browser/API boundaries required by Server 0.14.
upsert_property server.bind.address 127.0.0.1
upsert_property server.port 1964
upsert_property server.admin.enabled true
upsert_property server.admin.bind.address 127.0.0.1
upsert_property server.admin.port 1965
upsert_property server.admin.request.max.body.bytes 65536
upsert_property server.admin.token.file KANGER/admin.token
upsert_property server.url https://api.kanger.org
upsert_property server.confirmation.redirect.url https://kanger.org/
upsert_property server.cors.allowed.origin.1 https://kanger.org
upsert_property server.cors.allowed.origin.2 https://www.kanger.org
upsert_property server.cors.allow.credentials false

# Do not print the complete configuration: it contains SMTP credentials.
echo "Safe deployment configuration:"
grep -E '^(server\.bind\.address|server\.port|server\.admin\.(enabled|bind\.address|port|request\.max\.body\.bytes|token\.file)|server\.email\.mode|server\.url|server\.confirmation\.redirect\.url|server\.cors\.allowed\.origin\.[0-9]+|server\.cors\.allow\.credentials)=' "${CONFIG}"

bash "${DEPLOY_DIR}/install.sh" "${SOURCE_JAR}"
bash "${DEPLOY_DIR}/verify-installed.sh"

installed_sha256="$(sha256sum "${TARGET_JAR}" | awk '{print $1}')"
[[ "${installed_sha256}" == "${EXPECTED_JAR_SHA256}" ]]

previous_properties="$(unzip -p "${PREVIOUS_JAR}" org/kanger/build.properties)"
grep -qx 'server.version=server-0.13' <<<"${previous_properties}"

post_health="$(curl --fail --silent --show-error --max-time 5 "${HEALTH_URL}")"
post_ready="$(curl --fail --silent --show-error --max-time 5 "${READY_URL}")"
grep -q '"server_version":"server-0.14"' <<<"${post_health}"
grep -q '"server_version":"server-0.14"' <<<"${post_ready}"

trap - ERR INT TERM

echo
echo "KANGER Server 0.14 guarded installation complete"
echo "DEPLOYMENT_GATE=PASS"
echo "INSTALLED_SHA256=${installed_sha256}"
echo "CONFIG_ROLLBACK_COPY=${config_backup}"
echo "SYSTEMD_ROLLBACK_COPY=${unit_backup}"
echo "PREVIOUS_JAR_IDENTITY=server-0.13"
echo "SERVICE_ACTIVE=$(systemctl is-active "${SERVICE}")"
echo "${post_health}"
echo "${post_ready}"
