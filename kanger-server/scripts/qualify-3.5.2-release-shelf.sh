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
RELEASE_CONTRACT_012="b0ed1cee70d6a4bbaf3b7690df766b9eae41f891"
CONSOLE_SHUTDOWN_QUALIFIED="df738ca6657fcc1fa15619e1d2b3cccd4e51b397"
CONSOLE_SHUTDOWN_INTEGRATION="ddbf5ab380b4124013f58bbd655a2131ccba536b"
POST_SHUTDOWN_RELEASE_CONTRACT="f9419c9428424b958bd938db0f6cf29650acf3f0"
BASELINE_INSERTION_QUALIFIED="6b4b8e51c8ab4023cb5e81c2b2d9ec9ad9d5cdc3"
BASELINE_INSERTION_DOCS="d894e6e3d17a3c7bfb6c7a5c110664f838c489bf"
BASELINE_INSERTION_INTEGRATION="a70dd388576882aa4cf827a31b3f4724ac339b16"
POST_BASELINE_RELEASE_CONTRACT="307042411124ae181e19aea70b50ca7dff6d72a1"
VPS_SOAK_CLOSURE_QUALIFIED="4f47994f61f68bf163179b8cd8eacbb1062633dd"

require_pattern() {
  local pattern="$1"
  local file="$2"
  grep -q -- "${pattern}" "${file}" || {
    echo "Missing release-contract marker in ${file}: ${pattern}" >&2
    exit 1
  }
}

require_fixed() {
  local pattern="$1"
  local file="$2"
  grep -Fq -- "${pattern}" "${file}" || {
    echo "Missing fixed release-contract marker in ${file}: ${pattern}" >&2
    exit 1
  }
}

for commit in \
  "${BASE}" \
  "${STAGE_8}" \
  "${LIFECYCLE_CORRECTION}" \
  "${CORRECTED_SHELF}" \
  "${SERVER_018_CODE}" \
  "${SERVER_018_DOCS}" \
  "${SERVER_018_INTEGRATION}" \
  "${RELEASE_CONTRACT_012}" \
  "${CONSOLE_SHUTDOWN_QUALIFIED}" \
  "${CONSOLE_SHUTDOWN_INTEGRATION}" \
  "${POST_SHUTDOWN_RELEASE_CONTRACT}" \
  "${BASELINE_INSERTION_QUALIFIED}" \
  "${BASELINE_INSERTION_DOCS}" \
  "${BASELINE_INSERTION_INTEGRATION}" \
  "${POST_BASELINE_RELEASE_CONTRACT}" \
  "${VPS_SOAK_CLOSURE_QUALIFIED}"; do
  git cat-file -e "${commit}^{commit}"
done

git merge-base --is-ancestor "${BASE}" "${STAGE_8}"
git merge-base --is-ancestor "${STAGE_8}" "${LIFECYCLE_CORRECTION}"
git merge-base --is-ancestor "${LIFECYCLE_CORRECTION}" "${CORRECTED_SHELF}"
git merge-base --is-ancestor "${CORRECTED_SHELF}" "${SERVER_018_CODE}"
git merge-base --is-ancestor "${SERVER_018_CODE}" "${SERVER_018_DOCS}"
git merge-base --is-ancestor "${SERVER_018_DOCS}" "${SERVER_018_INTEGRATION}"
git merge-base --is-ancestor "${SERVER_018_INTEGRATION}" "${RELEASE_CONTRACT_012}"
git merge-base --is-ancestor "${RELEASE_CONTRACT_012}" "${CONSOLE_SHUTDOWN_QUALIFIED}"
git merge-base --is-ancestor "${CONSOLE_SHUTDOWN_QUALIFIED}" "${CONSOLE_SHUTDOWN_INTEGRATION}"
git merge-base --is-ancestor "${CONSOLE_SHUTDOWN_INTEGRATION}" "${POST_SHUTDOWN_RELEASE_CONTRACT}"
git merge-base --is-ancestor "${POST_SHUTDOWN_RELEASE_CONTRACT}" "${BASELINE_INSERTION_QUALIFIED}"
git merge-base --is-ancestor "${BASELINE_INSERTION_QUALIFIED}" "${BASELINE_INSERTION_DOCS}"
git merge-base --is-ancestor "${BASELINE_INSERTION_DOCS}" "${BASELINE_INSERTION_INTEGRATION}"
git merge-base --is-ancestor "${BASELINE_INSERTION_INTEGRATION}" "${POST_BASELINE_RELEASE_CONTRACT}"
git merge-base --is-ancestor "${POST_BASELINE_RELEASE_CONTRACT}" "${VPS_SOAK_CLOSURE_QUALIFIED}"
git merge-base --is-ancestor "${VPS_SOAK_CLOSURE_QUALIFIED}" HEAD

