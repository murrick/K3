#!/usr/bin/env bash
set -euo pipefail

ROOT="${1:-$(pwd)}"
cd "${ROOT}"

BASE="22918a09ce443e87cf0ee7397ff1b9f1b70f09e8"
STAGE_2="f5c2a76cd757c9e4179d3f90abc02b99f38fdba8"
STAGE_3="c5fa90d41e6577bb83bef4db403b9bec7c4cfb2e"
STAGE_4="f011bf378cc3f29f7a13b422810c92708efe85e5"
STAGE_5="8c62a1c3fcf7fb68f93c9c46fafc429b389f8cea"
STAGE_6="8fc389f41459b9f996df2dc194c842fba388e5b2"
STAGE_7="42216f0b14099c6069fec28b34b01282608ebeaa"
STAGE_8="9e2db22ea78de9c8660dd521b12d05a44a35c283"

CHAIN=(
  "${BASE}"
  "${STAGE_2}"
  "${STAGE_3}"
  "${STAGE_4}"
  "${STAGE_5}"
  "${STAGE_6}"
  "${STAGE_7}"
  "${STAGE_8}"
)

for commit in "${CHAIN[@]}"; do
  git cat-file -e "${commit}^{commit}"
done

for ((index = 0; index < ${#CHAIN[@]} - 1; index++)); do
  git merge-base --is-ancestor "${CHAIN[index]}" "${CHAIN[index + 1]}"
done

git merge-base --is-ancestor "${STAGE_8}" HEAD

test "$(git merge-base "${BASE}" "${STAGE_8}")" = "${BASE}"
test "$(git rev-list --count "${BASE}..${STAGE_8}")" = "57"

echo "RELEASE_SHELF_PASS ancestry"

expected_integration_files="$(cat <<'EOF_FILES'
.github/workflows/kanger-3.5.2-release-shelf.yml
3.5.2-closure.md
REPOSITORY-LIFECYCLE.md
kanger-server/scripts/qualify-3.5.2-release-shelf.sh
release-manifest.yaml
EOF_FILES
)"
actual_integration_files="$(git diff --name-only "${STAGE_8}..HEAD" | sort)"
test "${actual_integration_files}" = "${expected_integration_files}"

if git diff --name-only "${STAGE_8}..HEAD" \
    | grep -Eq '^(html/|kanger/|kanger-data-dumb/|kanger-udf/|kanger-server/src/|kanger-server/pom.xml$)'; then
  echo "Product-code delta detected after 3.5.2.8" >&2
  exit 1
fi

echo "RELEASE_SHELF_PASS integration-only-delta"

grep -q 'artifact: "3.5.2"' release-manifest.yaml
grep -q 'baseline_commit: "22918a09ce443e87cf0ee7397ff1b9f1b70f09e8"' release-manifest.yaml
grep -q 'implementation_checkpoint: "9e2db22ea78de9c8660dd521b12d05a44a35c283"' release-manifest.yaml
grep -q 'integration_pull_request: 72' release-manifest.yaml
grep -q 'candidate_version: "server-0.17"' release-manifest.yaml
grep -q 'production_version: "server-0.14"' release-manifest.yaml
grep -q 'integration_qualification: "PASS"' release-manifest.yaml
grep -q 'acceptance: "NOT_PERFORMED"' release-manifest.yaml
grep -q 'production_cutover: "NOT_PERFORMED"' release-manifest.yaml
grep -q 'server_version: server-0.17' 3.5.2-closure.md
grep -q 'integration qualification: PASS' 3.5.2-closure.md
grep -q 'production remains:       release/3.5.1 + server-0.14' 3.5.2-closure.md
grep -q 'Qualification does not itself authorize merge' REPOSITORY-LIFECYCLE.md

grep -q '<kanger.server.artifact.version>server-0.17</kanger.server.artifact.version>' \
  kanger-server/pom.xml
grep -Fq 'EXPECTED_SERVER_VERSION="${KANGER_EXPECTED_SERVER_VERSION:-server-0.17}"' \
  kanger-server/scripts/smoke-local.sh
grep -q '"server_version":"server-0.17"' \
  kanger-server/deploy/verify-installed.sh

echo "RELEASE_SHELF_PASS identity"

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
grep -q 'server-0.17' kanger-server/VERSION-CONTRACT.md
! grep -q 'allow-same-origin is permitted' kanger-server/DEPLOYMENT.md

echo "RELEASE_SHELF_PASS deployment-contract"
echo "RELEASE_SHELF_OK"
