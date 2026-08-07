# KANGER 3.5.2 / Server 0.18 VPS soak protocol — r3

Status: **POST-BASELINE-INSERTION OPERATIONS CANDIDATE / DEPLOYMENT NOT YET PERFORMED**  
Date: 2026-08-07

## Fixed identities

```text
canonical source branch: develop/3.5.2.9-integration-release-shelf
canonical source head:   307042411124ae181e19aea70b50ca7dff6d72a1
operations branch:       ops/3.5.2-server-0.18-vps-soak-r3
packaging_generation=r3
candidate server:        server-0.18
core version:            3.3
API version:             1
current production:      release/3.5.1 + server-0.14
VPS:                     murray@94.103.94.41:4211
public UI:               https://kanger.org
public API:              https://api.kanger.org
application listener:    127.0.0.1:1964
operator listener:       127.0.0.1:1965
editable UI directory:   /home/murray/sites/kanger
public UI symlink:       /var/www/html/kanger
current public target:   /home/murray/sites/kanger-server-0.14-20260804T181706Z
```

This r3 package supersedes r2 as deployment provenance because the complete repository candidate changed with the integrated `3.5.2.15` storage baseline insertion correction and the `3.5.2.16` release-contract refresh. r2 remains immutable historical build evidence and must not be deployed for the current candidate.

Server identity remains `server-0.18`. r3 nevertheless rebuilds and re-hashes the Server JAR and exact 15-file Browser distribution from the new canonical shelf.

Server 0.17, its package and its damaged soak database remain immutable failed-soak evidence. Do not copy, open, repair, reindex, delete or reuse that database. Use a **fresh disposable database** for every Server 0.18 qualification route.

## UI publication and recovery model

The editable directory `/home/murray/sites/kanger` is not served directly. The public path is the versioned-directory symlink `/var/www/html/kanger`.

The soak preserves the established model:

1. copy the complete current public target into a new Server 0.18 r3 directory;
2. overlay only the exact 15 candidate-managed Browser files;
3. preserve unrelated files inherited from the current target;
4. atomically repoint `/var/www/html/kanger` using `mv -Tf`;
5. leave `/home/murray/sites/kanger` untouched.

Recovery levels remain:

1. automatic failed-deployment rollback restores the previous JAR and UI target;
2. normal soak rollback restores Server 0.14 and the prior UI target while preserving soak database state;
3. full snapshot restore is reserved for config or persistent-state corruption and discards post-snapshot changes.

The full snapshot contains secrets and must remain private with mode `0600`.

## 1. Obtain and verify the r3 package

Use only a successful GitHub Actions artifact named:

```text
kanger-3.5.2-server-0.18-vps-soak-r3
```

Verify the tarball SHA-256 before upload and `SHA256SUMS` after extraction. Required `SOURCE.txt` anchors include:

```text
schema=3
canonical_source_head=307042411124ae181e19aea70b50ca7dff6d72a1
packaging_branch=ops/3.5.2-server-0.18-vps-soak-r3
packaging_generation=r3
server_version=server-0.18
core_version=3.3
api_version=1
browser_files=15
baseline_insertion_integrated=a70dd388576882aa4cf827a31b3f4724ac339b16
editable_ui_directory=/home/murray/sites/kanger
public_ui_link=/var/www/html/kanger
production_ui_target=/home/murray/sites/kanger-server-0.14-20260804T181706Z
ui_publication_mode=versioned-directory-atomic-symlink
production_before_soak=server-0.14
```

Any mismatch is a hard stop.

## 2. Verify current production anchors

Before snapshot or deployment verify:

```text
health/ready:          server-0.14
JAR SHA-256:           e089497d0a8f041a872a3a5a09581f8d94f5962a277794747b7f54e209882a19
editable UI directory: /home/murray/sites/kanger
public UI target:      /home/murray/sites/kanger-server-0.14-20260804T181706Z
systemd:               active
nginx:                 valid
```

Also verify both Java listeners remain loopback-only.

## 3. Create a transactionally quiet host snapshot

From the extracted package directory:

```bash
sudo bash deploy/snapshot-current.sh \
  | tee /tmp/kanger-server-0.18-snapshot-result.txt
```

The script stops KANGER cleanly, requires removal of `KANGER/kanger.active`, captures configuration/state/install/systemd/nginx/UI evidence, then restarts and re-verifies Server 0.14.

