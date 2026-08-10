# KANGER 3.7.0.6 — VPS development-soak deployment contract

Date: 2026-08-10

## Lifecycle position

This operations layer deploys the 3.7.0.6 development-soak candidate over the currently active 3.7.0.5 development soak.

It does not perform release acceptance, merge, tag/publication, or production cutover.

## Exact anchors

Canonical product source:

```text
branch: ui/3.7.0.6-soak-corrections
head:   62fdb5d56fe5efe5e3cbe3ca8771d4f677644155
```

Immediate prior VPS development soak:

```text
artifact: 3.7.0.5-vps-soak
server:   server-0.18
jar sha256:
  5a25dbf5f563f9cd36647ca75566798a39335b076d5b123b8d5e5f3a7501e763
public UI target:
  /home/murray/sites/kanger-3.7.0.5-vps-soak-20260810T125400Z
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

The accepted production anchor is recorded for disaster recovery, but the automatic failure rollback for this deployment restores the immediate prior 3.7.0.5 development soak.

## Required sequence

1. Verify the immutable 3.7.0.6 bundle and its internal `SHA256SUMS`.
2. Run `deploy/snapshot-current.sh` against the active 3.7.0.5 soak.
3. Copy the new snapshot and its `.sha256` off-host.
4. Independently verify the off-host SHA.
5. Create an off-host receipt containing exact archive basename and SHA-256.
6. Run `deploy/deploy-soak.sh BUNDLE_DIR SNAPSHOT_ARCHIVE OFFHOST_RECEIPT`.
7. Require `DEVELOPMENT_SOAK_DEPLOYMENT_OK`.
8. Independently verify installed candidate JAR, `/health`, `/ready`, public UI target, and Browser bytes.
9. Execute `VPS-SOAK-3.7.0.6.md` manually.

## Failure semantics

Before live mutation, the deploy guard verifies:

- active Server 0.18 health/readiness;
- exact current 3.7.0.5 JAR SHA;
- exact current 3.7.0.5 public UI target;
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

Any guarded deployment failure restores the prior 3.7.0.5 Server JAR and public UI target when those mutations occurred.

The full pre-3.7.0.6 snapshot remains untouched for disaster recovery.

## Manual qualification

The mandatory live regression protocol is:

```text
kanger-server/VPS-SOAK-3.7.0.6.md
```

The artifact remains DEVELOPMENT SOAK until that protocol is completed and explicitly accepted.
