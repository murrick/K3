# KANGER 3.5.2 VPS soak protocol

Status: operations candidate; no release acceptance or permanent production authorization.

## Fixed identities

```text
canonical source branch: develop/3.5.2.9-integration-release-shelf
canonical source head:   7946d3969302aa198fea506f419a885565db118a
operations branch:       ops/3.5.2-vps-soak
candidate server:        server-0.17
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

The soak package contains the qualified Server 0.17 JAR, the exact 15-file
browser distribution and operations-only snapshot/deploy/rollback tools. It
contains no product-code delta after canonical head `7946d3969302`.

## Actual UI publication model

The VPS has two distinct UI locations:

```text
/home/murray/sites/kanger
```

is an editable directory owned by `murray`, but it is not the directory
currently served by nginx.

The public path is:

```text
/var/www/html/kanger
  -> /home/murray/sites/kanger-server-0.14-20260804T181706Z
```

The soak preserves this versioned-directory publication model:

1. copy the complete current public target into a new Server 0.17 soak
   directory;
2. overlay the exact 15 candidate-managed browser files there;
3. preserve all other files inherited from the current public target;
4. atomically replace `/var/www/html/kanger` with a symlink to the candidate
   directory;
5. leave `/home/murray/sites/kanger` untouched.

Normal rollback atomically repoints the public symlink to the exact prior target.
The candidate directory is retained for evidence.

## Recovery levels

1. **Automatic failed-deployment rollback** restores the previous JAR and prior
   public UI symlink target if deployment verification fails.
2. **Normal soak rollback** restores Server 0.14 and the exact prior public UI
   target while preserving database state produced during the soak.
3. **Full snapshot restore** restores config, state, server installation,
   nginx, systemd, the editable UI directory, the published versioned UI
   directory and the public symlink. It discards changes after the snapshot.

The full snapshot contains account data and configuration secrets. Keep it mode
`0600`, transfer it only over SSH and store it privately.

## 1. Obtain and verify the package

Use only the latest successful `KANGER 3.5.2 VPS soak package` artifact named:

```text
kanger-3.5.2-server-0.17-vps-soak
```

On the workstation:

```bash
shasum -a 256 -c kanger-3.5.2-server-0.17-vps-soak-*.tar.gz.sha256
```

Upload and extract:

```bash
scp -P 4211 \
  kanger-3.5.2-server-0.17-vps-soak-*.tar.gz \
  murray@94.103.94.41:/tmp/

ssh -p 4211 murray@94.103.94.41
cd /tmp
tar -xzf kanger-3.5.2-server-0.17-vps-soak-*.tar.gz
cd kanger-3.5.2-server-0.17-vps-soak-*
sha256sum -c SHA256SUMS
cat SOURCE.txt
```

Required `SOURCE.txt` anchors:

```text
schema=3
canonical_source_head=7946d3969302aa198fea506f419a885565db118a
server_version=server-0.17
editable_ui_directory=/home/murray/sites/kanger
public_ui_link=/var/www/html/kanger
production_ui_target=/home/murray/sites/kanger-server-0.14-20260804T181706Z
ui_publication_mode=versioned-directory-atomic-symlink
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
nginx:                 configuration valid
```

## 3. Create a transactionally quiet host snapshot

From the extracted package directory:

```bash
sudo bash deploy/snapshot-current.sh | tee /tmp/kanger-snapshot-result.txt
```

The script validates all production anchors, stops KANGER cleanly, verifies that
`kanger.active` disappeared, captures the snapshot, restarts Server 0.14 and
re-verifies health/readiness.

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
health/readiness responses
JAR hashes
editable and published UI hashes and trees
systemd unit/status
listeners
effective nginx configuration
Java identity
```

## 4. Copy the snapshot off-host

Read the exact archive path from `/tmp/kanger-snapshot-result.txt` and make a
temporary owner-only handoff copy:

```bash
archive="$(sudo awk -F= '$1 == "archive" {print $2}' /tmp/kanger-snapshot-result.txt)"
sudo install -d -o murray -g murray -m 0700 /home/murray/kanger-backups
sudo install -o murray -g murray -m 0600 \
  "${archive}" /home/murray/kanger-backups/
sudo install -o murray -g murray -m 0600 \
  "${archive}.sha256" /home/murray/kanger-backups/
```

On the workstation:

```bash
scp -P 4211 \
  'murray@94.103.94.41:/home/murray/kanger-backups/kanger-vps-before-3.5.2-*' \
  ./

shasum -a 256 -c kanger-vps-before-3.5.2-*.tar.gz.sha256
```

Create and upload a receipt:

