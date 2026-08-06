# KANGER 3.5.2 / Server 0.18 VPS soak protocol

Status: **OPERATIONS CANDIDATE / DEPLOYMENT NOT YET PERFORMED**  
Date: 2026-08-06

## Fixed identities

```text
canonical source branch: develop/3.5.2.9-integration-release-shelf
canonical source head:   b0ed1cee70d6a4bbaf3b7690df766b9eae41f891
operations branch:       ops/3.5.2-server-0.18-vps-soak
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

The package contains a newly built Server 0.18 JAR, the exact 15-file Browser
distribution and operations-only snapshot, deployment and rollback tools. It
contains no product or test-code delta after canonical source head
`b0ed1cee70d6a4bbaf3b7690df766b9eae41f891`.

Server 0.17, its package and its damaged soak database remain immutable failed-
soak evidence. Do not copy, open, repair, reindex, delete or reuse that database.
Use a fresh disposable database for every Server 0.18 qualification route.

## UI publication model

The editable directory:

```text
/home/murray/sites/kanger
```

is not the directory currently served by nginx. The public path is:

```text
/var/www/html/kanger
  -> /home/murray/sites/kanger-server-0.14-20260804T181706Z
```

The soak preserves the versioned-directory model:

1. copy the complete current public target into a new Server 0.18 directory;
2. overlay the exact 15 candidate-managed Browser files;
3. preserve all unrelated files inherited from the current target;
4. atomically repoint `/var/www/html/kanger` using `mv -Tf`;
5. leave `/home/murray/sites/kanger` untouched.

Normal rollback atomically restores the exact prior public target. The candidate
directory remains as evidence.

## Recovery levels

1. **Automatic failed-deployment rollback** restores the previous JAR and exact
   prior UI target when deployment verification fails.
2. **Normal soak rollback** restores Server 0.14 and the prior UI target while
   preserving database state produced during the soak.
3. **Full snapshot restore** restores config, state, installation, systemd,
   nginx, editable UI, published UI and symlink state. It discards all changes
   after the snapshot.

The full snapshot contains account data and secrets. Keep it mode `0600`, move
it only over SSH and store it privately.

## 1. Obtain and verify the package

Use only a successful GitHub Actions artifact named:

```text
kanger-3.5.2-server-0.18-vps-soak
```

The downloaded artifact contains the package tarball, its SHA-256 file,
`SOURCE.txt` and component `SHA256SUMS`.

On the workstation:

```bash
shasum -a 256 -c \
  kanger-3.5.2-server-0.18-vps-soak-*.tar.gz.sha256
```

Upload and extract:

```bash
scp -P 4211 \
  kanger-3.5.2-server-0.18-vps-soak-*.tar.gz \
  murray@94.103.94.41:/tmp/

ssh -p 4211 murray@94.103.94.41
cd /tmp
tar -xzf kanger-3.5.2-server-0.18-vps-soak-*.tar.gz
cd kanger-3.5.2-server-0.18-vps-soak-*
sha256sum -c SHA256SUMS
cat SOURCE.txt
```

Required `SOURCE.txt` anchors:

```text
schema=3
canonical_source_head=b0ed1cee70d6a4bbaf3b7690df766b9eae41f891
packaging_branch=ops/3.5.2-server-0.18-vps-soak
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

A mismatch is a hard stop. Do not override an anchor until its cause is known.

## 3. Create a transactionally quiet host snapshot

From the extracted package directory:

```bash
sudo bash deploy/snapshot-current.sh \
  | tee /tmp/kanger-server-0.18-snapshot-result.txt
```

The script validates current production, stops KANGER cleanly, verifies that
`KANGER/kanger.active` disappeared, captures the host snapshot, restarts Server
0.14 and re-verifies health/readiness.

The snapshot includes:

```text
/etc/kanger-server
/var/lib/kanger-server
/opt/kanger-server
/etc/systemd/system/kanger-server.service
/etc/nginx
/home/murray/sites/kanger
/home/murray/sites/kanger-server-0.14-20260804T181706Z
/var/www/html/kanger
health/readiness evidence
JAR and UI hashes
systemd, listeners, nginx and Java evidence
```

## 4. Copy the snapshot off-host

Read the archive path and create a temporary owner-only handoff copy:

```bash
archive="$(sudo awk -F= '$1 == "archive" {print $2}' \
  /tmp/kanger-server-0.18-snapshot-result.txt)"
sudo install -d -o murray -g murray -m 0700 /home/murray/kanger-backups
sudo install -o murray -g murray -m 0600 \
  "${archive}" /home/murray/kanger-backups/
sudo install -o murray -g murray -m 0600 \
  "${archive}.sha256" /home/murray/kanger-backups/
```

On the workstation:

