#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
DEPLOYER="${SCRIPT_DIR}/../deploy/kanger-deploy.sh"
TEST_ROOT="$(mktemp -d)"
trap 'rm -rf "${TEST_ROOT}"' EXIT

ORIGIN="${TEST_ROOT}/origin"
CHECKOUT="${TEST_ROOT}/checkout"
FAKE_BIN="${TEST_ROOT}/bin"
CALL_LOG="${TEST_ROOT}/calls.log"
EXISTING_FLAG="${TEST_ROOT}/existing.flag"
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
git -C "${ORIGIN}" config user.name "KANGER Deploy Test"
git -C "${ORIGIN}" add .
git -C "${ORIGIN}" commit -qm initial
git -C "${ORIGIN}" branch -M develop/server/0.12

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
date=2026-08-04_08:00:00
PROPERTIES
EOF

cat > "${FAKE_BIN}/ssh" <<EOF
#!/usr/bin/env bash
echo "ssh \$*" >> "${CALL_LOG}"
case "\$*" in
  *'systemctl is-active --quiet kanger-server.service'*)
    if [[ -f "${EXISTING_FLAG}" ]]; then
      printf installed
    else
      printf clean
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

bash -n "${DEPLOYER}"
bash "${DEPLOYER}" --help | grep -q 'first KANGER Server application deployment'
bash "${DEPLOYER}" --dry-run --target test@example.invalid \
  | grep -q 'no repository, build, SSH or HTTP action was performed'

if bash "${DEPLOYER}" --dry-run --ref 'unsafe ref' >/dev/null 2>&1; then
  echo "Unsafe Git ref was accepted" >&2
  exit 1
fi

PATH="${FAKE_BIN}:${PATH}" bash "${DEPLOYER}" \
  --repo-url "${ORIGIN}" \
  --checkout "${CHECKOUT}" \
  --target test@example.invalid \
  --port 4211 \
  > "${TEST_ROOT}/deploy.out"

grep -q 'KANGER Server deployment completed' "${TEST_ROOT}/deploy.out"
grep -q 'operation: first installation' "${TEST_ROOT}/deploy.out"
grep -q 'install.sh' "${CALL_LOG}"
grep -q 'verify-installed.sh' "${CALL_LOG}"
grep -q 'https://api.kanger.org/health' "${CALL_LOG}"
grep -q 'https://api.kanger.org/ready' "${CALL_LOG}"
grep -q 'deployment.properties' "${CALL_LOG}"

touch "${EXISTING_FLAG}"
: > "${CALL_LOG}"

if PATH="${FAKE_BIN}:${PATH}" bash "${DEPLOYER}" \
    --repo-url "${ORIGIN}" \
    --checkout "${CHECKOUT}" \
    --target test@example.invalid \
    --port 4211 \
    >"${TEST_ROOT}/existing.out" 2>&1; then
  echo "Existing installation was not rejected" >&2
  exit 1
fi
grep -q 'use kanger-update.sh' "${TEST_ROOT}/existing.out"
if grep -q '^mvn ' "${CALL_LOG}"; then
  echo "Existing-install guard ran Maven" >&2
  exit 1
fi

: > "${CALL_LOG}"
PATH="${FAKE_BIN}:${PATH}" bash "${DEPLOYER}" \
  --force \
  --repo-url "${ORIGIN}" \
  --checkout "${CHECKOUT}" \
  --target test@example.invalid \
  --port 4211 \
  > "${TEST_ROOT}/force.out"
grep -q 'operation: forced redeployment' "${TEST_ROOT}/force.out"
grep -q 'install.sh' "${CALL_LOG}"

echo "KANGER deploy orchestrator tests passed."
