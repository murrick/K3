#!/usr/bin/env bash
set -euo pipefail

HEALTH_URL="${KANGER_HEALTH_URL:-http://127.0.0.1:1964/health}"

for command in systemctl curl ss nginx; do
  command -v "${command}" >/dev/null 2>&1 || {
    echo "Required command not found: ${command}" >&2
    exit 1
  }
done

systemctl is-enabled --quiet kanger-server.service
systemctl is-active --quiet kanger-server.service
curl --fail --silent --show-error --max-time 3 "${HEALTH_URL}"
echo

listeners="$(ss -H -ltn '( sport = :1964 )')"
[[ -n "${listeners}" ]] || {
  echo "No listener found on port 1964" >&2
  exit 1
}

echo "${listeners}" | grep -Eq '127\.0\.0\.1:1964|\[::ffff:127\.0\.0\.1\]:1964' || {
  echo "KANGER Server is not confined to IPv4 loopback:" >&2
  echo "${listeners}" >&2
  exit 1
}

if echo "${listeners}" | grep -Eq '(^|[[:space:]])(0\.0\.0\.0|\[::\]):1964'; then
  echo "KANGER Server is publicly bound on port 1964" >&2
  echo "${listeners}" >&2
  exit 1
fi

nginx -t

echo "KANGER service, loopback confinement and nginx configuration are valid."
