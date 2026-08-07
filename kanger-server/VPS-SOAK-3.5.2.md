# KANGER 3.5.2 / Server 0.18 VPS soak protocol — r2

Status: **POST-SHUTDOWN OPERATIONS CANDIDATE / DEPLOYMENT NOT YET PERFORMED**  
Date: 2026-08-07

## Fixed identities

```text
canonical source branch: develop/3.5.2.9-integration-release-shelf
canonical source head:   f9419c9428424b958bd938db0f6cf29650acf3f0
operations branch:       ops/3.5.2-server-0.18-vps-soak-r2
packaging generation:    r2
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

This r2 package supersedes the earlier Server 0.18 package sourced from `b0ed1cee...`. The earlier package remains historical build evidence but must not be deployed because the complete repository candidate changed when `3.5.2.13` repaired the Console JVM shutdown lifecycle and `3.5.2.14` refreshed release provenance.

The Server production identity remains `server-0.18`; the Console correction is outside the Server Maven production roots. r2 nevertheless rebuilds and re-hashes the Server JAR and exact 15-file Browser distribution from the new canonical shelf.

Server 0.17, its package and its damaged soak database remain immutable failed-soak evidence. Do not copy, open, repair, reindex, delete or reuse that database. Use a fresh disposable database for every Server 0.18 qualification route.

## UI publication model

The editable directory `/home/murray/sites/kanger` is not served directly. The public path is:

```text
/var/www/html/kanger
  -> /home/murray/sites/kanger-server-0.14-20260804T181706Z
```

The soak preserves the versioned-directory model:

1. copy the complete current public target into a new Server 0.18 r2 directory;
2. overlay the exact 15 candidate-managed Browser files;
3. preserve unrelated files inherited from the current target;
4. atomically repoint `/var/www/html/kanger` using `mv -Tf`;
5. leave `/home/murray/sites/kanger` untouched.

Normal rollback atomically restores the exact prior public target. The candidate directory remains as evidence.

## Recovery levels

1. **Automatic failed-deployment rollback** restores the previous JAR and prior UI target when deployment verification fails.
2. **Normal soak rollback** restores Server 0.14 and the prior UI target while preserving database state produced during the soak.
3. **Full snapshot restore** restores config, state, installation, systemd, nginx, editable UI, published UI and symlink state, discarding all changes after the snapshot.

The full snapshot contains account data and secrets. Keep it mode `0600`, transfer it only over SSH and store it privately.

## 1. Obtain and verify the r2 package

Use only a successful GitHub Actions artifact named:

```text
kanger-3.5.2-server-0.18-vps-soak-r2
```

The artifact contains the package tarball, tarball SHA-256, `SOURCE.txt` and component `SHA256SUMS`.

On the workstation:

```bash
shasum -a 256 -c kanger-3.5.2-server-0.18-vps-soak-r2-*.tar.gz.sha256
```

Upload and extract:

```bash
scp -P 4211 \
  kanger-3.5.2-server-0.18-vps-soak-r2-*.tar.gz \
  murray@94.103.94.41:/tmp/

ssh -p 4211 murray@94.103.94.41
cd /tmp
tar -xzf kanger-3.5.2-server-0.18-vps-soak-r2-*.tar.gz
cd kanger-3.5.2-server-0.18-vps-soak-r2-*
sha256sum -c SHA256SUMS
cat SOURCE.txt
```

Required `SOURCE.txt` anchors:

```text
schema=3
canonical_source_head=f9419c9428424b958bd938db0f6cf29650acf3f0
packaging_branch=ops/3.5.2-server-0.18-vps-soak-r2
packaging_generation=r2
server_version=server-0.18
core_version=3.3
api_version=1
browser_files=15
editable_ui_directory=/home/murray/sites/kanger
public_ui_link=/var/www/html/kanger
production_ui_target=/home/murray/sites/kanger-server-0.14-20260804T181706Z
ui_publication_mode=versioned-directory-atomic-symlink
production_before_soak=server-0.14
```

## 2. Verify current production anchors

```bash
curl --fail --silent http://127.0.0.1:1964/health; echo
curl --fail --silent http://127.0.0.1:1964/ready; echo
sudo sha256sum /opt/kanger-server/kanger-server.jar
test -d /home/murray/sites/kanger
test ! -L /home/murray/sites/kanger
test -L /var/www/html/kanger
readlink -f /var/www/html/kanger
sudo systemctl is-active kanger-server.service
sudo nginx -t
```

Required values:

```text
server_version:        server-0.14
JAR SHA-256:           e089497d0a8f041a872a3a5a09581f8d94f5962a277794747b7f54e209882a19
editable UI directory: /home/murray/sites/kanger
public UI target:      /home/murray/sites/kanger-server-0.14-20260804T181706Z
systemd:               active
nginx:                 valid
```

Any mismatch is a hard stop.

## 3. Create a transactionally quiet host snapshot

From the extracted package directory:

```bash
sudo bash deploy/snapshot-current.sh \
  | tee /tmp/kanger-server-0.18-snapshot-result.txt
