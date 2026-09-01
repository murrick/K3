#!/usr/bin/env bash

KANGER_ROOT="${KANGER_ROOT:-/opt/kanger}"
KANGER_RELEASES_DIR="${KANGER_RELEASES_DIR:-${KANGER_ROOT}/releases}"
KANGER_CURRENT_LINK="${KANGER_CURRENT_LINK:-${KANGER_ROOT}/current}"
KANGER_CONFIG_DIR="${KANGER_CONFIG_DIR:-/etc/kanger}"
KANGER_STATE_DIR="${KANGER_STATE_DIR:-/var/lib/kanger}"
KANGER_UNIT_FILE="${KANGER_UNIT_FILE:-/etc/systemd/system/kanger.service}"
KANGER_NGINX_FILE="${KANGER_NGINX_FILE:-/etc/nginx/conf.d/kanger.conf}"
KANGER_INSTANCE_FILE="${KANGER_INSTANCE_FILE:-${KANGER_CONFIG_DIR}/instance.conf}"
KANGER_SERVER_CONFIG="${KANGER_SERVER_CONFIG:-${KANGER_CONFIG_DIR}/kanger.conf}"
KANGER_HEALTH_URL="http://127.0.0.1:1964/health"
KANGER_READY_URL="http://127.0.0.1:1964/ready"

fail() {
  printf 'ERROR: %s\n' "$*" >&2
  exit 1
}

log() {
  printf '[kanger] %s\n' "$*"
}

require_root() {
  [[ "${EUID}" -eq 0 ]] || fail "Run as root (for example: sudo $0)"
}

require_command() {
  command -v "$1" >/dev/null 2>&1 || fail "Required command not found: $1"
}

release_property() {
  local name="$1"
  awk -F= -v key="${name}" '$1 == key {sub(/^[^=]*=/, ""); print; exit}' "${BUNDLE_ROOT}/RELEASE"
}

validate_bundle_layout() {
  local udf_modules storage_modules core_libraries bootstrap_libraries leaked_udf leaked_storage

  [[ -f "${BUNDLE_ROOT}/RELEASE" ]] || fail "RELEASE is missing from distribution"
  [[ -f "${BUNDLE_ROOT}/SHA256SUMS" ]] || fail "SHA256SUMS is missing from distribution"
  [[ ! -e "${BUNDLE_ROOT}/server/kanger-server.jar" ]] || fail "Compatibility Server JAR must not be present in distribution"
  [[ -f "${BUNDLE_ROOT}/server/kanger-server-thin.jar" ]] || fail "Thin Server JAR is missing from distribution"
  [[ -d "${BUNDLE_ROOT}/server/lib" ]] || fail "Server runtime library directory is missing from distribution"
  [[ -d "${BUNDLE_ROOT}/server/modules" ]] || fail "Server runtime module directory is missing from distribution"
  [[ -f "${BUNDLE_ROOT}/server/kanger.conf.example" ]] || fail "Server configuration template is missing"
  [[ -f "${BUNDLE_ROOT}/ui/index.html" ]] || fail "Browser UI is missing from distribution"
  [[ -x "${BUNDLE_ROOT}/bin/kanger-admin" ]] || fail "KANGER admin launcher is missing or not executable"
  [[ -f "${BUNDLE_ROOT}/systemd/kanger.service.template" ]] || fail "systemd service template is missing"
  [[ -f "${BUNDLE_ROOT}/nginx/kanger.conf.template" ]] || fail "nginx template is missing"

  shopt -s nullglob
  udf_modules=("${BUNDLE_ROOT}"/server/modules/kanger-udf-*.jar)
  storage_modules=("${BUNDLE_ROOT}"/server/modules/kanger-data-dumb-*.jar)
  core_libraries=("${BUNDLE_ROOT}"/server/lib/kanger-core-*.jar)
  bootstrap_libraries=("${BUNDLE_ROOT}"/server/lib/kanger-bootstrap-*.jar)
  leaked_udf=("${BUNDLE_ROOT}"/server/lib/kanger-udf-*.jar)
  leaked_storage=("${BUNDLE_ROOT}"/server/lib/kanger-data-dumb-*.jar)
  shopt -u nullglob

  [[ "${#udf_modules[@]}" -eq 1 ]] || fail "Distribution must contain exactly one UDF runtime module"
  [[ "${#storage_modules[@]}" -eq 1 ]] || fail "Distribution must contain exactly one DUMB storage runtime module"
  [[ "${#core_libraries[@]}" -eq 1 ]] || fail "Distribution must contain exactly one Core runtime library"
  [[ "${#bootstrap_libraries[@]}" -eq 1 ]] || fail "Distribution must contain exactly one bootstrap runtime library"
  [[ "${#leaked_udf[@]}" -eq 0 ]] || fail "UDF provider leaked into server/lib"
  [[ "${#leaked_storage[@]}" -eq 0 ]] || fail "DUMB storage provider leaked into server/lib"

  PRODUCT="$(release_property product)"
  VERSION="$(release_property version)"
  SERVER_VERSION="$(release_property server.version)"
  [[ "${PRODUCT}" == "KANGER" ]] || fail "Unsupported product in RELEASE: ${PRODUCT:-<empty>}"
  [[ "${VERSION}" =~ ^[0-9]+\.[0-9]+\.[0-9]+([.-][A-Za-z0-9._-]+)?$ ]] \
    || fail "Invalid release version: ${VERSION:-<empty>}"
  [[ -n "${SERVER_VERSION}" ]] || fail "RELEASE has no server.version"
}

