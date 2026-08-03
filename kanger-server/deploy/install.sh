#!/usr/bin/env bash
set -euo pipefail

if [[ "${EUID}" -ne 0 ]]; then
  echo "Run as root: sudo bash $0 /path/to/kanger-server.jar" >&2
  exit 1
fi

if [[ $# -ne 1 ]]; then
  echo "Usage: $0 /path/to/kanger-server.jar" >&2
  exit 1
fi

SOURCE_JAR="$(readlink -f "$1")"
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
INSTALL_DIR="/opt/kanger-server"
STATE_DIR="/var/lib/kanger-server"
CONFIG_DIR="/etc/kanger-server"
UNIT_FILE="/etc/systemd/system/kanger-server.service"
TARGET_JAR="${INSTALL_DIR}/kanger-server.jar"
PREVIOUS_JAR="${INSTALL_DIR}/kanger-server.jar.previous"
HEALTH_URL="http://127.0.0.1:1964/health"

for command in java systemctl systemd-analyze journalctl curl install \
  getent groupadd useradd readlink; do
  command -v "${command}" >/dev/null 2>&1 || {
    echo "Required command not found: ${command}" >&2
    exit 1
  }
done

[[ -f "${SOURCE_JAR}" ]] || {
  echo "JAR not found: ${SOURCE_JAR}" >&2
  exit 1
}

if ! getent group kanger >/dev/null; then
  groupadd --system kanger
fi
if ! getent passwd kanger >/dev/null; then
  useradd --system \
    --gid kanger \
    --home-dir "${STATE_DIR}" \
    --shell /usr/sbin/nologin \
    kanger
fi

install -d -o root -g kanger -m 0750 "${INSTALL_DIR}"
install -d -o kanger -g kanger -m 0750 "${STATE_DIR}"
install -d -o root -g kanger -m 0750 "${CONFIG_DIR}"

if [[ ! -f "${CONFIG_DIR}/kanger.conf" ]]; then
  install -o root -g kanger -m 0640 \
    "${SCRIPT_DIR}/kanger.conf.example" \
    "${CONFIG_DIR}/kanger.conf"
  echo "Created ${CONFIG_DIR}/kanger.conf"
fi

ln -sfn "${CONFIG_DIR}/kanger.conf" "${STATE_DIR}/kanger.conf"
chown -h root:kanger "${STATE_DIR}/kanger.conf"

if [[ -f "${TARGET_JAR}" ]]; then
  cp -f "${TARGET_JAR}" "${PREVIOUS_JAR}"
  chown root:kanger "${PREVIOUS_JAR}"
  chmod 0640 "${PREVIOUS_JAR}"
fi

install -o root -g kanger -m 0640 "${SOURCE_JAR}" "${TARGET_JAR}.new"
mv -f "${TARGET_JAR}.new" "${TARGET_JAR}"

install -o root -g root -m 0644 \
  "${SCRIPT_DIR}/systemd/kanger-server.service" \
  "${UNIT_FILE}"

if [[ -f "${SCRIPT_DIR}/../DEPLOYMENT.md" ]]; then
  install -o root -g kanger -m 0640 \
    "${SCRIPT_DIR}/../DEPLOYMENT.md" \
    "${INSTALL_DIR}/DEPLOYMENT.md"
fi

systemd-analyze verify "${UNIT_FILE}"
systemctl daemon-reload
systemctl enable kanger-server.service >/dev/null
systemctl restart kanger-server.service

healthy=false
for attempt in $(seq 1 30); do
  if curl --fail --silent --show-error --max-time 2 \
      "${HEALTH_URL}" >/dev/null; then
    healthy=true
    break
  fi
  sleep 1
done

if [[ "${healthy}" != true ]]; then
  echo "KANGER Server failed its loopback health check." >&2
  systemctl status kanger-server.service --no-pager || true
  journalctl -u kanger-server.service -n 100 --no-pager || true

  if [[ -f "${PREVIOUS_JAR}" ]]; then
    echo "Restoring previous JAR..." >&2
    cp -f "${PREVIOUS_JAR}" "${TARGET_JAR}"
    chown root:kanger "${TARGET_JAR}"
    chmod 0640 "${TARGET_JAR}"
    systemctl restart kanger-server.service || true
  fi
  exit 1
fi

systemctl --no-pager --full status kanger-server.service
echo
echo "KANGER Server is healthy on ${HEALTH_URL}"
echo "Public nginx exposure is a separate explicit step."
