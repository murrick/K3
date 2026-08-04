#!/usr/bin/env bash
set -euo pipefail

BASE_URL="${KANGER_BASE_URL:-http://127.0.0.1:1964}"
HEADERS="$(mktemp)"
trap 'rm -f "${HEADERS}"' EXIT

EXPECTED_CORE_VERSION="${KANGER_EXPECTED_CORE_VERSION:-3.3}"
EXPECTED_API_VERSION="${KANGER_EXPECTED_API_VERSION:-1}"
EXPECTED_SERVER_VERSION="${KANGER_EXPECTED_SERVER_VERSION:-server-0.13}"

assert_version_identity() {
  local response="$1"
  echo "${response}" | grep -q "\"version\":\"${EXPECTED_CORE_VERSION}\""
  echo "${response}" | grep -q "\"core_version\":\"${EXPECTED_CORE_VERSION}\""
  echo "${response}" | grep -q "\"api_version\":\"${EXPECTED_API_VERSION}\""
  echo "${response}" | grep -q "\"server_version\":\"${EXPECTED_SERVER_VERSION}\""
}

echo "Checking ${BASE_URL}/health"
HEALTH="$(curl --fail --silent --show-error \
  --dump-header "${HEADERS}" \
  "${BASE_URL}/health")"
echo "${HEALTH}"
echo "${HEALTH}" | grep -q '"status":"UP"'
assert_version_identity "${HEALTH}"
grep -qi '^X-Request-ID: kanger-' "${HEADERS}"

echo "Checking ${BASE_URL}/ready"
READY_ID="smoke-ready-$$"
READY="$(curl --fail --silent --show-error \
  --dump-header "${HEADERS}" \
  --header "X-Request-ID: ${READY_ID}" \
  "${BASE_URL}/ready")"
echo "${READY}"
echo "${READY}" | grep -q '"status":"READY"'
echo "${READY}" | grep -q '"queue_capacity"'
assert_version_identity "${READY}"
grep -qi "^X-Request-ID: ${READY_ID}" "${HEADERS}"

echo "Checking ${BASE_URL}/version"
VERSION="$(curl --fail --silent --show-error "${BASE_URL}/version")"
echo "${VERSION}"
echo "${VERSION}" | grep -q '"result":"OK"'
assert_version_identity "${VERSION}"

echo "KANGER Server local smoke test passed."
