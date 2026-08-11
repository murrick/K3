# KANGER 3.7.0.8 — VPS development-soak deployment contract

Date: 2026-08-11

## Lifecycle position

This operations layer deploys the 3.7.0.8 focused development-soak candidate
over the currently active 3.7.0.7 development soak.

It does not perform release acceptance, merge, tag/publication, or production
cutover.

## Exact anchors

Canonical product source:

```text
branch: ui/3.7.0.8-soak-corrections
head:   ff3ac0a338cfa72ce0ac3660c6fd215bb7da3b2f
```

CI-qualified code checkpoint contained by that product head:

```text
e64e6e41db5e396469e58c40fbcd48c8485afe93
```

Immediate prior VPS development soak:

```text
artifact: 3.7.0.7-vps-soak
server:   server-0.18
jar sha256:
  99ba358d177a1e947cfd8de1fef86da57609e86aa95e3a84d2d128e69ec49f93
public UI target:
  /home/murray/sites/kanger-3.7.0.7-vps-soak-20260811T051011Z
deployment record:
  /root/kanger-deployments/3.7.0.7-vps-soak-20260811T051011Z
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

The accepted production anchor is retained for disaster recovery. Automatic
guarded rollback for this deployment restores the immediate prior 3.7.0.7
development soak.

## Product qualification prerequisite

Packaging is permitted only from exact product head:

```text
ff3ac0a338cfa72ce0ac3660c6fd215bb7da3b2f
```

whose code checkpoint `e64e6e41db5e396469e58c40fbcd48c8485afe93`
completed all nine inherited product workflows SUCCESS:

- KANGER browser trusted rendering;
- KANGER browser session authority;
- KANGER qualification isolation;
- KANGER architectural Javadoc;
- KANGER Server;
- KANGER CI;
- KANGER III 3.4.1 semantic planning;
- KANGER III 3.4.3 storage optimization;
- KANGER III 3.4.4 DUMB reliability qualification.

Server Java 21 qualification at that checkpoint ran 209 tests with zero
failures/errors. The exact Browser inventory remains 22 files.

## Required sequence

1. Verify the immutable 3.7.0.8 bundle and internal `SHA256SUMS`.
2. Run `deploy/snapshot-current.sh` against active 3.7.0.7.
3. Copy the fresh snapshot and `.sha256` off-host.
4. Independently verify the off-host SHA.
5. Create an off-host receipt containing exact archive basename and SHA-256.
6. Run `deploy/deploy-soak.sh BUNDLE_DIR SNAPSHOT_ARCHIVE OFFHOST_RECEIPT`.
7. Require `DEVELOPMENT_SOAK_DEPLOYMENT_OK`.
8. Independently verify installed candidate JAR, `/health`, `/ready`, public UI
   target, and Browser bytes.
9. Execute `VPS-SOAK-3.7.0.8.md` manually, exactly one targeted item at a time.

## Failure semantics

Before live mutation the deploy guard verifies:

- active Server 0.18 health/readiness;
- exact current 3.7.0.7 JAR SHA;
- exact current 3.7.0.7 public UI target;
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

Any guarded deployment failure restores the prior 3.7.0.7 Server JAR and
public UI target when those mutations occurred.

The full pre-3.7.0.8 snapshot remains untouched for disaster recovery.

## Manual qualification

The mandatory live focused regression protocol is:

```text
kanger-server/VPS-SOAK-3.7.0.8.md
```

It covers the two residual 3.7.0.7 blockers, the accumulated Browser polish,
the obsolete confirmation-email menu removal, safety-sensitive command
boundaries, minimal Core smoke, and final independent operations evidence.

A successful soak qualifies only this development candidate. No promotion
follows automatically.
