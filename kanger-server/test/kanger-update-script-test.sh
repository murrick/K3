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
REMOTE_RECEIPT_FILE="${TEST_ROOT}/remote-receipt.properties"
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
git -C "${ORIGIN}" config user.name "KANGER Update Test"
git -C "${ORIGIN}" add .
git -C "${ORIGIN}" commit -qm initial
git -C "${ORIGIN}" branch -M develop/server/0.12
SOURCE_COMMIT="$(git -C "${ORIGIN}" rev-parse HEAD)"

cat > "${FAKE_BIN}/mvn" <<EOF
#!/usr/bin/env bash
echo "mvn \$*" >> "${CALL_LOG}"
mkdir -p "${CHECKOUT}/kanger-server/target"
printf fake > "${CHECKOUT}/kanger-server/target/kanger-server.jar"
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
date=2026-08-04_07:00:00
PROPERTIES
EOF

cat > "${FAKE_BIN}/ssh" <<EOF
#!/usr/bin/env bash
echo "ssh \$*" >> "${CALL_LOG}"
case "\$*" in
  *"cat '/opt/kanger-server/deployment.properties'"*)
    if [[ -s "${REMOTE_RECEIPT_FILE}" ]]; then
      cat "${REMOTE_RECEIPT_FILE}"
    fi
    ;;
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
  *'https://kanger.org'*) printf 200 ;;
  *'/health'*) printf '{"result":"OK","status":"UP","version":"server-0.12"}' ;;
  *) exit 0 ;;
esac
EOF

chmod +x "${FAKE_BIN}/"*

bash -n "${UPDATER}"
bash "${UPDATER}" --help | grep -q 'stable shelf branch develop/server/0.12'
bash "${UPDATER}" --dry-run --target test@example.invalid \
  | grep -q 'no repository, build, SSH or HTTP action was performed'

if bash "${UPDATER}" --dry-run --ref 'unsafe ref' >/dev/null 2>&1; then
  echo "Unsafe Git ref was accepted" >&2
  exit 1
fi

PATH="${FAKE_BIN}:${PATH}" bash "${UPDATER}" \
  --repo-url "${ORIGIN}" \
  --checkout "${CHECKOUT}" \
  --target test@example.invalid \
  --port 4211 \
  > "${TEST_ROOT}/deploy.out"

grep -q 'operation: installed' "${TEST_ROOT}/deploy.out"
grep -q 'install.sh' "${CALL_LOG}"
grep -q 'flock -n' "${CALL_LOG}"
grep -q 'verify-installed.sh' "${CALL_LOG}"
grep -q 'deployment.properties' "${CALL_LOG}"
grep -q 'https://api.kanger.org/health' "${CALL_LOG}"
grep -q 'https://api.kanger.org/ready' "${CALL_LOG}"

JAR_SHA="$(sha256sum "${CHECKOUT}/kanger-server/target/kanger-server.jar" | awk '{print $1}')"
cat > "${REMOTE_RECEIPT_FILE}" <<EOF
artifact.version=server-0.12
source.ref=develop/server/0.12
source.commit=${SOURCE_COMMIT}
jar.sha256=${JAR_SHA}
build.date=2026-08-04_07:00:00
deployed.at=2026-08-04T07:00:00Z
EOF
: > "${CALL_LOG}"

cat > "${FAKE_BIN}/mvn" <<EOF
#!/usr/bin/env bash
echo unexpected-mvn >> "${CALL_LOG}"
exit 99
EOF
chmod +x "${FAKE_BIN}/mvn"

PATH="${FAKE_BIN}:${PATH}" bash "${UPDATER}" \
  --repo-url "${ORIGIN}" \
  --checkout "${CHECKOUT}" \
  --target test@example.invalid \
  --port 4211 \
  > "${TEST_ROOT}/noop.out"

grep -q 'source commit is already deployed; skipping build and restart' \
  "${TEST_ROOT}/noop.out"
grep -q 'operation: no-op (source commit already deployed)' \
  "${TEST_ROOT}/noop.out"
if grep -q 'unexpected-mvn\|install.sh' "${CALL_LOG}"; then
  echo "Source-commit no-op unexpectedly built or installed the server" >&2
  exit 1
fi
grep -q 'verify-installed.sh' "${CALL_LOG}"

echo "KANGER update orchestrator tests passed."
