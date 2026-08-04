#!/usr/bin/env bash
set -euo pipefail

BASE_URL="${KANGER_BASE_URL:-http://127.0.0.1:1964}"
STATE_HOME="${KANGER_SMOKE_HOME:?KANGER_SMOKE_HOME is required}"
SERVER_JAR="${KANGER_SERVER_JAR:?KANGER_SERVER_JAR is required}"
PYTHON="${PYTHON:-python3}"

command -v java >/dev/null 2>&1 || {
  echo "java is required" >&2
  exit 1
}
command -v curl >/dev/null 2>&1 || {
  echo "curl is required" >&2
  exit 1
}
command -v "${PYTHON}" >/dev/null 2>&1 || {
  echo "${PYTHON} is required" >&2
  exit 1
}

token_file="${STATE_HOME}/KANGER/admin.token"
for attempt in $(seq 1 30); do
  [[ -s "${token_file}" ]] && break
  sleep 1
done
[[ -s "${token_file}" ]] || {
  echo "admin token was not created" >&2
  exit 1
}

json_field() {
  local document="$1"
  local field="$2"
  printf '%s' "${document}" | "${PYTHON}" -c '
import json
import sys
print(json.load(sys.stdin).get(sys.argv[1], ""))
' "${field}"
}

post_public() {
  curl --fail --silent --show-error \
    --request POST \
    --header 'Content-Type: application/json' \
    --data "$1" \
    "${BASE_URL}/"
}

suffix="$(date +%s)-$$-${RANDOM}"
login="admin-smoke-${suffix}"
password="Admin-Smoke-Password-${suffix}"
email="${login}@example.org"

printf '%s\n' "[admin 1/6] Creating ACTIVE account through kanger-admin"
create_output="$(printf '%s\n' "${password}" | java \
  -Duser.home="${STATE_HOME}" \
  -cp "${SERVER_JAR}" \
  org.kanger.admin.KangerAdmin \
  create-user \
  --login "${login}" \
  --email "${email}" \
  --privacy-consent true \
  --password-stdin)"
[[ "${create_output}" == *"Created ACTIVE account ${login}"* ]] || {
  echo "unexpected create-user output" >&2
  exit 1
}
[[ "${create_output}" != *"${password}"* ]] || {
  echo "create-user leaked plaintext password" >&2
  exit 1
}
user_id="$(printf '%s' "${create_output}" | sed -n 's/.*userId=\([0-9][0-9]*\)).*/\1/p')"
[[ -n "${user_id}" ]] || {
  echo "create-user returned no user id" >&2
  exit 1
}
profile="${STATE_HOME}/KANGER/${user_id}/kanger.conf"
[[ -f "${profile}" ]] || {
  echo "operator-created account profile is missing" >&2
  exit 1
}
grep -q '^reg.email.confirmed=false$' "${profile}"
grep -q '^reg.agreed=false$' "${profile}"
! grep -q "${password}" "${profile}"

printf '%s\n' "[admin 2/6] Authenticating through the ordinary public login path"
login_response="$(post_public "{\"context\":\"login\",\"parameters\":{\"login\":\"${login}\",\"password\":\"${password}\"}}")"
[[ "$(json_field "${login_response}" result)" = "OK" ]] || {
  echo "operator-created account could not authenticate" >&2
  exit 1
}
session_token="$(json_field "${login_response}" token)"
[[ -n "${session_token}" ]] || {
  echo "ordinary login returned no session token" >&2
  exit 1
}

printf '%s\n' "[admin 3/6] Deleting the active account through kanger-admin"
delete_output="$(java \
  -Duser.home="${STATE_HOME}" \
  -cp "${SERVER_JAR}" \
  org.kanger.admin.KangerAdmin \
  delete-user --login "${login}" --yes)"
[[ "${delete_output}" == *"reached COMPLETE"* ]] || {
  echo "delete-user did not reach COMPLETE" >&2
  exit 1
}
[[ "${delete_output}" != *"${password}"* ]] || {
  echo "delete-user leaked plaintext password" >&2
  exit 1
}

printf '%s\n' "[admin 4/6] Verifying credential and session revocation"
rejected_login="$(post_public "{\"context\":\"login\",\"parameters\":{\"login\":\"${login}\",\"password\":\"${password}\"}}")"
[[ "$(json_field "${rejected_login}" result)" = "error" ]] || {
  echo "deleted credential still authenticates" >&2
  exit 1
}
rejected_session="$(post_public "{\"context\":\"command\",\"parameters\":{\"token\":\"${session_token}\",\"ping\":\"\"}}")"
[[ "$(json_field "${rejected_session}" result)" = "error" ]] || {
  echo "deleted account session remains active" >&2
  exit 1
}

printf '%s\n' "[admin 5/6] Verifying canonical-home quarantine"
[[ ! -e "${STATE_HOME}/KANGER/${user_id}" ]] || {
  echo "deleted canonical home still exists" >&2
  exit 1
}
quarantine_count="$(find "${STATE_HOME}/KANGER/.quarantine" \
  -mindepth 1 -maxdepth 1 -type d -name "${user_id}-*" | wc -l)"
[[ "${quarantine_count}" -eq 1 ]] || {
  echo "expected one exact quarantine tree, found ${quarantine_count}" >&2
  exit 1
}
grep -q "${login}" "${STATE_HOME}/KANGER/account-deletions.conf"

printf '%s\n' "[admin 6/6] Verifying public listener cannot dispatch admin paths"
public_admin_response="$(curl --fail --silent --show-error \
  --request POST --header 'Content-Type: application/json' --data '{}' \
  "${BASE_URL}/create-user")"
[[ "$(json_field "${public_admin_response}" result)" = "error" ]] || {
  echo "public listener unexpectedly dispatched admin operation" >&2
  exit 1
}

printf '%s\n' "KANGER local operator plane smoke passed"
