#!/usr/bin/env bash
set -euo pipefail

BASE_URL="${KANGER_BASE_URL:-http://127.0.0.1:1964}"

echo "Checking ${BASE_URL}/health"
HEALTH="$(curl --fail --silent --show-error "${BASE_URL}/health")"
echo "${HEALTH}"
echo "${HEALTH}" | grep -q '"status":"UP"'

echo "Checking ${BASE_URL}/version"
VERSION="$(curl --fail --silent --show-error "${BASE_URL}/version")"
echo "${VERSION}"
echo "${VERSION}" | grep -q '"result":"OK"'

echo "KANGER Server local smoke test passed."
