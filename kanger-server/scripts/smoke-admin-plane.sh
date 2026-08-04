#!/usr/bin/env bash
set -euo pipefail

BASE_URL="${KANGER_BASE_URL:-http://127.0.0.1:1964}"
STATE_HOME="${KANGER_SMOKE_HOME:?KANGER_SMOKE_HOME is required}"
SERVER_JAR="${KANGER_SERVER_JAR:?KANGER_SERVER_JAR is required}"
PYTHON="${PYTHON:-python3}"
CLI=(java -Duser.home="${STATE_HOME}" -cp "${SERVER_JAR}" org.kanger.admin.KangerAdmin)

fail() {
  echo "operator smoke: $*" >&2
  exit 1
}

json_field() {
  printf '%s' "$1" | "${PYTHON}" -c '
import json, sys
print(json.load(sys.stdin).get(sys.argv[1], ""))
' "$2"
}

post_public() {
  curl --fail --silent --show-error \
    --request POST \
    --header 'Content-Type: application/json' \
    --data "$1" \
    "${BASE_URL}/"
}

command -v java >/dev/null 2>&1 || fail "java is required"
command -v curl >/dev/null 2>&1 || fail "curl is required"
command -v "${PYTHON}" >/dev/null 2>&1 || fail "${PYTHON} is required"
[[ -f "${SERVER_JAR}" ]] || fail "server JAR is missing"

token_file="${STATE_HOME}/KANGER/admin.token"
for attempt in $(seq 1 30); do
  [[ -s "${token_file}" ]] && break
  sleep 1
done
[[ -s "${token_file}" ]] || fail "admin token was not created"
admin_token="$(tr -d '\r\n' < "${token_file}")"
[[ ${#admin_token} -ge 32 ]] || fail "admin token is malformed"

suffix="$(date +%s)-$$-${RANDOM}"
login="admin-smoke-${suffix}"
password="Admin-Smoke-Password-${suffix}"
email="${login}@example.org"

printf '%s\n' "[admin 1/6] Creating ACTIVE account through kanger-admin"
create_output="$(printf '%s\n' "${password}" | "${CLI[@]}" \
  create-user \
  --login "${login}" \
  --email "${email}" \
  --privacy-consent true \
  --password-stdin)"
[[ "${create_output}" == *"Created ACTIVE account ${login}"* ]] \
  || fail "unexpected create-user output"
[[ "${create_output}" != *"${password}"* ]] || fail "create-user leaked password"
[[ "${create_output}" != *"${admin_token}"* ]] || fail "create-user leaked admin token"
user_id="$(printf '%s' "${create_output}" \
  | sed -n 's/.*userId=\([0-9][0-9]*\)).*/\1/p')"
[[ -n "${user_id}" ]] || fail "create-user returned no user id"

profile="${STATE_HOME}/KANGER/${user_id}/kanger.conf"
[[ -f "${profile}" ]] || fail "operator-created profile is missing"
grep -q '^reg.email.confirmed=false$' "${profile}" \
  || fail "operator e-mail was incorrectly confirmed"
grep -q '^reg.agreed=false$' "${profile}" \
  || fail "legacy e-mail flag was incorrectly confirmed"
! grep -q "${password}" "${profile}" || fail "profile contains plaintext password"

printf '%s\n' "[admin 2/6] Authenticating through the ordinary public login path"
login_response="$(post_public "{\"context\":\"login\",\"parameters\":{\"login\":\"${login}\",\"password\":\"${password}\"}}")"
[[ "$(json_field "${login_response}" result)" = "OK" ]] \
  || fail "operator-created account could not authenticate"
session_token="$(json_field "${login_response}" token)"
[[ -n "${session_token}" ]] || fail "ordinary login returned no session token"

printf '%s\n' "[admin 3/6] Deleting the active account through kanger-admin"
delete_output="$("${CLI[@]}" delete-user --login "${login}" --yes)"
[[ "${delete_output}" == *"reached COMPLETE"* ]] \
  || fail "delete-user did not reach COMPLETE"
[[ "${delete_output}" != *"${password}"* ]] || fail "delete-user leaked password"
[[ "${delete_output}" != *"${admin_token}"* ]] || fail "delete-user leaked admin token"
record_id="$(printf '%s' "${delete_output}" \
  | sed -n 's/^Deletion \([^[:space:]]*\) reached COMPLETE$/\1/p')"
[[ -n "${record_id}" ]] || fail "delete-user returned no deletion id"

printf '%s\n' "[admin 4/6] Verifying credential and session revocation"
rejected_login="$(post_public "{\"context\":\"login\",\"parameters\":{\"login\":\"${login}\",\"password\":\"${password}\"}}")"
[[ "$(json_field "${rejected_login}" result)" = "error" ]] \
  || fail "deleted credential still authenticates"
rejected_session="$(post_public "{\"context\":\"command\",\"parameters\":{\"token\":\"${session_token}\",\"ping\":\"\"}}")"
[[ "$(json_field "${rejected_session}" result)" = "error" ]] \
  || fail "deleted account session remains active"

printf '%s\n' "[admin 5/6] Verifying canonical-home quarantine and journal"
[[ ! -e "${STATE_HOME}/KANGER/${user_id}" ]] || fail "canonical home still exists"
quarantine_home="${STATE_HOME}/KANGER/.quarantine/${user_id}-${record_id:0:16}"
[[ -d "${quarantine_home}" ]] || fail "exact quarantine tree is missing"
count="$(find "${STATE_HOME}/KANGER/.quarantine" \
  -mindepth 1 -maxdepth 1 -type d -name "${user_id}-*" | wc -l)"
[[ "${count}" -eq 1 ]] || fail "unexpected quarantine tree count"

journal="${STATE_HOME}/KANGER/account-deletions.conf"
[[ -s "${journal}" ]] || fail "account deletion journal is missing"
"${PYTHON}" - "${journal}" "${record_id}" "${user_id}" \
  "${login}" "${quarantine_home}" <<'PY'
import base64
import json
import os
import sys

journal, record_id, user_id, login, quarantine_home = sys.argv[1:]
record = None
with open(journal, encoding="utf-8") as stream:
    for raw in stream:
        line = raw.strip()
        if not line or line.startswith(("#", "!")):
            continue
        version, payload = line.split("\t", 1)
        if version != "v1":
            raise SystemExit("unexpected deletion journal version")
        payload += "=" * (-len(payload) % 4)
        candidate = json.loads(base64.urlsafe_b64decode(payload).decode("utf-8"))
        if candidate.get("id") == record_id:
            record = candidate
            break

if record is None:
    raise SystemExit("exact deletion journal record is missing")
if int(record.get("userId", -1)) != int(user_id):
    raise SystemExit("deletion journal userId mismatch")
if record.get("login") != login:
    raise SystemExit("deletion journal login mismatch")
if record.get("state") != "COMPLETE":
    raise SystemExit("deletion journal did not reach COMPLETE")
if os.path.realpath(record.get("quarantineHome", "")) != os.path.realpath(quarantine_home):
    raise SystemExit("deletion journal quarantine path mismatch")
PY

printf '%s\n' "[admin 6/6] Verifying public listener cannot dispatch admin paths"
public_response="$(curl --fail --silent --show-error \
  --request POST \
  --header 'Content-Type: application/json' \
  --data '{}' \
  "${BASE_URL}/create-user")"
[[ "$(json_field "${public_response}" result)" = "error" ]] \
  || fail "public listener unexpectedly dispatched admin operation"

printf '%s\n' "KANGER local operator plane smoke passed"