```

The script validates current production, stops KANGER cleanly, verifies that `KANGER/kanger.active` disappeared, captures config/state/install/systemd/nginx/UI and evidence, then restarts and re-verifies Server 0.14.

## 4. Copy the snapshot off-host

Create an owner-only handoff copy, download the archive and checksum over SSH, verify SHA-256 off-host, then upload a receipt containing:

```text
archive_basename=<exact archive>
sha256=<verified digest>
copied_utc=<UTC timestamp>
```

The deployment script refuses to proceed unless the receipt matches the on-host snapshot byte-for-byte.

## 5. Start the guarded deployment

On the VPS:

```bash
bundle="$(find /tmp -maxdepth 1 -type d \
  -name 'kanger-3.5.2-server-0.18-vps-soak-r2-*' \
  | sort | tail -1)"
snapshot="$(sudo awk -F= '$1 == "archive" {print $2}' \
  /tmp/kanger-server-0.18-snapshot-result.txt)"

sudo bash "${bundle}/deploy/deploy-soak.sh" \
  "${bundle}" \
  "${snapshot}" \
  /tmp/kanger-server-0.18-offhost-receipt.txt
```

The script verifies package checksums, exact r2 source/branch provenance, current Server 0.14, JAR digest, UI target, exact Browser inventory, health/readiness, loopback confinement and nginx. It installs Server 0.18, atomically switches the versioned UI symlink, writes immutable evidence and prints the exact rollback command.

Any failure before final success restores the previous JAR and prior public UI target.

## 6. Mandatory first qualification route

Use a dedicated account and a **fresh disposable database**. The first route is the former Server 0.17 failure boundary:

```text
login and token rotation
create and use a fresh disposable database
begin transaction level 1
begin nested transaction level 2
commit to level 1
commit to level 0
explicit close
use/reopen the same disposable database
verify persisted facts and canonical storage identity
clean logout and token rejection
clean service restart
use/reopen and verify again
```

Record exact UTC timestamps, responses and relevant journal excerpts. Stop immediately on any manifest, persistence, identity or lifecycle anomaly.

## 7. Broader soak route

Only after the first route passes:

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

Security/authority checks:

```text
iframe sandbox is exactly allow-scripts
no allow-same-origin
child document contains no bearer token
child direct network access is blocked
parent broker remains the only API authority
transport uncertainty does not erase the session
```

## 8. Normal rollback

Use the exact command printed by `deploy-soak.sh`:

```bash
sudo bash "${bundle}/deploy/rollback-soak.sh" \
  /root/kanger-deployments/3.5.2-server-0.18-soak-r2-<UTC-STAMP>
```

This restores Server 0.14 and the exact prior public UI target while preserving database state. The candidate UI directory and full snapshot remain available as evidence.

## 9. Full disaster recovery

Use only if config or persistent-state corruption makes normal rollback insufficient. Restore the transactionally quiet snapshot, reload systemd/nginx, restart Server 0.14, then verify the exact JAR, UI target and loopback listener boundary.

## Acceptance boundary

A successful soak does not itself create or accept `release/3.5.2`.

```text
PR #75 Server 0.18:             merged
PR #76 release contract:        merged
PR #78 Console shutdown:        merged
PR #79 post-shutdown contract:  merged
canonical shelf:                f9419c9428424b958bd938db0f6cf29650acf3f0
release/3.5.2:                  not created
tag/GitHub Release:             not created
accepted production before:    release/3.5.1 + server-0.14
running during soak:            temporary qualified 3.5.2 / server-0.18 candidate
```

Release fixation and permanent production acceptance remain separately recorded decisions after soak evidence is reviewed.