echo "RELEASE_SHELF_PASS ancestry"

expected_stage_15_files="$(cat <<'EOF_FILES'
docs/qualification/3.5.2.15-storage-baseline-insertion.md
kanger-console/src/org/kanger/Console.java
kanger-qualification/src/org/kanger/KangerConsoleLifecycleBindingRunner.java
kanger/src/org/kanger/Mind.java
kanger/src/org/kanger/User.java
EOF_FILES
)"
actual_stage_15_files="$(git diff --name-only "${POST_SHUTDOWN_RELEASE_CONTRACT}..${BASELINE_INSERTION_INTEGRATION}" | sort)"
test "${actual_stage_15_files}" = "${expected_stage_15_files}" || {
  echo "Unexpected 3.5.2.15 file set" >&2
  printf 'Expected:\n%s\nActual:\n%s\n' "${expected_stage_15_files}" "${actual_stage_15_files}" >&2
  exit 1
}

if git diff --name-only "${POST_SHUTDOWN_RELEASE_CONTRACT}..${BASELINE_INSERTION_INTEGRATION}" \
    | grep -Eq '^(html/|kanger-data-dumb/|kanger-udf/|kanger-server/src/|kanger-server/test/|kanger-server/pom.xml$)'; then
  echo "Unexpected Server/Browser/DB/UDF product delta in baseline-insertion stage" >&2
  exit 1
fi

echo "RELEASE_SHELF_PASS baseline-insertion-stage"

expected_stage_16_files="$(cat <<'EOF_FILES'
3.5.2-closure.md
REPOSITORY-LIFECYCLE.md
docs/qualification/3.5.2.16-post-baseline-insertion-release-contract.md
kanger-server/DEPLOYMENT.md
kanger-server/scripts/qualify-3.5.2-release-shelf.sh
release-manifest.yaml
EOF_FILES
)"
actual_stage_16_files="$(git diff --name-only "${BASELINE_INSERTION_INTEGRATION}..${POST_BASELINE_RELEASE_CONTRACT}" | sort)"
test "${actual_stage_16_files}" = "${expected_stage_16_files}" || {
  echo "Unexpected 3.5.2.16 release-contract file set" >&2
  printf 'Expected:\n%s\nActual:\n%s\n' "${expected_stage_16_files}" "${actual_stage_16_files}" >&2
  exit 1
}

if git diff --name-only "${BASELINE_INSERTION_INTEGRATION}..${POST_BASELINE_RELEASE_CONTRACT}" \
    | grep -Eq '^(html/|kanger/|kanger-console/src/|kanger-data-dumb/|kanger-qualification/src/|kanger-udf/|kanger-server/src/|kanger-server/test/|kanger-server/pom.xml$)'; then
  echo "Product or qualification-code delta detected in 3.5.2.16 release-contract stage" >&2
  exit 1
fi

echo "RELEASE_SHELF_PASS post-baseline-insertion-release-contract-only-delta"