verify_bundle_checksums() {
  require_command sha256sum
  log "verifying distribution checksums"
  (
    cd "${BUNDLE_ROOT}"
    sha256sum -c SHA256SUMS
  )
}

java_major_version() {
  local version
  version="$(java -version 2>&1 | awk -F\" '/version/ {print $2; exit}')"
  [[ -n "${version}" ]] || fail "Unable to determine Java version"
  if [[ "${version}" == 1.* ]]; then
    printf '%s\n' "${version}" | awk -F. '{print $2}'
  else
    printf '%s\n' "${version}" | awk -F. '{print $1}'
  fi
}

check_runtime_prerequisites() {
  local command major
  for command in java nginx systemctl curl install getent groupadd useradd readlink ln mv cp chown awk grep sed find seq sleep dirname; do
    require_command "${command}"
  done
  major="$(java_major_version)"
  [[ "${major}" =~ ^[0-9]+$ ]] || fail "Unable to parse Java major version: ${major}"
  (( major >= 8 )) || fail "KANGER requires Java 8 or newer; found Java ${major}"
  nginx -v >/dev/null 2>&1 || fail "nginx prerequisite check failed"
  JAVA_BIN="$(readlink -f "$(command -v java)")"
  [[ -x "${JAVA_BIN}" ]] || fail "Resolved Java executable is not executable: ${JAVA_BIN}"
}

validate_domain() {
  local value="$1"
  [[ "${value}" =~ ^[A-Za-z0-9.-]+$ ]] || fail "Invalid domain: ${value}"
  [[ "${value}" == *.* ]] || fail "Domain must be fully qualified: ${value}"
}

validate_path_value() {
  local value="$1"
  [[ "${value}" =~ ^/[A-Za-z0-9._/@:+-]+$ ]] || fail "Unsafe absolute path: ${value}"
}

ensure_service_identity() {
  if ! getent group kanger >/dev/null; then
    groupadd --system kanger
  fi
  if ! getent passwd kanger >/dev/null; then
    useradd --system --gid kanger --home-dir "${KANGER_STATE_DIR}" --shell /usr/sbin/nologin kanger
  fi
}

install_persistent_directories() {
  install -d -o root -g root -m 0755 "${KANGER_ROOT}" "${KANGER_RELEASES_DIR}"
  install -d -o root -g kanger -m 0750 "${KANGER_CONFIG_DIR}"
  install -d -o kanger -g kanger -m 0750 "${KANGER_STATE_DIR}"
}

