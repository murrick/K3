# KANGER 3.7.0.5 — VPS development soak

Date: 2026-08-10

## Purpose

Deploy the late-qualified KANGER 3.7.0.5 development artifact to the existing VPS for operator soak without accepting a release and without changing the immutable accepted repository release.

```text
accepted production repository:
release/3.5.2 @ 9c8b7dd2c9ef347cea6af6a6faef9cfa48030306

accepted production runtime before soak:
KANGER 3.5.2 / Core 3.3 / API 1 / server-0.18
JAR SHA-256: 9a8fb1a0f1505d74fb15343ed0519782abb33b53097d6c0e46fbad7bad962718
public UI: /home/murray/sites/kanger-server-0.18-soak-r3-20260807T100726Z

3.7.0.5 canonical development source:
baaf8f0d0077736666aa9449ff26674f2eec00cd

CI-qualified code point:
e017df0cced5683cfeed949f5065de63ca36d8b3

operations branch:
ops/3.7.0.5-vps-soak

packaging generation:
dev1
```

This deployment is **DEVELOPMENT SOAK ONLY**. It does not merge PR #89, create `release/3.7.0.5`, publish a GitHub Release, change `release/3.5.2`, or constitute release acceptance.

## Package model

The operations branch is derived exactly from `baaf8f0...` and may differ from it only by these five operations files:

```text
.github/workflows/kanger-3.7.0.5-vps-soak.yml
kanger-server/VPS-SOAK-3.7.0.5.md
kanger-server/deploy/deploy-soak.sh
kanger-server/deploy/rollback-soak.sh
kanger-server/deploy/snapshot-current.sh
```

The package contains a matched unit:

```text
kanger-server.jar
html/                 exact 22-file 3.7.0.5 development Browser
deploy/               installation, verification, snapshot and rollback tools
docs/
SOURCE.txt
SHA256SUMS
```

The candidate still reports `server-0.18`, the same public server identity as accepted production. Therefore deployment identity is guarded by **exact JAR SHA-256 and source provenance**, not by `/health` version text alone.

## Browser inventory

The development Browser must contain exactly:

```text
bottom-layout.js
codemirror.css
codemirror.js
config.js
console.html
containment.js
dialogue.js
editor-local-file.js
error.js
favicon.ico
file-download.js
gateway.js
index.html
javascript-mode-vendor.js
javascript-mode.js
javascript.js
jquery-3.6.0.min.js
layout-persistence.js
operation.js
presentation.css
presentation.js
workspace.js
```

The accepted production 3.5.2 Browser remains the historical 15-file artifact. The 22-file Browser is installed only into a new versioned directory and published by atomic symlink switch.

## Mandatory pre-deployment snapshot

On the VPS, from the unpacked bundle:

```bash
sudo bash deploy/snapshot-current.sh
```

The script refuses to snapshot unless the running installation matches the accepted production anchor:

```text
server version: server-0.18
JAR SHA-256:    9a8fb1a0f1505d74fb15343ed0519782abb33b53097d6c0e46fbad7bad962718
public UI:      /home/murray/sites/kanger-server-0.18-soak-r3-20260807T100726Z
```

The resulting archive contains configuration, persistent state, installed runtime, systemd/nginx state and both editable/published UI trees. It contains secrets and must remain private.

**Do not deploy until the snapshot archive and its `.sha256` have been copied off-host and verified there.**

Create a small receipt file on the VPS only after independent off-host verification:

```text
archive_basename=<exact snapshot archive basename>
sha256=<verified sha256>
```

## Guarded deployment

Run:

```bash
sudo bash deploy/deploy-soak.sh \
  <bundle-directory> \
  <snapshot-archive> \
  <off-host-receipt-file>
```

The deployment refuses to proceed unless all of the following hold:

- exact source provenance is `baaf8f0...`;
- packaging branch is `ops/3.7.0.5-vps-soak`;
- package generation is `dev1`;
- all package checksums verify;
- Browser inventory is exactly 22 files;
- current production JAR hash is the accepted 3.5.2 Server 0.18 hash;
- current public UI symlink resolves to the accepted production target;
- snapshot checksum matches an independently verified off-host receipt;
- the candidate JAR differs from the currently installed production JAR;
- nginx configuration validates.

The install then:

1. copies the prior public UI into a fresh versioned candidate directory;
2. replaces the managed 22 Browser files from the package;
3. installs the candidate JAR through the existing Server installer;
4. verifies the installed JAR hash equals the packaged candidate hash;
5. runs the existing installed-server verifier;
6. atomically switches `/var/www/html/kanger` to the candidate UI;
7. validates nginx and reloads it;
8. validates loopback `/health` and `/ready`;
9. records exact prior/candidate JAR hashes and UI targets.

Any failure before completion attempts automatic rollback to the accepted prior JAR and prior public UI target.

## Rollback

The successful deployment prints a command of this form:

```bash
sudo bash <bundle>/deploy/rollback-soak.sh <deployment-record-directory>
```

Rollback verifies that the currently installed JAR still equals the recorded candidate SHA-256 before replacing it with the preserved prior JAR. It also verifies the public UI still points at the recorded candidate directory before switching back.

Rollback restores Server + Browser bytes only. The complete pre-soak snapshot remains the disaster-recovery authority for persistent state/configuration if such restoration is ever required.

## Operator soak target

This soak is deliberately broader and less scripted than the release-qualification torture pass. The objective is to use the current KANGER Browser normally and look for integration defects that automated qualification may not reveal.

Pay particular attention to:

```text
authentication/session lifecycle
source Open / Save / Compile
bare ? whole-program check
command abbreviations and ambiguity rejection
bottom-panel visibility/order/splitters/persistence
predicate information tooltip
mouse compose-only behavior
transactions and storage switching
browser refresh/reconnect
logout/login
long-running ordinary editing/query work
```

If a defect appears, do not accept a release artifact. Preserve the deployment record and reproduce/fix on the development branch.

If no defect appears after the intended soak, the next separate decision may be to freeze a release artifact. This document does not make that decision.