expected_stage_17_files="$(cat <<'EOF_FILES'
3.5.2-closure.md
docs/qualification/3.5.2.17-vps-soak-closure.md
kanger-server/scripts/qualify-3.5.2-release-shelf.sh
release-manifest.yaml
EOF_FILES
)"
actual_stage_17_files="$(git diff --name-only "${POST_BASELINE_RELEASE_CONTRACT}..HEAD" | sort)"
test "${actual_stage_17_files}" = "${expected_stage_17_files}" || {
  echo "Unexpected 3.5.2.17 VPS-soak-closure file set" >&2
  printf 'Expected:\n%s\nActual:\n%s\n' "${expected_stage_17_files}" "${actual_stage_17_files}" >&2
  exit 1
}

if git diff --name-only "${POST_BASELINE_RELEASE_CONTRACT}..HEAD" \
    | grep -Eq '^(html/|kanger/|kanger-console/src/|kanger-data-dumb/|kanger-qualification/src/|kanger-udf/|kanger-server/src/|kanger-server/test/|kanger-server/pom.xml$)'; then
  echo "Product/test/runtime delta detected in 3.5.2.17 VPS-soak closure" >&2
  exit 1
fi

echo "RELEASE_SHELF_PASS vps-soak-closure-only-delta"

require_pattern 'artifact: "3.5.2"' release-manifest.yaml
require_pattern 'integrated_server_018_commit: "b967846832586858d42a5e21091154c682948d00"' release-manifest.yaml
require_pattern 'post_shutdown_release_contract_integrated_commit: "f9419c9428424b958bd938db0f6cf29650acf3f0"' release-manifest.yaml
require_pattern 'baseline_insertion_qualified_commit: "6b4b8e51c8ab4023cb5e81c2b2d9ec9ad9d5cdc3"' release-manifest.yaml
require_pattern 'baseline_insertion_documentation_commit: "d894e6e3d17a3c7bfb6c7a5c110664f838c489bf"' release-manifest.yaml
require_pattern 'baseline_insertion_integrated_commit: "a70dd388576882aa4cf827a31b3f4724ac339b16"' release-manifest.yaml
require_pattern 'baseline_insertion_pull_request: 81' release-manifest.yaml
require_pattern 'post_baseline_release_contract_qualified_commit: "7385c282ca6f97a11c7436c06cc74cb01e4c018b"' release-manifest.yaml
require_pattern 'post_baseline_release_contract_integrated_commit: "307042411124ae181e19aea70b50ca7dff6d72a1"' release-manifest.yaml
require_pattern 'post_baseline_release_contract_pull_request: 82' release-manifest.yaml
require_pattern 'current_product_shelf_commit: "307042411124ae181e19aea70b50ca7dff6d72a1"' release-manifest.yaml
require_pattern 'candidate_version: "server-0.18"' release-manifest.yaml
require_pattern 'previous_failed_candidate_version: "server-0.17"' release-manifest.yaml
require_pattern 'production_version: "server-0.14"' release-manifest.yaml
require_pattern 'temporary_vps_soak_version: "server-0.18"' release-manifest.yaml
require_pattern 'post_shutdown_release_contract_qualification: "PASS"' release-manifest.yaml
require_pattern 'baseline_insertion_qualification: "PASS"' release-manifest.yaml
require_pattern 'baseline_insertion_manual_torture_qualification: "PASS"' release-manifest.yaml
require_pattern 'baseline_insertion_integration: "PASS"' release-manifest.yaml
require_pattern 'post_baseline_insertion_release_contract_qualification: "PASS"' release-manifest.yaml
require_pattern 'corrected_r3_operations_head: "e451643776c16992e831c8b7313d60381d1e79c0"' release-manifest.yaml
require_pattern 'corrected_r3_artifact_id: 8989921008' release-manifest.yaml
require_pattern 'corrected_r3_package_qualification: "PASS"' release-manifest.yaml
require_pattern 'corrected_r3_vps_deployment: "PASS"' release-manifest.yaml
require_pattern 'vps_manual_torture_a_i: "PASS"' release-manifest.yaml
require_pattern 'vps_soak_closure_branch: "fix/3.5.2.17-vps-soak-closure"' release-manifest.yaml
require_pattern 'vps_soak_closure_qualified_commit: "4f47994f61f68bf163179b8cd8eacbb1062633dd"' release-manifest.yaml
require_pattern 'vps_soak_closure_pull_request: 84' release-manifest.yaml
require_pattern 'vps_soak_closure_qualification: "PASS"' release-manifest.yaml
require_pattern 'acceptance: "NOT_PERFORMED"' release-manifest.yaml
require_pattern 'production_cutover: "NOT_PERFORMED"' release-manifest.yaml
require_pattern 'release_acceptance_performed: false' release-manifest.yaml
require_pattern 'release_branch_created: false' release-manifest.yaml
require_pattern 'permanent_production_cutover: false' release-manifest.yaml