write_instance_file() {
  umask 077
  {
    printf 'KANGER_UI_DOMAIN=%q\n' "${UI_DOMAIN}"
    printf 'KANGER_API_DOMAIN=%q\n' "${API_DOMAIN}"
    printf 'KANGER_UI_CERT=%q\n' "${UI_CERT}"
    printf 'KANGER_UI_KEY=%q\n' "${UI_KEY}"
    printf 'KANGER_API_CERT=%q\n' "${API_CERT}"
    printf 'KANGER_API_KEY=%q\n' "${API_KEY}"
    printf 'KANGER_NGINX_FILE=%q\n' "${KANGER_NGINX_FILE}"
  } > "${KANGER_INSTANCE_FILE}.new"
  install -o root -g root -m 0600 "${KANGER_INSTANCE_FILE}.new" "${KANGER_INSTANCE_FILE}"
  rm -f "${KANGER_INSTANCE_FILE}.new"
}

load_instance_file() {
  [[ -r "${KANGER_INSTANCE_FILE}" ]] || fail "Installation identity is missing: ${KANGER_INSTANCE_FILE}"
  # File is generated by install.sh, owned by root and mode 0600.
  # shellcheck disable=SC1090
  source "${KANGER_INSTANCE_FILE}"
  validate_domain "${KANGER_UI_DOMAIN}"
  validate_domain "${KANGER_API_DOMAIN}"
  validate_path_value "${KANGER_UI_CERT}"
  validate_path_value "${KANGER_UI_KEY}"
  validate_path_value "${KANGER_API_CERT}"
  validate_path_value "${KANGER_API_KEY}"
  validate_path_value "${KANGER_NGINX_FILE}"
  UI_DOMAIN="${KANGER_UI_DOMAIN}"
  API_DOMAIN="${KANGER_API_DOMAIN}"
  UI_CERT="${KANGER_UI_CERT}"
  UI_KEY="${KANGER_UI_KEY}"
  API_CERT="${KANGER_API_CERT}"
  API_KEY="${KANGER_API_KEY}"
}

validate_tls_material() {
  local file
  for file in "${UI_CERT}" "${UI_KEY}" "${API_CERT}" "${API_KEY}"; do
    [[ -r "${file}" ]] || fail "TLS file is not readable: ${file}"
  done
}

render_with_tokens() {
  local source="$1"
  local target="$2"
  sed \
    -e "s|__JAVA_BIN__|${JAVA_BIN}|g" \
    -e "s|__UI_DOMAIN__|${UI_DOMAIN}|g" \
    -e "s|__API_DOMAIN__|${API_DOMAIN}|g" \
    -e "s|__UI_CERT__|${UI_CERT}|g" \
    -e "s|__UI_KEY__|${UI_KEY}|g" \
    -e "s|__API_CERT__|${API_CERT}|g" \
    -e "s|__API_KEY__|${API_KEY}|g" \
    "${source}" > "${target}"
}

stage_release() {
  local final_dir="$1"
  local stage_dir="${final_dir}.new.$$"
  [[ ! -e "${final_dir}" ]] || fail "Release is already installed: ${final_dir}"
  rm -rf -- "${stage_dir}"

  if ! {
    install -d -o root -g root -m 0755 "${stage_dir}" "${stage_dir}/ui" "${stage_dir}/bin"
    install -d -o root -g kanger -m 0750 \
      "${stage_dir}/server" \
      "${stage_dir}/server/lib" \
      "${stage_dir}/server/modules"
    install -o root -g root -m 0644 "${BUNDLE_ROOT}/RELEASE" "${stage_dir}/RELEASE"
    install -o root -g root -m 0755 "${BUNDLE_ROOT}/bin/kanger-admin" "${stage_dir}/bin/kanger-admin"
    install -o root -g kanger -m 0640 \
      "${BUNDLE_ROOT}/server/kanger-server-thin.jar" \
      "${stage_dir}/server/kanger-server-thin.jar"
    cp -a "${BUNDLE_ROOT}/server/lib/." "${stage_dir}/server/lib/"
    cp -a "${BUNDLE_ROOT}/server/modules/." "${stage_dir}/server/modules/"
    chown -R root:kanger "${stage_dir}/server/lib" "${stage_dir}/server/modules"
    find "${stage_dir}/server/lib" "${stage_dir}/server/modules" -type d -exec chmod 0750 {} +
    find "${stage_dir}/server/lib" "${stage_dir}/server/modules" -type f -exec chmod 0640 {} +
    cp -a "${BUNDLE_ROOT}/ui/." "${stage_dir}/ui/"
    chown -R root:root "${stage_dir}/ui"
    find "${stage_dir}/ui" -type d -exec chmod 0755 {} +
    find "${stage_dir}/ui" -type f -exec chmod 0644 {} +
    cat > "${stage_dir}/ui/config.js" <<UI_CONFIG
(function (window) {
    'use strict';
    window.KANGER_API_HOST = 'https://${API_DOMAIN}';
}(window));
UI_CONFIG
    chown root:root "${stage_dir}/ui/config.js"
    chmod 0644 "${stage_dir}/ui/config.js"
  }; then
    rm -rf -- "${stage_dir}"
    return 1
  fi

  mv "${stage_dir}" "${final_dir}"
}