Copy the resulting archive and checksum off-host over SSH. Verify SHA-256 off-host and return a receipt:

```text
archive_basename=<exact archive>
sha256=<verified digest>
copied_utc=<UTC timestamp>
```

Deployment refuses to proceed without a matching receipt.

## 4. Start guarded deployment

Run `deploy/deploy-soak.sh` with the extracted r3 bundle, snapshot archive and off-host receipt. The script verifies exact r3 source/branch provenance, package checksums, current Server 0.14/JAR/UI anchors, Browser inventory, health/readiness, listener confinement and nginx before activating Server 0.18 and atomically switching the UI symlink.

Any failure before final success restores Server 0.14 and the exact prior public UI target.

## 5. Mandatory lifecycle qualification route

Use a dedicated account and fresh disposable databases. Stop immediately on any persistence, identity, duplication, transaction-topology, storage-integrity or lifecycle anomaly.

### A. Previous failure boundary

```text
login and token rotation
create/use a fresh disposable database
begin transaction level 1
begin nested transaction level 2
commit level 2 -> level 1
commit level 1 -> level 0
explicit close
use/reopen the same disposable database
verify persisted facts and canonical storage identity
```

### B. Storage baseline insertion — rollback

```text
explicit close
define one offline workspace assertion at L0
use the disposable database
verify database content is L0
verify offline workspace is provisional L1
rollback L1
explicit close
use/reopen
verify the offline assertion was NOT persisted
```

### C. Storage baseline insertion — commit

```text
explicit close
define a fresh offline workspace assertion at L0
use the disposable database
verify database L0 + workspace L1
explicit commit L1 -> L0
explicit close
use/reopen
verify the committed assertion exists exactly once
repeat reopen and verify no multiplicative duplicates
```

### D. Unsupported multi-level use

```text
explicit close
build an offline L0/L1/L2 transaction stack
attempt use database while L2 is active
expect ACTIVE_TRANSACTION / TRANSACTION_RESOLUTION_REQUIRED
verify L2/L1/L0 source and transaction topology are unchanged
resolve the original stack with ordinary commit/rollback
verify no duplicate rules and no storage was acquired by the rejected use
```

### E. Restart and abrupt shutdown

After committed disposable state is verified:

```text
clean service restart
use/reopen and verify again
exercise an abrupt process termination on disposable state only
allow the normal service recovery/start path
use/reopen again
verify committed state, no transient state, no duplicates and valid storage integrity
```

The abrupt test must never use the historical damaged Server 0.17 database or production-critical data.

## 6. Broader soak route

Only after the mandatory lifecycle route passes:

```text
plain query and semantic views
source get / edit / put / delete
missing-source and failed-save errors
failed use preserves confirmed active storage
drop a different disposable database
reindex the active disposable database
browser reload and restored parent session
transport interruption and recovery
workspace indicators remain truthful
```

Security/authority checks remain:

```text
iframe sandbox is exactly allow-scripts
no allow-same-origin
child document contains no bearer token
child direct network access is blocked
parent broker remains the only API authority
transport uncertainty does not erase the session
```

Record exact UTC timestamps, responses and relevant journal excerpts.

## 7. Normal rollback

Use the exact rollback command printed by `deploy-soak.sh`. Normal rollback restores Server 0.14 and the exact prior public UI target while preserving database state produced during the soak. Candidate UI and full pre-soak snapshot remain evidence.

## 8. Full disaster recovery

Use only if config or persistent-state corruption makes normal rollback insufficient. Restore the transactionally quiet snapshot, reload systemd/nginx, restart Server 0.14, then verify exact JAR, UI target and loopback listener boundary.

## Acceptance boundary

A successful soak does not itself create or accept `release/3.5.2`.

```text
PR #81 storage baseline insertion: merged
PR #82 release contract refresh:   merged
canonical shelf:                   307042411124ae181e19aea70b50ca7dff6d72a1
release/3.5.2:                     not created
tag/GitHub Release:                not created
accepted production before soak:  release/3.5.1 + server-0.14
running during soak:               temporary qualified 3.5.2 / server-0.18 candidate
```

Release fixation and permanent production acceptance remain separate explicit decisions after soak evidence is reviewed.