require_pattern 'server_version: server-0.18' 3.5.2-closure.md
require_pattern '3.5.2.16.*Post-baseline-insertion release contract' 3.5.2-closure.md
require_pattern '3.5.2.17.*Corrected-r3 VPS soak closure' 3.5.2-closure.md
require_pattern '307042411124ae181e19aea70b50ca7dff6d72a1' 3.5.2-closure.md
require_pattern 'manual VPS torture A-I' 3.5.2-closure.md
require_pattern '3.5.2.17 VPS soak closure:.*PASS' 3.5.2-closure.md
require_pattern '3.5.2.15' REPOSITORY-LIFECYCLE.md
require_pattern '3.5.2.16' REPOSITORY-LIFECYCLE.md
require_pattern 'kanger MUST NOT depend on' REPOSITORY-LIFECYCLE.md
require_pattern 'Server 0.18 deployment contract' kanger-server/DEPLOYMENT.md
require_pattern 'a70dd388576882aa4cf827a31b3f4724ac339b16' kanger-server/DEPLOYMENT.md
require_pattern 'baseline insertion' kanger-server/DEPLOYMENT.md
require_pattern 'fresh disposable database' kanger-server/DEPLOYMENT.md
require_pattern 'must not be opened, repaired, reindexed or deleted' kanger-server/DEPLOYMENT.md
require_pattern '3.5.2.15 integrated shelf' docs/qualification/3.5.2.16-post-baseline-insertion-release-contract.md
require_pattern 'Corrected r3 operations provenance' docs/qualification/3.5.2.17-vps-soak-closure.md
require_pattern 'manual VPS torture route through A–I' docs/qualification/3.5.2.17-vps-soak-closure.md
require_pattern 'release acceptance:.*NOT PERFORMED' docs/qualification/3.5.2.17-vps-soak-closure.md

require_pattern '<kanger.server.artifact.version>server-0.18</kanger.server.artifact.version>' kanger-server/pom.xml
require_fixed 'EXPECTED_SERVER_VERSION="${KANGER_EXPECTED_SERVER_VERSION:-server-0.18}"' kanger-server/scripts/smoke-local.sh
require_fixed '"server_version":"server-0.18"' kanger-server/deploy/verify-installed.sh

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
actual_browser_files="$(find html -mindepth 1 -maxdepth 1 -type f -printf '%f\n' | sort)"
test "${actual_browser_files}" = "${expected_browser_files}"
test "$(git hash-object html/javascript-mode-vendor.js)" = "047395622eb2501dea6fbb9e6be2389e02bf2c77"
require_pattern 'inventory_count: 15' release-manifest.yaml
require_pattern 'sandbox="allow-scripts"' html/index.html
! grep -q 'allow-same-origin' html/index.html
require_pattern '<script src="containment.js"></script>' html/index.html
require_pattern 'error.js' html/javascript-mode.js

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
require_pattern 'Publish the qualified 15-file browser artifact' kanger-server/DEPLOYMENT.md
require_pattern 'sandbox="allow-scripts"' kanger-server/DEPLOYMENT.md
require_pattern 'server-0.18' kanger-server/VERSION-CONTRACT.md
! grep -q 'allow-same-origin is permitted' kanger-server/DEPLOYMENT.md

echo "RELEASE_SHELF_PASS deployment-contract"
echo "RELEASE_SHELF_OK"