```bash
archive_file="$(ls -1t kanger-vps-before-3.5.2-*.tar.gz | head -1)"
digest="$(shasum -a 256 "${archive_file}" | awk '{print $1}')"
cat > offhost-receipt.txt <<EOF
archive_basename=$(basename "${archive_file}")
sha256=${digest}
copied_utc=$(date -u +%Y-%m-%dT%H:%M:%SZ)
EOF

scp -P 4211 offhost-receipt.txt \
  murray@94.103.94.41:/tmp/kanger-offhost-receipt.txt
```

`deploy-soak.sh` refuses to proceed unless this receipt matches the on-host
snapshot byte-for-byte.

## 5. Start the soak deployment

On the VPS:

```bash
bundle="$(find /tmp -maxdepth 1 -type d \
  -name 'kanger-3.5.2-server-0.17-vps-soak-*' | sort | tail -1)"
snapshot="$(sudo awk -F= '$1 == "archive" {print $2}' \
  /tmp/kanger-snapshot-result.txt)"

sudo bash "${bundle}/deploy/deploy-soak.sh" \
  "${bundle}" \
  "${snapshot}" \
  /tmp/kanger-offhost-receipt.txt
```

The script:

- re-verifies package checksums, canonical head and exact browser inventory;
- re-verifies Server 0.14, the current JAR and exact current public UI target;
- copies the complete current published UI to a new versioned candidate
  directory;
- overlays the exact 15 candidate browser files;
- preserves all unrelated files inherited from the prior target;
- installs Server 0.17 through the rollback-capable installer;
- verifies health, readiness, listener confinement and nginx;
- atomically switches `/var/www/html/kanger` to the candidate directory;
- verifies the browser containment boundary from the local HTTPS origin;
- writes evidence under `/root/kanger-deployments`;
- prints the exact rollback command.

Any failure before final success restores the previous JAR and prior UI target.

## 6. Soak checks

Use a dedicated test account and dedicated databases. Do not use destructive
commands against valuable production data.

Minimum route:

```text
login and token rotation
plain query and semantic views
source get / edit / put / delete
missing source and failed-save errors
use a dedicated test database
failed use preserves the confirmed active database
drop a different test database
reindex the active test database
transaction levels 0 -> 1 -> 2
nested logout and token rejection
browser reload and restored parent session
transport interruption and recovery
```

Security/authority checks:

```text
iframe sandbox is exactly allow-scripts
no allow-same-origin
child document contains no bearer token
child direct network access is blocked
parent broker remains the only API authority
transport uncertainty does not erase the session
workspace indicators remain truthful
```

Operational checks:

```bash
sudo systemctl --no-pager --full status kanger-server.service
sudo journalctl -u kanger-server.service --since '1 hour ago' --no-pager
curl --fail --silent http://127.0.0.1:1964/health; echo
curl --fail --silent http://127.0.0.1:1964/ready; echo
sudo kanger-admin status
sudo ss -ltnp | grep -E ':(1964|1965)'
readlink -f /var/www/html/kanger
```

## 7. Normal rollback

Use the exact command printed by `deploy-soak.sh`:

```bash
sudo bash "${bundle}/deploy/rollback-soak.sh" \
  /root/kanger-deployments/3.5.2-soak-<UTC-STAMP>
```

This restores Server 0.14 and atomically repoints `/var/www/html/kanger` to:

```text
/home/murray/sites/kanger-server-0.14-20260804T181706Z
```

Database state is preserved. The candidate UI directory and full snapshot remain
available for evidence and disaster recovery.

## 8. Full disaster recovery

Use only when config or state corruption makes normal rollback insufficient.
This discards all changes since the snapshot.

```bash
sudo systemctl stop kanger-server.service
sudo mkdir -p /root/kanger-full-restore
sudo tar -C /root/kanger-full-restore -xzf \
  /root/kanger-snapshots/kanger-vps-before-3.5.2-<UTC-STAMP>.tar.gz
sudo tar --numeric-owner --acls --xattrs -C / -xf \
  /root/kanger-full-restore/kanger-vps-before-3.5.2-<UTC-STAMP>/host-root.tar
sudo systemctl daemon-reload
sudo nginx -t
sudo systemctl start kanger-server.service
sudo systemctl reload nginx
```

Then verify Server 0.14, the exact public UI target, loopback listeners and
public routing.

## Acceptance boundary

A successful soak does not create or accept `release/3.5.2`.

```text
PR #72:                  draft and unmerged
PR #73:                  draft and unmerged
release/3.5.2:           not created
tag/GitHub Release:      not created
accepted production:     release/3.5.1 + server-0.14
running VPS during soak: temporary qualified 3.5.2/server-0.17 candidate
```

Release fixation and permanent production acceptance remain a separate explicit
decision after observation.