install_server_config_if_absent() {
  if [[ ! -f "${KANGER_SERVER_CONFIG}" ]]; then
    cp "${BUNDLE_ROOT}/server/kanger.conf.example" "${KANGER_SERVER_CONFIG}.new"
    cat >> "${KANGER_SERVER_CONFIG}.new" <<SERVER_CONFIG

# Installation identity generated by KANGER distribution installer.
server.cors.allowed.origin.1=https://${UI_DOMAIN}
server.url=https://${API_DOMAIN}
server.confirmation.redirect.url=https://${UI_DOMAIN}/
SERVER_CONFIG
    install -o root -g kanger -m 0640 "${KANGER_SERVER_CONFIG}.new" "${KANGER_SERVER_CONFIG}"
    rm -f "${KANGER_SERVER_CONFIG}.new"
  fi
  ln -sfn "${KANGER_SERVER_CONFIG}" "${KANGER_STATE_DIR}/kanger.conf"
  chown -h root:kanger "${KANGER_STATE_DIR}/kanger.conf"
}

install_systemd_unit() {
  local temp="${KANGER_UNIT_FILE}.new.$$"
  install -d -o root -g root -m 0755 "$(dirname "${KANGER_UNIT_FILE}")"
  render_with_tokens "${BUNDLE_ROOT}/systemd/kanger.service.template" "${temp}"
  install -o root -g root -m 0644 "${temp}" "${KANGER_UNIT_FILE}"
  rm -f "${temp}"
  systemctl daemon-reload
}

install_nginx_config() {
  local temp="${KANGER_NGINX_FILE}.new.$$"
  install -d -o root -g root -m 0755 "$(dirname "${KANGER_NGINX_FILE}")"
  render_with_tokens "${BUNDLE_ROOT}/nginx/kanger.conf.template" "${temp}"
  install -o root -g root -m 0644 "${temp}" "${KANGER_NGINX_FILE}"
  rm -f "${temp}"
  nginx -t
}

atomic_current_link() {
  local target="$1"
  local temp="${KANGER_ROOT}/.current.new.$$"
  ln -s "${target}" "${temp}"
  mv -Tf "${temp}" "${KANGER_CURRENT_LINK}"
}

wait_for_server() {
  local attempt health ready
  for attempt in $(seq 1 30); do
    if health="$(curl --fail --silent --show-error --max-time 2 "${KANGER_HEALTH_URL}" 2>/dev/null)" \
      && ready="$(curl --fail --silent --show-error --max-time 2 "${KANGER_READY_URL}" 2>/dev/null)"; then
      printf '%s\n' "${health}" | grep -Eq "\"(server_version|version)\":\"${SERVER_VERSION}\"" \
        || { sleep 1; continue; }
      printf '%s\n' "${ready}" >/dev/null
      return 0
    fi
    sleep 1
  done
  return 1
}

reload_nginx() {
  nginx -t
  nginx -s reload
}