```bash
scp -P 4211 \
  'murray@94.103.94.41:/home/murray/kanger-backups/kanger-vps-before-3.5.2-server-0.18-*' \
  ./

shasum -a 256 -c \
  kanger-vps-before-3.5.2-server-0.18-*.tar.gz.sha256
```

Create and upload the receipt:

```bash
archive_file="$(ls -1t \
  kanger-vps-before-3.5.2-server-0.18-*.tar.gz | head -1)"
digest="$(shasum -a 256 "${archive_file}" | awk '{print $1}')"
cat > offhost-receipt.txt <<EOF
archive_basename=$(basename "${archive_file}")
sha256=${digest}
copied_utc=$(date -u +%Y-%m-%dT%H:%M:%SZ)
EOF

scp -P 4211 offhost-receipt.txt \
  murray@94.103.94.41:/tmp/kanger-server-0.18-offhost-receipt.txt
```

Deployment refuses to proceed unless the receipt matches the on-host snapshot
byte-for-byte.

## 5. Start the guarded deployment

On the VPS:

```bash
bundle="$(find /tmp -maxdepth 1 -type d \
  -name 'kanger-3.5.2-server-0.18-vps-soak-*' \
  | sort | tail -1)"
snapshot="$(sudo awk -F= '$1 == "archive" {print $2}' \
  /tmp/kanger-server-0.18-snapshot-result.txt)"

sudo bash "${bundle}/deploy/deploy-soak.sh" \
  "${bundle}" \
  "${snapshot}" \
  /tmp/kanger-server-0.18-offhost-receipt.txt
```

The script:

- verifies package checksums, canonical source and packaging branch;
- verifies exact 15-file Browser inventory;
- verifies current Server 0.14, JAR digest and UI target;
- copies the complete current public UI to a new versioned directory;
- overlays only the qualified Browser files;
- installs Server 0.18 through the rollback-capable installer;
- verifies health, readiness, loopback confinement and nginx;
- atomically switches the public UI symlink;
- verifies the containment boundary through local HTTPS;
- writes immutable deployment evidence under `/root/kanger-deployments`;
- prints the exact rollback command.

Any failure before final success restores the previous JAR and prior UI target.

## 6. Mandatory first qualification route

Use a dedicated account and a **fresh disposable database**. Do not reuse the
Server 0.17 database or any valuable production database.

The first route is the former failure boundary:

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

Record exact UTC timestamps, responses and relevant journal excerpts. Stop
immediately on any manifest, persistence, identity or lifecycle anomaly.

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

Operational evidence:

```bash
sudo systemctl --no-pager --full status kanger-server.service
sudo journalctl -u kanger-server.service --since '1 hour ago' --no-pager
curl --fail --silent http://127.0.0.1:1964/health; echo
curl --fail --silent http://127.0.0.1:1964/ready; echo
sudo kanger-admin status
sudo ss -ltnp | grep -E ':(1964|1965)'
readlink -f /var/www/html/kanger
```

## 8. Normal rollback

Use the exact command printed by `deploy-soak.sh`, for example:

```bash
sudo bash "${bundle}/deploy/rollback-soak.sh" \
  /root/kanger-deployments/3.5.2-server-0.18-soak-<UTC-STAMP>
```

This restores Server 0.14 and atomically restores:

```text
/home/murray/sites/kanger-server-0.14-20260804T181706Z
```

Database state is preserved. The candidate UI directory and full snapshot remain
available for evidence.

## 9. Full disaster recovery

Use only if config or persistent-state corruption makes normal rollback
insufficient. This discards all changes since the snapshot.

```bash
sudo systemctl stop kanger-server.service
sudo mkdir -p /root/kanger-full-restore
sudo tar -C /root/kanger-full-restore -xzf \
  /root/kanger-snapshots/kanger-vps-before-3.5.2-server-0.18-<UTC-STAMP>.tar.gz
sudo tar --numeric-owner --acls --xattrs -C / -xf \
  /root/kanger-full-restore/kanger-vps-before-3.5.2-server-0.18-<UTC-STAMP>/host-root.tar
sudo systemctl daemon-reload
sudo nginx -t
sudo systemctl start kanger-server.service
sudo systemctl reload nginx
```

Then verify Server 0.14, the exact public UI target, loopback listeners and
public routing.

## Acceptance boundary

A successful soak does not by itself create or accept `release/3.5.2`.

```text
PR #75:                      merged
PR #76:                      merged
canonical shelf:             b0ed1cee70d6a4bbaf3b7690df766b9eae41f891
release/3.5.2:               not created
tag/GitHub Release:          not created
accepted production before: release/3.5.1 + server-0.14
running during soak:         temporary qualified 3.5.2 / server-0.18 candidate
```

Release fixation and permanent production acceptance remain separately recorded
decisions after soak evidence is reviewed.
