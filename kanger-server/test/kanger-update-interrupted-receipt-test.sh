#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
UPDATER="${SCRIPT_DIR}/../deploy/kanger-update.sh"
TEST_ROOT="$(mktemp -d)"
trap 'rm -rf "${TEST_ROOT}"' EXIT

ORIGIN="${TEST_ROOT}/origin"
CHECKOUT="${TEST_ROOT}/checkout"
FAKE_BIN="${TEST_ROOT}/bin"
CALL_LOG="${TEST_ROOT}/calls.log"
REMOTE_SHA_FILE="${TEST_ROOT}/remote.sha"
mkdir -p "${ORIGIN}/kanger-server/deploy" "${FAKE_BIN}"

cat > "${ORIGIN}/kanger-server/pom.xml" <<'EOF'
<project></project>
EOF
cat > "${ORIGIN}/kanger-server/deploy/install.sh" <<'EOF'
#!/usr/bin/env bash
exit 0
EOF
cat > "${ORIGIN}/kanger-server/deploy/verify-installed.sh" <<'EOF'
#!/usr/bin/env bash
exit 0
EOF
chmod +x "${ORIGIN}/kanger-server/deploy/"*.sh

git -C "${ORIGIN}" init -q
git -C "${ORIGIN}" config user.email test@example.invalid
git -C "${ORIGIN}" config user.name "KANGER Receipt Recovery Test"
git -C "${ORIGIN}" add .
git -C "${ORIGIN}" commit -qm initial
git -C "${ORIGIN}" branch -M develop/server/0.12

cat > "${FAKE_BIN}/mvn" <<EOF
#!/usr/bin/env bash
echo "mvn \$*" >> "${CALL_LOG}"
mkdir -p "${CHECKOUT}/kanger-server/target"
printf fake-qualified-jar > "${CHECKOUT}/kanger-server/target/kanger-server.jar"
EOF

cat > "${FAKE_BIN}/jar" <<'EOF'
#!/usr/bin/env bash
echo org/kanger/Kanger.class
EOF

cat > "${FAKE_BIN}/unzip" <<'EOF'
#!/usr/bin/env bash
cat <<'PROPERTIES'
branch=server-0.12
source.branch=develop/server/0.12
server.version=server-0.12
date=2026-08-04_08:20:00
PROPERTIES
EOF

cat > "${FAKE_BIN}/ssh" <<EOF
#!/usr/bin/env bash
echo "ssh \$*" >> "${CALL_LOG}"
case "\$*" in
  *'/opt/kanger-server/kanger-server.jar'*)
    if [[ -s "${REMOTE_SHA_FILE}" ]]; then
      cat "${REMOTE_SHA_FILE}"
    fi
    ;;
esac
exit 0
EOF

cat > "${FAKE_BIN}/scp" <<EOF
#!/usr/bin/env bash
echo "scp \$*" >> "${CALL_LOG}"
exit 0
EOF

cat > "${FAKE_BIN}/curl" <<EOF
#!/usr/bin/env bash
echo "curl \$*" >> "${CALL_LOG}"
case "\$*" in
  *'/ready'*) printf 403 ;;
  *'https://kanger.org'*)
    echo 'curl: (28) Operation timed out after 10001 milliseconds with 19139 bytes received' >&2
    exit 28
    ;;
  *'/health'*) printf '{"result":"OK","status":"UP","version":"server-0.12"}' ;;
  *) exit 0 ;;
esac
EOF

chmod +x "${FAKE_BIN}/"*

PATH="${FAKE_BIN}:${PATH}" bash "${UPDATER}" \
  --repo-url "${ORIGIN}" \
  --checkout "${CHECKOUT}" \
  --target test@example.invalid \
  --port 4211 \
  > "${TEST_ROOT}/installed.out"

grep -q 'operation: installed' "${TEST_ROOT}/installed.out"
grep -q 'WARNING: public UI check did not complete' "${TEST_ROOT}/installed.out"
grep -q 'KANGER Server update completed' "${TEST_ROOT}/installed.out"
grep -q 'deployment.properties' "${CALL_LOG}"

grep -n 'deployment.properties' "${CALL_LOG}" | head -1 | cut -d: -f1 \
  > "${TEST_ROOT}/receipt-line"
grep -n 'https://kanger.org' "${CALL_LOG}" | head -1 | cut -d: -f1 \
  > "${TEST_ROOT}/ui-line"
[[ "$(cat "${TEST_ROOT}/receipt-line")" -lt "$(cat "${TEST_ROOT}/ui-line")" ]] \
  || { echo "Receipt was not written before advisory UI check" >&2; exit 1; }

sha256sum "${CHECKOUT}/kanger-server/target/kanger-server.jar" \
  | awk '{print $1}' > "${REMOTE_SHA_FILE}"

cat > "${FAKE_BIN}/mvn" <<EOF
#!/usr/bin/env bash
echo unexpected-mvn >> "${CALL_LOG}"
exit 99
EOF
chmod +x "${FAKE_BIN}/mvn"
: > "${CALL_LOG}"

PATH="${FAKE_BIN}:${PATH}" bash "${UPDATER}" \
  --repo-url "${ORIGIN}" \
  --checkout "${CHECKOUT}" \
  --target test@example.invalid \
  --port 4211 \
  > "${TEST_ROOT}/recovery.out"

grep -q 'recovering deployment receipt from the previously qualified installed JAR' \
  "${TEST_ROOT}/recovery.out"
grep -q 'operation: receipt recovery (qualified JAR already installed)' \
  "${TEST_ROOT}/recovery.out"
grep -q 'WARNING: public UI check did not complete' \
  "${TEST_ROOT}/recovery.out"
if grep -q 'unexpected-mvn\|install.sh' "${CALL_LOG}"; then
  echo "Receipt recovery unexpectedly rebuilt or reinstalled the server" >&2
  exit 1
fi
grep -q 'deployment.properties' "${CALL_LOG}"

echo "KANGER interrupted-receipt recovery tests passed."
