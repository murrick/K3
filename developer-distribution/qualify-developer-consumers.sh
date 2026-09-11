#!/usr/bin/env bash
set -euo pipefail
export LC_ALL=C

ARCHIVE="${1:-}"

fail() {
  printf 'ERROR: %s\n' "$*" >&2
  exit 1
}

log() {
  printf '[qualify-developer-consumers] %s\n' "$*"
}

require_command() {
  command -v "$1" >/dev/null 2>&1 || fail "Required command not found: $1"
}

for command in java mvn gradle tar find grep tr mktemp mkdir rm cp sha256sum sort; do
  require_command "${command}"
done

[[ -n "${ARCHIVE}" ]] || fail "Usage: $0 <kanger-developer-*.tar.gz>"
[[ -f "${ARCHIVE}" ]] || fail "Developer distribution archive not found: ${ARCHIVE}"

STAGING_PARENT="$(mktemp -d "${TMPDIR:-/tmp}/kanger-developer-consumers.XXXXXX")"
trap 'rm -rf -- "${STAGING_PARENT}"' EXIT

tar -xzf "${ARCHIVE}" -C "${STAGING_PARENT}"

bundle_dirs=()
while IFS= read -r dir; do
  bundle_dirs+=("${dir}")
done < <(find "${STAGING_PARENT}" -mindepth 1 -maxdepth 1 -type d -print)
[[ "${#bundle_dirs[@]}" -eq 1 ]] \
  || fail "Expected exactly one top-level bundle directory, found ${#bundle_dirs[@]}"

BUNDLE_DIR="${bundle_dirs[0]}"
[[ -f "${BUNDLE_DIR}/VERSION" ]] || fail "Bundle VERSION file is missing"
VERSION="$(tr -d '[:space:]' < "${BUNDLE_DIR}/VERSION")"
REPOSITORY="${BUNDLE_DIR}/repository"
[[ -d "${REPOSITORY}/org/kanger" ]] || fail "Bundled Maven repository is missing"

for artifact in kanger-command kanger-core kanger-bootstrap kanger-udf kanger-data-dumb kanger-sdk; do
  base="${REPOSITORY}/org/kanger/${artifact}/${VERSION}/${artifact}-${VERSION}"
  [[ -f "${base}.pom" ]] || fail "Canonical SDK POM missing: ${base}.pom"
  [[ -f "${base}.jar" ]] || fail "Canonical SDK JAR missing: ${base}.jar"
done

if grep -R -n -E '3\.3-SNAPSHOT|kanger-server|org\.jline' "${REPOSITORY}/org/kanger"; then
  fail "Internal or non-SDK dependency leaked into canonical SDK repository"
fi

QUALIFICATION="${STAGING_PARENT}/qualification"
mkdir -p "${QUALIFICATION}"

log "qualifying Maven consumer against bundled org.kanger:${VERSION} repository"
MAVEN_PROJECT="${QUALIFICATION}/maven"
cp -a "${BUNDLE_DIR}/examples/maven" "${MAVEN_PROJECT}"
MAVEN_REPO="${QUALIFICATION}/m2"
mkdir -p "${MAVEN_REPO}"
if [[ -d "${HOME}/.m2/repository" ]]; then
  cp -a "${HOME}/.m2/repository/." "${MAVEN_REPO}/"
fi
rm -rf "${MAVEN_REPO}/org/kanger"

mvn --batch-mode --no-transfer-progress \
  -Dmaven.repo.local="${MAVEN_REPO}" \
  -Dkanger.repository="${REPOSITORY}" \
  -f "${MAVEN_PROJECT}/pom.xml" \
  org.apache.maven.plugins:maven-compiler-plugin:3.11.0:compile

[[ -f "${MAVEN_PROJECT}/target/classes/StorageExample.class" ]] \
  || fail "Maven consumer did not compile StorageExample"

# The aggregate embedding SDK intentionally excludes the infrastructure command
# feature. Core, Bootstrap and the bundled providers must resolve; Command must
# remain available in the repository only for consumers that choose it explicitly.
for artifact in kanger-core kanger-bootstrap kanger-udf kanger-data-dumb kanger-sdk; do
  bundled="${REPOSITORY}/org/kanger/${artifact}/${VERSION}"
  resolved="${MAVEN_REPO}/org/kanger/${artifact}/${VERSION}"
  for extension in pom jar; do
    expected="${bundled}/${artifact}-${VERSION}.${extension}"
    actual="${resolved}/${artifact}-${VERSION}.${extension}"
    [[ -f "${actual}" ]] || fail "Maven did not resolve ${artifact}-${VERSION}.${extension}"
    expected_sha="$(sha256sum "${expected}")"
    expected_sha="${expected_sha%% *}"
    actual_sha="$(sha256sum "${actual}")"
    actual_sha="${actual_sha%% *}"
    [[ "${expected_sha}" == "${actual_sha}" ]] \
      || fail "Maven resolved non-bundled ${artifact}-${VERSION}.${extension}"
  done
done

COMMAND_RESOLVED="${MAVEN_REPO}/org/kanger/kanger-command/${VERSION}"
[[ ! -e "${COMMAND_RESOLVED}/kanger-command-${VERSION}.pom" \
   && ! -e "${COMMAND_RESOLVED}/kanger-command-${VERSION}.jar" ]] \
  || fail "Embedding SDK resolved kanger-command transitively"

maven_cp="${MAVEN_PROJECT}/target/classes"
while IFS= read -r jar; do
  maven_cp="${maven_cp}:${jar}"
done < <(find "${MAVEN_REPO}/org/kanger" -type f -name '*.jar' | sort)
MAVEN_OUTPUT="${QUALIFICATION}/maven-run.log"
JAVA_TOOL_OPTIONS="-Duser.home=${QUALIFICATION}/maven-home" \
  java -cp "${maven_cp}" StorageExample >"${MAVEN_OUTPUT}" 2>&1
grep -Fq 'STORAGE_PASS' "${MAVEN_OUTPUT}" \
  || { cat "${MAVEN_OUTPUT}" >&2; fail "Maven consumer runtime probe failed"; }
log "MAVEN_CONSUMER_PASS version=${VERSION} command=not-resolved"

log "qualifying Gradle consumer with bundled repository as its only dependency source"
GRADLE_PROJECT="${QUALIFICATION}/gradle"
cp -a "${BUNDLE_DIR}/examples/gradle" "${GRADLE_PROJECT}"
GRADLE_OUTPUT="${QUALIFICATION}/gradle-run.log"
JAVA_TOOL_OPTIONS="-Duser.home=${QUALIFICATION}/gradle-runtime-home" \
  gradle \
  --no-daemon \
  --gradle-user-home "${QUALIFICATION}/gradle-home" \
  -p "${GRADLE_PROJECT}" \
  -PkangerRepository="${REPOSITORY}" \
  run >"${GRADLE_OUTPUT}" 2>&1 \
  || { cat "${GRADLE_OUTPUT}" >&2; fail "Gradle consumer build failed"; }
grep -Fq 'STORAGE_PASS' "${GRADLE_OUTPUT}" \
  || { cat "${GRADLE_OUTPUT}" >&2; fail "Gradle consumer runtime probe failed"; }
log "GRADLE_CONSUMER_PASS version=${VERSION}"

log "DEVELOPER_CONSUMER_QUALIFICATION_PASS version=${VERSION}"
