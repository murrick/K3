#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
SERVER_DIR="$(cd "${SCRIPT_DIR}/.." && pwd)"
RUN_DIR="${KANGER_LOCAL_RUN_DIR:-${SERVER_DIR}/run/local}"
HOME_DIR="${RUN_DIR}/home"
CONF_FILE="${HOME_DIR}/kanger.conf"
JAR_FILE="${SERVER_DIR}/target/kanger-server.jar"

mkdir -p "${HOME_DIR}"

if [[ ! -f "${CONF_FILE}" ]]; then
  cp "${SERVER_DIR}/config/kanger.local.conf.example" "${CONF_FILE}"
  echo "Created local configuration: ${CONF_FILE}"
fi

if [[ ! -f "${JAR_FILE}" || "${KANGER_LOCAL_REBUILD:-1}" == "1" ]]; then
  mvn -B -ntp \
    -f "${SERVER_DIR}/pom.xml" \
    -Dkanger.build.branch.override="local" \
    clean verify
fi

cleanup() {
  rm -f "${HOME_DIR}/KANGER/kanger.active"
}

shutdown() {
  if [[ -n "${SERVER_PID:-}" ]] && kill -0 "${SERVER_PID}" 2>/dev/null; then
    kill "${SERVER_PID}" 2>/dev/null || true
    wait "${SERVER_PID}" 2>/dev/null || true
  fi
  cleanup
}

trap shutdown INT TERM EXIT

echo "KANGER Server local sandbox: ${RUN_DIR}"
echo "Configuration:               ${CONF_FILE}"
echo "Health:                      http://127.0.0.1:1964/health"
echo "Version:                     http://127.0.0.1:1964/version"
echo "Stop with Ctrl+C"

java -Duser.home="${HOME_DIR}" -jar "${JAR_FILE}" &
SERVER_PID=$!
wait "${SERVER_PID}"
STATUS=$?
SERVER_PID=""
cleanup
trap - INT TERM EXIT
exit "${STATUS}"
