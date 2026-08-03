#!/usr/bin/env bash
set -euo pipefail

BASE_URL="${KANGER_BASE_URL:-http://127.0.0.1:1964}"
PYTHON="${PYTHON:-python3}"

command -v curl >/dev/null 2>&1 || {
  echo "curl is required" >&2
  exit 1
}
command -v "${PYTHON}" >/dev/null 2>&1 || {
  echo "${PYTHON} is required" >&2
  exit 1
}

json_field() {
  local document="$1"
  local field="$2"
  printf '%s' "${document}" | "${PYTHON}" -c '
import json
import sys
value = json.load(sys.stdin).get(sys.argv[1], "")
if isinstance(value, (dict, list)):
    print(json.dumps(value, ensure_ascii=False))
else:
    print(value)
' "${field}"
}

post() {
  local payload="$1"
  curl --fail --silent --show-error \
    --request POST \
    --header 'Content-Type: application/json' \
    --data "${payload}" \
    "${BASE_URL}/"
}

require_result() {
  local response="$1"
  local expected="$2"
  local phase="$3"
  local actual
  actual="$(json_field "${response}" result)"
  if [[ "${actual}" != "${expected}" ]]; then
    echo "${phase} failed: ${response}" >&2
    exit 1
  fi
}

suffix="$(date +%s)-$$-${RANDOM}"
login="smoke-${suffix}"
password="Kanger-Smoke-${suffix}"

echo "[1/6] Registering isolated smoke user ${login}"
register_response="$(post "{\"context\":\"login\",\"parameters\":{\"register\":\"${login}\",\"password\":\"${password}\",\"token\":\"\",\"privacy\":true}}")"
require_result "${register_response}" "OK" "registration"
first_token="$(json_field "${register_response}" token)"
[[ -n "${first_token}" ]] || {
  echo "registration returned no session token: ${register_response}" >&2
  exit 1
}

echo "[2/6] Executing authenticated ping"
ping_response="$(post "{\"context\":\"command\",\"parameters\":{\"token\":\"${first_token}\",\"ping\":\"\"}}")"
require_result "${ping_response}" "OK" "first ping"

echo "[3/6] Logging out first session"
logout_response="$(post "{\"context\":\"command\",\"parameters\":{\"token\":\"${first_token}\",\"quit\":\"\"}}")"
require_result "${logout_response}" "OK" "first logout"

echo "[4/6] Verifying logged-out token is rejected"
rejected_response="$(post "{\"context\":\"command\",\"parameters\":{\"token\":\"${first_token}\",\"ping\":\"\"}}")"
require_result "${rejected_response}" "error" "logged-out token rejection"

echo "[5/6] Logging in again with stored credential"
login_response="$(post "{\"context\":\"login\",\"parameters\":{\"login\":\"${login}\",\"password\":\"${password}\"}}")"
require_result "${login_response}" "OK" "login"
second_token="$(json_field "${login_response}" token)"
[[ -n "${second_token}" && "${second_token}" != "${first_token}" ]] || {
  echo "login did not rotate the session token: ${login_response}" >&2
  exit 1
}

second_ping="$(post "{\"context\":\"command\",\"parameters\":{\"token\":\"${second_token}\",\"ping\":\"\"}}")"
require_result "${second_ping}" "OK" "second ping"

echo "[6/6] Closing second session"
second_logout="$(post "{\"context\":\"command\",\"parameters\":{\"token\":\"${second_token}\",\"quit\":\"\"}}")"
require_result "${second_logout}" "OK" "second logout"

echo "Authenticated KANGER Server smoke passed"
