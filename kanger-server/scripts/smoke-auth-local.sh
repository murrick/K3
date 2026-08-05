#!/usr/bin/env bash
set -euo pipefail

BASE_URL="${KANGER_BASE_URL:-http://127.0.0.1:1964}"
PYTHON="${PYTHON:-python3}"
STATE_HOME="${KANGER_SMOKE_HOME:-${RUNNER_TEMP:-}/kanger-server-smoke/home}"
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

command -v curl >/dev/null 2>&1 || {
  echo "curl is required" >&2
  exit 1
}
command -v "${PYTHON}" >/dev/null 2>&1 || {
  echo "${PYTHON} is required" >&2
  exit 1
}
[[ -n "${STATE_HOME}" ]] || {
  echo "KANGER_SMOKE_HOME or RUNNER_TEMP is required for the isolated credential fixture" >&2
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

require_code() {
  local response="$1"
  local expected="$2"
  local phase="$3"
  local actual
  actual="$(json_field "${response}" code)"
  if [[ "${actual}" != "${expected}" ]]; then
    echo "${phase} returned unexpected code: ${response}" >&2
    exit 1
  fi
}

legacy_token() {
  local login="$1"
  local password="$2"
  "${PYTHON}" - "${login}" "${password}" <<'PY'
import sys

def java_hash(value):
    result = 0
    for character in value:
        result = (31 * result + ord(character)) & 0xffffffff
    return result

print(f"{java_hash(sys.argv[1]):04x}{java_hash(sys.argv[2]):04x}")
PY
}

suffix="$(date +%s)-$$-${RANDOM}"
login="smoke-${suffix}"
password="Kanger-Smoke-${suffix}"
mail_login="mail-disabled-${suffix}"
mail_password="Mail-Disabled-${suffix}"

printf '%s\n' "[1/12] Reading the public TRUSTED authentication capability snapshot"
capability_response="$(post '{"context":"version","parameters":{}}')"
require_result "${capability_response}" "OK" "authentication capability snapshot"
printf '%s' "${capability_response}" | "${PYTHON}" -c '
import json
import sys
response = json.load(sys.stdin)
auth = response.get("auth") or {}
expected = {
    "registration_policy": "TRUSTED",
    "public_registration": False,
    "email_confirmation_required": False,
    "confirmation_creates_session": False,
    "pending_registration_actions": False,
}
for key, value in expected.items():
    if auth.get(key) != value:
        raise SystemExit("unexpected auth capability %s=%r in %r" % (key, auth.get(key), auth))
'

printf '%s\n' "[2/12] Rejecting e-mail registration in TRUSTED mode"
mail_response="$(post "{\"context\":\"login\",\"parameters\":{\"register\":\"${mail_login}\",\"password\":\"${mail_password}\",\"token\":\"\",\"email\":\"${mail_login}@example.org\",\"privacy\":true}}")"
require_result "${mail_response}" "error" "trusted e-mail registration"
require_code "${mail_response}" "REGISTRATION_DISABLED" "trusted e-mail registration"

printf '%s\n' "[3/12] Verifying rejected e-mail registration created no credential"
mail_login_response="$(post "{\"context\":\"login\",\"parameters\":{\"login\":\"${mail_login}\",\"password\":\"${mail_password}\"}}")"
require_result "${mail_login_response}" "error" "trusted e-mail credential rejection"

printf '%s\n' "[4/12] Rejecting registration without e-mail in TRUSTED mode"
register_response="$(post "{\"context\":\"login\",\"parameters\":{\"register\":\"${login}\",\"password\":\"${password}\",\"token\":\"\",\"privacy\":true}}")"
require_result "${register_response}" "error" "trusted registration"
require_code "${register_response}" "REGISTRATION_DISABLED" "trusted registration"

printf '%s\n' "[5/12] Verifying rejected registration created no credential"
rejected_login="$(post "{\"context\":\"login\",\"parameters\":{\"login\":\"${login}\",\"password\":\"${password}\"}}")"
require_result "${rejected_login}" "error" "trusted credential rejection"

printf '%s\n' "[6/12] Provisioning unconfirmed Server 0.13 credential fixture"
state_dir="${STATE_HOME}/KANGER"
user_dir="${state_dir}/1"
mkdir -p "${user_dir}"
credential_token="$(legacy_token "${login}" "${password}")"
printf '# isolated Server 0.13 migration fixture\n%s=1\n' \
  "${credential_token}" > "${state_dir}/users.conf"
cat > "${user_dir}/kanger.conf" <<EOF
reg.login=${login}
reg.agreed=false
reg.email.confirmed=false
EOF

printf '%s\n' "[7/12] Logging in with the existing unconfirmed credential"
login_response="$(post "{\"context\":\"login\",\"parameters\":{\"login\":\"${login}\",\"password\":\"${password}\"}}")"
require_result "${login_response}" "OK" "existing-account login"
first_token="$(json_field "${login_response}" token)"
[[ -n "${first_token}" ]] || {
  echo "login returned no session token: ${login_response}" >&2
  exit 1
}

printf '%s\n' "[8/12] Executing authenticated ping"
ping_response="$(post "{\"context\":\"command\",\"parameters\":{\"token\":\"${first_token}\",\"ping\":\"\"}}")"
require_result "${ping_response}" "OK" "first ping"

printf '%s\n' "[9/12] Logging out and rejecting the closed token"
logout_response="$(post "{\"context\":\"command\",\"parameters\":{\"token\":\"${first_token}\",\"quit\":\"\"}}")"
require_result "${logout_response}" "OK" "first logout"
closed_response="$(post "{\"context\":\"command\",\"parameters\":{\"token\":\"${first_token}\",\"ping\":\"\"}}")"
require_result "${closed_response}" "error" "logged-out token rejection"

printf '%s\n' "[10/12] Logging in again and requiring token rotation"
second_login="$(post "{\"context\":\"login\",\"parameters\":{\"login\":\"${login}\",\"password\":\"${password}\"}}")"
require_result "${second_login}" "OK" "second login"
second_token="$(json_field "${second_login}" token)"
[[ -n "${second_token}" && "${second_token}" != "${first_token}" ]] || {
  echo "login did not rotate the session token: ${second_login}" >&2
  exit 1
}
second_ping="$(post "{\"context\":\"command\",\"parameters\":{\"token\":\"${second_token}\",\"ping\":\"\"}}")"
require_result "${second_ping}" "OK" "second ping"

printf '%s\n' "[11/12] Opening two explicit transaction levels"
first_transaction="$(post "{\"context\":\"query\",\"parameters\":{\"token\":\"${second_token}\",\"transaction\":\"create\"}}")"
require_result "${first_transaction}" "OK" "first transaction create"
second_transaction="$(post "{\"context\":\"query\",\"parameters\":{\"token\":\"${second_token}\",\"transaction\":\"create\"}}")"
require_result "${second_transaction}" "OK" "second transaction create"
[[ "$(json_field "${second_transaction}" transaction)" = "2" ]] || {
  echo "nested transaction depth was not published: ${second_transaction}" >&2
  exit 1
}

printf '%s\n' "[12/12] Closing the nested session and rejecting its token"
second_logout="$(post "{\"context\":\"command\",\"parameters\":{\"token\":\"${second_token}\",\"quit\":\"\"}}")"
require_result "${second_logout}" "OK" "nested second logout"
second_closed="$(post "{\"context\":\"command\",\"parameters\":{\"token\":\"${second_token}\",\"ping\":\"\"}}")"
require_result "${second_closed}" "error" "nested logged-out token rejection"

printf '%s\n' "TRUSTED policy, public capabilities, nested lifecycle and existing-account authentication smoke passed"

server_jar="${KANGER_SERVER_JAR:-${GITHUB_WORKSPACE:-}/kanger-server/target/kanger-server.jar}"
[[ -f "${server_jar}" ]] || {
  echo "KANGER_SERVER_JAR is required for operator-plane smoke" >&2
  exit 1
}
KANGER_BASE_URL="${BASE_URL}" \
KANGER_SMOKE_HOME="${STATE_HOME}" \
KANGER_SERVER_JAR="${server_jar}" \
  bash "${SCRIPT_DIR}/smoke-admin-local.sh"
