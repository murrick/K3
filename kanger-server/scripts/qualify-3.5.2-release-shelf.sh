#!/usr/bin/env bash
set -euo pipefail

ROOT="${1:-$(pwd)}"
cd "${ROOT}"

BASE="22918a09ce443e87cf0ee7397ff1b9f1b70f09e8"
STAGE_8="9e2db22ea78de9c8660dd521b12d05a44a35c283"
LIFECYCLE_CORRECTION="99185db7e1effccd810c9e8479bdceca5d61b31a"
CORRECTED_SHELF="03310482cebdf55b34829f3d59bdd197edb6275b"
SERVER_018_CODE="a16ec7abb9b2df1aebbaed921088184f0e571c47"
SERVER_018_DOCS="0213e82023a313641b05ff62d7381da5adc6da09"
SERVER_018_INTEGRATION="b967846832586858d42a5e21091154c682948d00"

for commit in \
  "${BASE}" \
  "${STAGE_8}" \
  "${LIFECYCLE_CORRECTION}" \
  "${CORRECTED_SHELF}" \
  "${SERVER_018_CODE}" \
  "${SERVER_018_DOCS}" \
  "${SERVER_018_INTEGRATION}"; do
  git cat-file -e "${commit}^{commit}"
done

git merge-base --is-ancestor "${BASE}" "${STAGE_8}"
git merge-base --is-ancestor "${STAGE_8}" "${LIFECYCLE_CORRECTION}"
git merge-base --is-ancestor "${LIFECYCLE_CORRECTION}" "${CORRECTED_SHELF}"
git merge-base --is-ancestor "${CORRECTED_SHELF}" "${SERVER_018_CODE}"
git merge-base --is-ancestor "${SERVER_018_CODE}" "${SERVER_018_DOCS}"
git merge-base --is-ancestor "${SERVER_018_DOCS}" "${SERVER_018_INTEGRATION}"
git merge-base --is-ancestor "${SERVER_018_INTEGRATION}" HEAD

echo "RELEASE_SHELF_PASS ancestry"

expected_release_contract_files="$(cat <<'EOF_FILES'
.github/workflows/kanger-3.5.2-release-shelf.yml
3.5.2-closure.md
3.5.2.11-server-0.18.md
REPOSITORY-LIFECYCLE.md
kanger-server/DEPLOYMENT.md
kanger-server/scripts/qualify-3.5.2-release-shelf.sh
release-manifest.yaml
EOF_FILES
)"
actual_release_contract_files="$(
  git diff --name-only "${SERVER_018_INTEGRATION}..HEAD" | sort
)"
test "${actual_release_contract_files}" = "${expected_release_contract_files}"

if git diff --name-only "${SERVER_018_INTEGRATION}..HEAD" \
    | grep -Eq '^(html/|kanger/|kanger-data-dumb/|kanger-udf/|kanger-server/src/|kanger-server/test/|kanger-server/pom.xml$)'; then
  echo "Product or test-code delta detected in release-contract stage" >&2
  exit 1
fi

echo "RELEASE_SHELF_PASS release-contract-only-delta"

grep -q 'artifact: "3.5.2"' release-manifest.yaml
grep -q 'integrated_server_018_commit: "b967846832586858d42a5e21091154c682948d00"' \
  release-manifest.yaml
grep -q 'candidate_qualified_code_commit: "a16ec7abb9b2df1aebbaed921088184f0e571c47"' \
  release-manifest.yaml
grep -q 'candidate_pull_request: 75' release-manifest.yaml
grep -q 'candidate_version: "server-0.18"' release-manifest.yaml
grep -q 'previous_failed_candidate_version: "server-0.17"' release-manifest.yaml
grep -q 'production_version: "server-0.14"' release-manifest.yaml
grep -q 'candidate_qualification: "PASS"' release-manifest.yaml
grep -q 'candidate_integration: "PASS"' release-manifest.yaml
grep -q 'acceptance: "NOT_PERFORMED"' release-manifest.yaml
grep -q 'production_cutover: "NOT_PERFORMED"' release-manifest.yaml

