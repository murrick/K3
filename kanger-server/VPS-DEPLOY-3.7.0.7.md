# KANGER 3.7.0.7 — VPS development-soak deployment contract

Date: 2026-08-10

## Lifecycle position

This operations layer deploys the 3.7.0.7 targeted development-soak candidate over the currently active 3.7.0.6 development soak.

It does not perform release acceptance, merge, tag/publication, or production cutover.

## Exact anchors

Canonical product source:

```text
branch: ui/3.7.0.7-soak-corrections
head:   5c0cd3662cbadbd63e78ebbc6a23131f1b9682b7
```

CI-qualified code checkpoint contained by that product head:

```text
d43b701f8b722597fc44f08fb1056bbb862b9f6b
```

Immediate prior VPS development soak:

```text
artifact: 3.7.0.6-vps-soak
server:   server-0.18
jar sha256:
  afb72c6569e3496be972cd56ce974998132cf49586948669c8b4e2b8c634d0fe
public UI target:
  /home/murray/sites/kanger-3.7.0.6-vps-soak-20260810T163710Z
deployment record:
  /root/kanger-deployments/3.7.0.6-vps-soak-20260810T163710Z
```

Accepted production recovery anchor remains:

```text
release: release/3.5.2
head:    9c8b7dd2c9ef347cea6af6a6faef9cfa48030306
jar sha256:
  9a8fb1a0f1505d74fb15343ed0519782abb33b53097d6c0e46fbad7bad962718
public UI target:
  /home/murray/sites/kanger-server-0.18-soak-r3-20260807T100726Z
```

The accepted production anchor is retained for disaster recovery. Automatic guarded rollback for this deployment restores the immediate prior 3.7.0.6 development soak.

## Product qualification prerequisite

Packaging is permitted only from exact product head:

```text
5c0cd3662cbadbd63e78ebbc6a23131f1b9682b7
```

after all nine inherited product workflows completed SUCCESS:

- KANGER browser trusted rendering;
- KANGER browser session authority;
- KANGER qualification isolation;
- KANGER architectural Javadoc;
- KANGER Server;
- KANGER CI;
- KANGER III 3.4.1 semantic planning;
- KANGER III 3.4.3 storage optimization;
- KANGER III 3.4.4 DUMB reliability qualification.

## Required sequence

1. Verify the immutable 3.7.0.7 bundle and internal `SHA256SUMS`.
2. Run `deploy/snapshot-current.sh` against active 3.7.0.6.
3. Copy the fresh snapshot and `.sha256` off-host.
4. Independently verify the off-host SHA.
5. Create an off-host receipt containing exact archive basename and SHA-256.
6. Run `deploy/deploy-soak.sh BUNDLE_DIR SNAPSHOT_ARCHIVE OFFHOST_RECEIPT`.
7. Require `DEVELOPMENT_SOAK_DEPLOYMENT_OK`.
8. Independently verify installed candidate JAR, `/health`, `/ready`, public UI target, and Browser bytes.
9. Execute `VPS-SOAK-3.7.0.7.md` manually, one targeted item at a time.

## Failure semantics

Before live mutation the deploy guard verifies:

- active Server 0.18 health/readiness;
- exact current 3.7.0.6 JAR SHA;
- exact current 3.7.0.6 public UI target;
- snapshot/off-host receipt identity;
- package provenance;
- exact 22-file Browser inventory;
- Browser sandbox and loader chain;
- nginx syntax.

After Server/UI mutation it verifies:

- exact candidate JAR bytes;
- Server health/readiness;
- exact public Browser bytes for the loader chain;
- candidate public UI symlink target.

Any guarded deployment failure restores the prior 3.7.0.6 Server JAR and public UI target when those mutations occurred.

The full pre-3.7.0.7 snapshot remains untouched for disaster recovery.

## Manual qualification

The mandatory live targeted regression protocol is:

```text
kanger-server/VPS-SOAK-3.7.0.7.md
```

A successful soak qualifies only this development candidate. No promotion follows automatically.
