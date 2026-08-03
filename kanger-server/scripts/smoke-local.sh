#!/usr/bin/env bash
set -euo pipefail

BASE_URL="${KANGER_BASE_URL:-http://127.0.0.1:1964}"
HEADERS="$(mktemp)"
trap 'rm -f "${HEADERS}"' EXIT

echo "Checking ${BASE_URL}/health"
HEALTH="$(curl --fail --silent --show-error \
  --dump-header "${HEADERS}" \
  "${BASE_URL}/health")"
echo "${HEALTH}"
echo "${HEALTH}" | grep -q '"status":"UP"'
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
grep -qi "^X-Request-ID: ${READY_ID}" "${HEADERS}"

echo "Checking ${BASE_URL}/version"
VERSION="$(curl --fail --silent --show-error "${BASE_URL}/version")"
echo "${VERSION}"
echo "${VERSION}" | grep -q '"result":"OK"'

echo "KANGER Server local smoke test passed."