grep -q 'server_version: server-0.18' 3.5.2-closure.md
grep -q 'PR #75:.*merged' 3.5.2-closure.md
grep -q 'integration commit:.*b967846832586858d42a5e21091154c682948d00' \
  3.5.2-closure.md
grep -q 'MERGED / RELEASE CONTRACT QUALIFIED' 3.5.2.11-server-0.18.md
grep -q 'Server 0.17.*immutable failed-soak evidence' \
  3.5.2.11-server-0.18.md

grep -q '<kanger.server.artifact.version>server-0.18</kanger.server.artifact.version>' \
  kanger-server/pom.xml
grep -Fq 'EXPECTED_SERVER_VERSION="${KANGER_EXPECTED_SERVER_VERSION:-server-0.18}"' \
  kanger-server/scripts/smoke-local.sh
grep -q '"server_version":"server-0.18"' \
  kanger-server/deploy/verify-installed.sh
grep -q 'Server 0.18 deployment contract' kanger-server/DEPLOYMENT.md
grep -q 'fresh disposable database' kanger-server/DEPLOYMENT.md
grep -q 'must not be opened, repaired, reindexed or deleted' \
  kanger-server/DEPLOYMENT.md

echo "RELEASE_SHELF_PASS identity-and-record"

expected_browser_files="$(cat <<'EOF_BROWSER'
codemirror.css
codemirror.js
config.js
console.html
containment.js
error.js
favicon.ico
gateway.js
index.html
javascript-mode-vendor.js
javascript-mode.js
javascript.js
jquery-3.6.0.min.js
operation.js
workspace.js
EOF_BROWSER
)"
actual_browser_files="$(
  find html -mindepth 1 -maxdepth 1 -type f -printf '%f\n' | sort
)"
test "${actual_browser_files}" = "${expected_browser_files}"
test "$(git hash-object html/javascript-mode-vendor.js)" = \
  "047395622eb2501dea6fbb9e6be2389e02bf2c77"
grep -q 'inventory_count: 15' release-manifest.yaml
grep -q 'sandbox="allow-scripts"' html/index.html
! grep -q 'allow-same-origin' html/index.html
grep -q '<script src="containment.js"></script>' html/index.html
grep -q 'error.js' html/javascript-mode.js

echo "RELEASE_SHELF_PASS browser-inventory"

node --check html/config.js
node --check html/containment.js
node --check html/error.js
node --check html/gateway.js
node --check html/javascript-mode-vendor.js
node --check html/javascript-mode.js
node --check html/javascript.js
node --check html/operation.js
node --check html/workspace.js
node --check kanger-server/scripts/qualify-gateway-session.js
node --check kanger-server/scripts/qualify-gateway-bootstrap.js
node --check kanger-server/scripts/qualify-trusted-rendering.js
node --check kanger-server/scripts/qualify-operation-snapshot.js
node --check kanger-server/scripts/qualify-workspace-state.js
node --check kanger-server/scripts/qualify-error-containment.js

node kanger-server/scripts/qualify-gateway-session.js
node kanger-server/scripts/qualify-gateway-bootstrap.js
node kanger-server/scripts/qualify-trusted-rendering.js
node kanger-server/scripts/qualify-operation-snapshot.js
node kanger-server/scripts/qualify-workspace-state.js
node kanger-server/scripts/qualify-error-containment.js "${ROOT}"

echo "RELEASE_SHELF_PASS browser-authorities"

test -f kanger-server/DEPLOYMENT-0.16.md
test -f kanger-server/DEPLOYMENT.md
test -f kanger-server/VERSION-CONTRACT.md
grep -q 'Publish the qualified 15-file browser artifact' \
  kanger-server/DEPLOYMENT.md
grep -q 'sandbox="allow-scripts"' kanger-server/DEPLOYMENT.md
grep -q 'server-0.18' kanger-server/VERSION-CONTRACT.md
! grep -q 'allow-same-origin is permitted' kanger-server/DEPLOYMENT.md

echo "RELEASE_SHELF_PASS deployment-contract"
echo "RELEASE_SHELF_OK"
