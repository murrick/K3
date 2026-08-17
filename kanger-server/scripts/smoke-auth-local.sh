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

require_workspace() {
  local response="$1"
  local transaction_level="$2"
  local phase="$3"
  "${PYTHON}" - "${response}" "${transaction_level}" "${phase}" <<'PY'
import json
import sys

response = json.loads(sys.argv[1])
expected_level = int(sys.argv[2])
phase = sys.argv[3]
workspace = response.get("workspace")
if not isinstance(workspace, dict) or workspace.get("schema") != 2:
    raise SystemExit("%s returned no workspace schema 2: %r" % (phase, response))
if "source" in workspace:
    raise SystemExit("%s reintroduced source authority into workspace v2: %r" %
                     (phase, workspace))
storage = workspace.get("storage") or {}
transaction = workspace.get("transaction") or {}
if storage.get("active") is not False:
    raise SystemExit("%s unexpectedly has active storage: %r" % (phase, storage))
if transaction.get("level") != expected_level:
    raise SystemExit("%s returned transaction level %r, expected %r" %
                     (phase, transaction.get("level"), expected_level))
PY
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

printf '%s\n' "[1/13] Reading the public TRUSTED authentication capability snapshot"
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

printf '%s\n' "[2/13] Rejecting e-mail registration in TRUSTED mode"
mail_response="$(post "{\"context\":\"login\",\"parameters\":{\"register\":\"${mail_login}\",\"password\":\"${mail_password}\",\"token\":\"\",\"email\":\"${mail_login}@example.org\",\"privacy\":true}}")"
require_result "${mail_response}" "error" "trusted e-mail registration"
require_code "${mail_response}" "REGISTRATION_DISABLED" "trusted e-mail registration"

printf '%s\n' "[3/13] Verifying rejected e-mail registration created no credential"
mail_login_response="$(post "{\"context\":\"login\",\"parameters\":{\"login\":\"${mail_login}\",\"password\":\"${mail_password}\"}}")"
require_result "${mail_login_response}" "error" "trusted e-mail credential rejection"

printf '%s\n' "[4/13] Rejecting registration without e-mail in TRUSTED mode"
register_response="$(post "{\"context\":\"login\",\"parameters\":{\"register\":\"${login}\",\"password\":\"${password}\",\"token\":\"\",\"privacy\":true}}")"
require_result "${register_response}" "error" "trusted registration"
require_code "${register_response}" "REGISTRATION_DISABLED" "trusted registration"

printf '%s\n' "[5/13] Verifying rejected registration created no credential"
rejected_login="$(post "{\"context\":\"login\",\"parameters\":{\"login\":\"${login}\",\"password\":\"${password}\"}}")"
require_result "${rejected_login}" "error" "trusted credential rejection"

printf '%s\n' "[6/13] Provisioning unconfirmed Server 0.13 credential fixture"
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

printf '%s\n' "[7/13] Logging in with the existing unconfirmed credential"
login_response="$(post "{\"context\":\"login\",\"parameters\":{\"login\":\"${login}\",\"password\":\"${password}\"}}")"
require_result "${login_response}" "OK" "existing-account login"
first_token="$(json_field "${login_response}" token)"
[[ -n "${first_token}" ]] || {
  echo "login returned no session token: ${login_response}" >&2
  exit 1
}

printf '%s\n' "[8/13] Executing authenticated ping with canonical workspace projection"
ping_response="$(post "{\"context\":\"command\",\"parameters\":{\"token\":\"${first_token}\",\"ping\":\"\"}}")"
require_result "${ping_response}" "OK" "first ping"
require_workspace "${ping_response}" 0 "first ping"

printf '%s\n' "[9/13] Logging out and rejecting the closed token"
logout_response="$(post "{\"context\":\"command\",\"parameters\":{\"token\":\"${first_token}\",\"quit\":\"\"}}")"
require_result "${logout_response}" "OK" "first logout"
closed_response="$(post "{\"context\":\"command\",\"parameters\":{\"token\":\"${first_token}\",\"ping\":\"\"}}")"
require_result "${closed_response}" "error" "logged-out token rejection"

printf '%s\n' "[10/13] Logging in again and requiring token rotation"
second_login="$(post "{\"context\":\"login\",\"parameters\":{\"login\":\"${login}\",\"password\":\"${password}\"}}")"
require_result "${second_login}" "OK" "second login"
second_token="$(json_field "${second_login}" token)"
[[ -n "${second_token}" && "${second_token}" != "${first_token}" ]] || {
  echo "login did not rotate the session token: ${second_login}" >&2
  exit 1
}
second_ping="$(post "{\"context\":\"command\",\"parameters\":{\"token\":\"${second_token}\",\"ping\":\"\"}}")"
require_result "${second_ping}" "OK" "second ping"
require_workspace "${second_ping}" 0 "second ping"

printf '%s\n' "[11/13] Executing registry-derived HELP through raw canonical dialogue"
dialogue_help="$(post "{\"context\":\"dialogue\",\"parameters\":{\"token\":\"${second_token}\",\"line\":\"help\"}}")"
require_result "${dialogue_help}" "OK" "canonical dialogue help"
require_workspace "${dialogue_help}" 0 "canonical dialogue help"
printf '%s' "${dialogue_help}" | "${PYTHON}" -c '
import json
import sys
response = json.load(sys.stdin)
description = response.get("description") or ""
if "rule <id>" not in description or "values order <field>" not in description:
    raise SystemExit("canonical help was not registry-derived: %r" % response)
'

printf '%s\n' "[12/13] Mixing canonical and legacy transaction ingress"
ambiguous_transaction="$(post "{\"context\":\"dialogue\",\"parameters\":{\"token\":\"${second_token}\",\"line\":\"t s\"}}")"
require_result "${ambiguous_transaction}" "error" "ambiguous transaction prefix"
require_code "${ambiguous_transaction}" "command_parse_error" "ambiguous transaction prefix"
[[ "$(json_field "${ambiguous_transaction}" reason)" = "AMBIGUOUS_PREFIX" ]] || {
  echo "transaction s did not report AMBIGUOUS_PREFIX: ${ambiguous_transaction}" >&2
  exit 1
}
first_transaction="$(post "{\"context\":\"dialogue\",\"parameters\":{\"token\":\"${second_token}\",\"line\":\"t st\"}}")"
require_result "${first_transaction}" "OK" "canonical transaction start"
require_workspace "${first_transaction}" 1 "canonical transaction start"
second_transaction="$(post "{\"context\":\"query\",\"parameters\":{\"token\":\"${second_token}\",\"transaction\":\"create\"}}")"
require_result "${second_transaction}" "OK" "legacy second transaction create"
[[ "$(json_field "${second_transaction}" transaction)" = "2" ]] || {
  echo "nested transaction depth was not published: ${second_transaction}" >&2
  exit 1
}
require_workspace "${second_transaction}" 2 "legacy second transaction create"

printf '%s\n' "[13/13] Closing the nested session and rejecting its token"
second_logout="$(post "{\"context\":\"command\",\"parameters\":{\"token\":\"${second_token}\",\"quit\":\"\"}}")"
require_result "${second_logout}" "OK" "nested second logout"
second_closed="$(post "{\"context\":\"command\",\"parameters\":{\"token\":\"${second_token}\",\"ping\":\"\"}}")"
require_result "${second_closed}" "error" "nested logged-out token rejection"

printf '%s\n' "TRUSTED policy, canonical dialogue/workspace, nested lifecycle and existing-account authentication smoke passed"

server_jar="${KANGER_SERVER_JAR:-${GITHUB_WORKSPACE:-}/kanger-server/target/kanger-server.jar}"
[[ -f "${server_jar}" ]] || {
  echo "KANGER_SERVER_JAR is required for operator-plane smoke" >&2
  exit 1
}
KANGER_BASE_URL="${BASE_URL}" \
KANGER_SMOKE_HOME="${STATE_HOME}" \
KANGER_SERVER_JAR="${server_jar}" \
  bash "${SCRIPT_DIR}/smoke-admin-local.sh"
