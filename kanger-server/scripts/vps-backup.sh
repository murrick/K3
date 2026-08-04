#!/usr/bin/env bash
set -euo pipefail
export LC_ALL=C

if [[ "${EUID}" -ne 0 ]]; then
  echo "Run as root: sudo $0" >&2
  exit 1
fi

for command in systemctl tar sha256sum curl install stat; do
  command -v "${command}" >/dev/null 2>&1 || {
    echo "Required command not found: ${command}" >&2
    exit 1
  }
done

SERVICE="kanger-server.service"
HEALTH_URL="http://127.0.0.1:1964/health"
READY_URL="http://127.0.0.1:1964/ready"
TRANSFER_USER="${SUDO_USER:-root}"
TRANSFER_HOME="$(getent passwd "${TRANSFER_USER}" | cut -d: -f6)"

[[ -n "${TRANSFER_HOME}" && -d "${TRANSFER_HOME}" ]] || {
  echo "Cannot resolve transfer home for ${TRANSFER_USER}" >&2
  exit 1
}

stamp="$(date -u +%Y%m%dT%H%M%SZ)"
archive_name="kanger-server-pre-0.14-${stamp}.tar.gz"
archive_path="${TRANSFER_HOME}/${archive_name}"
checksum_path="${archive_path}.sha256"
manifest_path="${archive_path}.manifest.txt"

paths=(
  etc/kanger-server
  var/lib/kanger-server
  opt/kanger-server
  etc/systemd/system/kanger-server.service
  etc/nginx/sites-available/kanger-server.conf
  etc/nginx/sites-available/kanger-uix.conf
  etc/nginx/sites-enabled/kanger-server.conf
  etc/nginx/sites-enabled/kanger-uix.conf
)

existing_paths=()
for path in "${paths[@]}"; do
  if [[ -e "/${path}" || -L "/${path}" ]]; then
    existing_paths+=("${path}")
  else
    echo "Optional backup path absent: /${path}" >&2
  fi
done

[[ -d /etc/kanger-server ]] || {
  echo "Required path absent: /etc/kanger-server" >&2
  exit 1
}
[[ -d /var/lib/kanger-server ]] || {
  echo "Required path absent: /var/lib/kanger-server" >&2
  exit 1
}
[[ -f /opt/kanger-server/kanger-server.jar ]] || {
  echo "Required JAR absent: /opt/kanger-server/kanger-server.jar" >&2
  exit 1
}

was_active=false
if systemctl is-active --quiet "${SERVICE}"; then
  was_active=true
fi

service_restored=false
restore_service() {
  local exit_code=$?
  if [[ "${was_active}" == true && "${service_restored}" != true ]]; then
    echo "Restoring ${SERVICE} after backup interruption..." >&2
    systemctl start "${SERVICE}" || true
  fi
  exit "${exit_code}"
}
trap restore_service EXIT INT TERM

if [[ "${was_active}" == true ]]; then
  echo "Stopping ${SERVICE} for a transactionally quiet snapshot..."
  systemctl stop "${SERVICE}"
  systemctl is-active --quiet "${SERVICE}" && {
    echo "Service did not stop cleanly" >&2
    exit 1
  }
fi

umask 077
{
  echo "KANGER Server pre-0.14 backup manifest"
  echo "created_utc=${stamp}"
  echo "host=$(hostname -f)"
  echo "service_was_active=${was_active}"
  echo
  printf 'included=/%s\n' "${existing_paths[@]}"
  echo
  stat -c '%A %U:%G %s %n -> %N' \
    /etc/kanger-server \
    /etc/kanger-server/kanger.conf \
    /var/lib/kanger-server \
    /var/lib/kanger-server/kanger.conf \
    /opt/kanger-server/kanger-server.jar \
    /opt/kanger-server/kanger-server.jar.previous 2>/dev/null || true
  echo
  sha256sum /opt/kanger-server/kanger-server.jar
  [[ ! -f /opt/kanger-server/kanger-server.jar.previous ]] \
    || sha256sum /opt/kanger-server/kanger-server.jar.previous
} > "${manifest_path}"

tar -C / -czf "${archive_path}" "${existing_paths[@]}"
sha256sum "${archive_path}" > "${checksum_path}"

chown "${TRANSFER_USER}:${TRANSFER_USER}" \
  "${archive_path}" "${checksum_path}" "${manifest_path}"
chmod 0600 "${archive_path}" "${checksum_path}" "${manifest_path}"

if [[ "${was_active}" == true ]]; then
  echo "Starting ${SERVICE}..."
  systemctl start "${SERVICE}"
  service_restored=true

  ready=false
  for _ in $(seq 1 30); do
    if curl --fail --silent --show-error --max-time 2 "${HEALTH_URL}" >/dev/null \
      && curl --fail --silent --show-error --max-time 2 "${READY_URL}" >/dev/null; then
      ready=true
      break
    fi
    sleep 1
  done

  if [[ "${ready}" != true ]]; then
    echo "Service did not recover health/readiness after backup" >&2
    systemctl status "${SERVICE}" --no-pager --full || true
    exit 1
  fi
fi

trap - EXIT INT TERM

archive_sha256="$(awk '{print $1}' "${checksum_path}")"
echo
echo "KANGER pre-deployment backup complete"
echo "ARCHIVE=${archive_path}"
echo "CHECKSUM=${checksum_path}"
echo "MANIFEST=${manifest_path}"
echo "SHA256=${archive_sha256}"
echo "SERVICE_ACTIVE=$(systemctl is-active "${SERVICE}" 2>/dev/null || true)"
echo "Copy all three files off-host and verify the archive checksum before deployment."
