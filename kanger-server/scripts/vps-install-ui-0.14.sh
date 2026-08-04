#!/usr/bin/env bash
set -Eeuo pipefail
export LC_ALL=C

PACKAGE_ROOT="/tmp/kanger-ui-0.14"
STAGED_UI="${PACKAGE_ROOT}/html"
MANIFEST="${PACKAGE_ROOT}/SHA256SUMS"
PUBLIC_LINK="/var/www/html/kanger"
EXPECTED_INDEX_SHA256="747104f9e9a7599679b5329eb098590b190a876ff949f01e232f67a1e1b8baae"
EXPECTED_CONFIG_SHA256="d44e67c862f12065d62259055043922ff573271dd5fac2f373cc454172757715"
EXPECTED_CONSOLE_SHA256="24d74b6608d4d49a5464d62dd368354c3f30d5f90b90b6a8717531d8e7719dca"
EXPECTED_FILES=(
  codemirror.css
  codemirror.js
  config.js
  console.html
  favicon.ico
  index.html
  javascript.js
  jquery-3.6.0.min.js
)

if [[ "${EUID}" -ne 0 ]]; then
  echo "Run as root: sudo $0" >&2
  exit 1
fi

for command in curl find grep install ln mv nginx readlink sha256sum sort stat; do
  command -v "${command}" >/dev/null 2>&1 || {
    echo "Required command not found: ${command}" >&2
    exit 1
  }
done

[[ -d "${STAGED_UI}" ]] || {
  echo "Staged UI directory is absent: ${STAGED_UI}" >&2
  exit 1
}
[[ -f "${MANIFEST}" ]] || {
  echo "UI SHA-256 manifest is absent: ${MANIFEST}" >&2
  exit 1
}
[[ -L "${PUBLIC_LINK}" ]] || {
  echo "Production UI boundary is not the expected symlink: ${PUBLIC_LINK}" >&2
  exit 1
}

old_target="$(readlink -f "${PUBLIC_LINK}")"
[[ -d "${old_target}" ]] || {
  echo "Current UI target is not a directory: ${old_target}" >&2
  exit 1
}

mapfile -t staged_files < <(
  find "${STAGED_UI}" -mindepth 1 -maxdepth 1 -type f -printf '%f\n' | sort
)
if [[ "${#staged_files[@]}" -ne "${#EXPECTED_FILES[@]}" ]]; then
  echo "Unexpected staged UI file count" >&2
  printf 'staged: %s\n' "${staged_files[@]}" >&2
  exit 1
fi
for index in "${!EXPECTED_FILES[@]}"; do
  [[ "${staged_files[$index]}" == "${EXPECTED_FILES[$index]}" ]] || {
    echo "Unexpected staged UI file set" >&2
    printf 'staged: %s\n' "${staged_files[@]}" >&2
    exit 1
  }
done

(
  cd "${PACKAGE_ROOT}"
  sha256sum -c "$(basename "${MANIFEST}")"
)

assert_sha256() {
  local file="$1"
  local expected="$2"
  local actual
  actual="$(sha256sum "${file}" | awk '{print $1}')"
  [[ "${actual}" == "${expected}" ]] || {
    echo "SHA-256 mismatch for ${file}" >&2
    echo "expected=${expected}" >&2
    echo "actual=${actual}" >&2
    exit 1
  }
}

assert_sha256 "${STAGED_UI}/index.html" "${EXPECTED_INDEX_SHA256}"
assert_sha256 "${STAGED_UI}/config.js" "${EXPECTED_CONFIG_SHA256}"
assert_sha256 "${STAGED_UI}/console.html" "${EXPECTED_CONSOLE_SHA256}"
grep -q '<script src="config.js"></script>' "${STAGED_UI}/index.html"
grep -q 'registration_policy' "${STAGED_UI}/index.html"
grep -q 'public_registration' "${STAGED_UI}/index.html"
grep -q "https://api.kanger.org" "${STAGED_UI}/config.js"

owner="$(stat -c '%U' "${old_target}")"
group="$(stat -c '%G' "${old_target}")"
stamp="$(date -u +%Y%m%dT%H%M%SZ)"
new_target="$(dirname "${old_target}")/kanger-server-0.14-${stamp}"
temp_link="$(dirname "${PUBLIC_LINK}")/.kanger.new.$$"

install -d -o "${owner}" -g "${group}" -m 0755 "${new_target}"
for file in "${EXPECTED_FILES[@]}"; do
  install -o "${owner}" -g "${group}" -m 0644 \
    "${STAGED_UI}/${file}" "${new_target}/${file}"
done

(
  cd "$(dirname "${new_target}")"
  for file in "${EXPECTED_FILES[@]}"; do
    sha256sum "$(basename "${new_target}")/${file}"
  done > "${new_target}/SHA256SUMS.installed"
)
chown "${owner}:${group}" "${new_target}/SHA256SUMS.installed"
chmod 0644 "${new_target}/SHA256SUMS.installed"

nginx -t

rollback() {
  local exit_code=$?
  trap - ERR INT TERM
  set +e
  echo "Server 0.14 UI deployment failed; restoring ${old_target}..." >&2
  rm -f "${temp_link}"
  ln -s "${old_target}" "${temp_link}"
  mv -Tf "${temp_link}" "${PUBLIC_LINK}"
  nginx -t || true
  exit "${exit_code}"
}
trap rollback ERR INT TERM

ln -s "${new_target}" "${temp_link}"
mv -Tf "${temp_link}" "${PUBLIC_LINK}"

origin_index="$(curl --fail --silent --show-error --max-time 10 \
  --resolve kanger.org:443:127.0.0.1 \
  "https://kanger.org/?deployment=${stamp}")"
origin_config="$(curl --fail --silent --show-error --max-time 10 \
  --resolve kanger.org:443:127.0.0.1 \
  "https://kanger.org/config.js?deployment=${stamp}")"
origin_console="$(curl --fail --silent --show-error --max-time 10 \
  --resolve kanger.org:443:127.0.0.1 \
  "https://kanger.org/console.html?deployment=${stamp}")"

grep -q 'registration_policy' <<<"${origin_index}"
grep -q 'public_registration' <<<"${origin_index}"
grep -q 'https://api.kanger.org' <<<"${origin_config}"
grep -q 'KANGER' <<<"${origin_console}"

public_index="$(curl --fail --silent --show-error --max-time 15 \
  "https://kanger.org/?deployment=${stamp}")"
public_config="$(curl --fail --silent --show-error --max-time 15 \
  "https://kanger.org/config.js?deployment=${stamp}")"
grep -q 'registration_policy' <<<"${public_index}"
grep -q 'https://api.kanger.org' <<<"${public_config}"

trap - ERR INT TERM

echo
echo "KANGER Server 0.14 browser UI cutover complete"
echo "UI_DEPLOYMENT_GATE=PASS"
echo "PREVIOUS_UI_TARGET=${old_target}"
echo "CURRENT_UI_TARGET=$(readlink -f "${PUBLIC_LINK}")"
echo "INDEX_SHA256=$(sha256sum "${new_target}/index.html" | awk '{print $1}')"
echo "CONFIG_SHA256=$(sha256sum "${new_target}/config.js" | awk '{print $1}')"
echo "CONSOLE_SHA256=$(sha256sum "${new_target}/console.html" | awk '{print $1}')"
echo "ORIGIN_UI=PASS"
echo "PUBLIC_UI=PASS"
