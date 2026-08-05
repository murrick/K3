#!/usr/bin/env bash
set -euo pipefail

HEALTH_URL="${KANGER_HEALTH_URL:-http://127.0.0.1:1964/health}"
READY_URL="${KANGER_READY_URL:-http://127.0.0.1:1964/ready}"

for command in systemctl curl ss nginx; do
  command -v "${command}" >/dev/null 2>&1 || {
    echo "Required command not found: ${command}" >&2
    exit 1
  }
done

assert_loopback_listener() {
  local port="$1"
  local label="$2"
  local listeners

  listeners="$(ss -H -ltn "( sport = :${port} )")"
  [[ -n "${listeners}" ]] || {
    echo "No ${label} listener found on port ${port}" >&2
    exit 1
  }

  echo "${listeners}" \
    | grep -Eq "127\\.0\\.0\\.1:${port}|\\[::ffff:127\\.0\\.0\\.1\\]:${port}" || {
      echo "KANGER ${label} listener is not confined to IPv4 loopback:" >&2
      echo "${listeners}" >&2
      exit 1
    }

  if echo "${listeners}" \
      | grep -Eq "(^|[[:space:]])(0\\.0\\.0\\.0|\\[::\\]):${port}"; then
    echo "KANGER ${label} listener is publicly bound on port ${port}" >&2
    echo "${listeners}" >&2
    exit 1
  fi
}

systemctl is-enabled --quiet kanger-server.service
systemctl is-active --quiet kanger-server.service

health="$(curl --fail --silent --show-error --max-time 3 "${HEALTH_URL}")"
ready="$(curl --fail --silent --show-error --max-time 3 "${READY_URL}")"
echo "${health}"
echo "${ready}"
echo "${health}" | grep -q '"status":"UP"'
echo "${ready}" | grep -q '"status":"READY"'
echo "${health}" | grep -q '"server_version":"server-0.16"'
echo "${ready}" | grep -q '"server_version":"server-0.16"'

assert_loopback_listener 1964 "application"
assert_loopback_listener 1965 "operator"

nginx -t

echo "KANGER Server 0.16 service, readiness, application/operator loopback confinement and nginx configuration are valid."
