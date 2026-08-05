# KANGER 3.5.2 VPS soak protocol

Status: operations candidate; no release acceptance or production-release authorization.

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
public UI symlink:       /var/www/html/kanger -> /home/murray/sites/kanger
```

The soak package changes no KANGER product source after the qualified `3.5.2`
head. It packages the matched Server 0.17 JAR and 15-file browser distribution,
plus backup, deployment and rollback tools.

## UI publication model

The VPS intentionally uses a stable editable directory owned by `murray`:

```text
/home/murray/sites/kanger
```

nginx reaches it through:

```text
/var/www/html/kanger -> /home/murray/sites/kanger
```

The soak must preserve this topology. It does not repoint either path. The
deployment overlays only the 15 KANGER-managed browser files in the editable
directory. Site-specific and otherwise unmanaged files, including ownership
verification files, are left in place.

Before the overlay, the complete editable UI directory is copied to a
timestamped sibling directory. Automatic rollback and normal soak rollback
remove the 15 candidate-managed files and restore the pre-soak copy. Unmanaged
files created during the soak are not removed by normal rollback.

## Safety model

The procedure has three separate recovery levels:

1. **Automatic failed-deployment rollback** restores the previous JAR and
   managed UI files if installation or verification fails.
2. **Normal soak rollback** restores the matched `server-0.14` JAR/UI pair but
   intentionally keeps database state and unmanaged site files produced during
   the soak.
3. **Full snapshot restore** restores config, state, server installation,
   nginx, systemd, the editable UI directory and its public symlink. It is
   disaster recovery and discards state changes made after the snapshot.

The full snapshot contains account data and configuration secrets. Keep it mode
`0600`, transfer it over SSH, and store it privately.

## 1. Obtain and verify the package

Use the latest successful `KANGER 3.5.2 VPS soak package` GitHub Actions
artifact named:

```text
kanger-3.5.2-server-0.17-vps-soak
```

Do not reuse an earlier package whose scripts expect
`/home/murray/sites/kanger` itself to be a symlink.

The artifact contains a `.tar.gz`, its SHA-256 file, `SOURCE.txt` and the
component `SHA256SUMS`.

On the local workstation:

```bash
shasum -a 256 -c kanger-3.5.2-server-0.17-vps-soak-*.tar.gz.sha256
```

Upload and extract it:

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

Do not deploy unless `SOURCE.txt` contains exactly:

```text
canonical_source_head=7946d3969302aa198fea506f419a885565db118a
server_version=server-0.17
ui_directory=/home/murray/sites/kanger
public_ui_link=/var/www/html/kanger
ui_update_mode=managed-file-overlay
```

## 2. Verify the current production anchors

Before the snapshot:

```bash
curl --fail --silent http://127.0.0.1:1964/health; echo
curl --fail --silent http://127.0.0.1:1964/ready; echo
sudo sha256sum /opt/kanger-server/kanger-server.jar
test -d /home/murray/sites/kanger
test ! -L /home/murray/sites/kanger
test -L /var/www/html/kanger
readlink -f /var/www/html/kanger
sudo nginx -t
```

Required anchors:

```text
server_version:        server-0.14
JAR SHA-256:           e089497d0a8f041a872a3a5a09581f8d94f5962a277794747b7f54e209882a19
editable UI directory: /home/murray/sites/kanger
public UI target:      /home/murray/sites/kanger
```

## 3. Create a transactionally quiet host snapshot

From the extracted package directory:

```bash
sudo bash deploy/snapshot-current.sh | tee /tmp/kanger-snapshot-result.txt
```

The script stops KANGER cleanly, verifies removal of the active marker, captures
the state, starts Server 0.14 and re-verifies health/readiness before returning
success.

The snapshot includes:

```text
/etc/kanger-server
/var/lib/kanger-server
/opt/kanger-server
/etc/systemd/system/kanger-server.service
/etc/nginx
/home/murray/sites/kanger
/var/www/html/kanger
health/readiness responses
JAR and complete UI hashes
UI tree ownership/modes
systemd unit/status
listener evidence
nginx effective configuration
Java identity
```

## 4. Copy the snapshot off-host

The root-owned archive must not remain the only copy. Read the exact archive
path and SHA-256 from `/tmp/kanger-snapshot-result.txt`, then expose a temporary
owner-only handoff copy:

```bash
archive="$(sudo awk -F= '$1 == "archive" {print $2}' /tmp/kanger-snapshot-result.txt)"
sudo install -d -o murray -g murray -m 0700 /home/murray/kanger-backups
sudo install -o murray -g murray -m 0600 \
  "${archive}" /home/murray/kanger-backups/
sudo install -o murray -g murray -m 0600 \
  "${archive}.sha256" /home/murray/kanger-backups/
```

On the local workstation:

```bash
scp -P 4211 \
  'murray@94.103.94.41:/home/murray/kanger-backups/kanger-vps-before-3.5.2-*' \
  ./

shasum -a 256 -c kanger-vps-before-3.5.2-*.tar.gz.sha256
```

Create proof of the verified off-host copy:

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

The deployment script refuses to run unless this receipt matches the on-host
snapshot byte-for-byte.

## 5. Start the soak deployment

On the VPS, from `/tmp`:

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

- re-verifies the complete package;
- verifies the real UI topology;
- records the previous JAR and complete editable UI;
- creates a timestamped pre-soak UI backup;
- stages the exact 15-file candidate UI separately;
- installs Server 0.17 through the rollback-capable installer;
- proves health, readiness, loopback confinement and nginx validity;
- atomically replaces each managed file in `/home/murray/sites/kanger`;
- leaves unmanaged files in that directory untouched;
- verifies the origin UI contains the containment boundary;
- writes deployment evidence under `/root/kanger-deployments`;
- prints the exact rollback command.

Any failure before final success restores the previous JAR and managed UI files.

## 6. Soak checks

Use a dedicated test account and dedicated databases. Do not use destructive
commands against valuable production data.

Minimum functional route:

```text
login and token rotation
plain query and semantic views
source get / edit / put / delete
missing source and failed save errors
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
workspace source/storage/transaction indicators remain truthful
```

Operational observation:

```bash
sudo systemctl --no-pager --full status kanger-server.service
sudo journalctl -u kanger-server.service --since '1 hour ago' --no-pager
curl --fail --silent http://127.0.0.1:1964/health; echo
curl --fail --silent http://127.0.0.1:1964/ready; echo
sudo kanger-admin status
sudo ss -ltnp | grep -E ':(1964|1965)'
readlink -f /var/www/html/kanger
```

Keep the deployment evidence directory and record meaningful incidents in
`06_KANGER` with exact UTC timestamps.

## 7. Normal rollback after or during the soak

Use the exact command printed by `deploy-soak.sh`, or:

```bash
sudo bash "${bundle}/deploy/rollback-soak.sh" \
  /root/kanger-deployments/3.5.2-soak-<UTC-STAMP>
```

This restores Server 0.14 and the pre-soak versions of the 15 managed UI files.
The editable directory and public symlink remain in their original topology.
Database state and unrelated site files are preserved. The full pre-soak
snapshot remains untouched.

## 8. Full disaster recovery

Use only if config/state was corrupted and code rollback is insufficient. This
discards all changes since the snapshot.

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

Then verify Server 0.14, `/var/www/html/kanger ->
/home/murray/sites/kanger`, loopback listeners and public routing.

## Acceptance boundary

A successful soak does not by itself create or accept `release/3.5.2`.
Throughout the soak:

```text
PR #72:                  draft and unmerged
release/3.5.2:           not created
tag/GitHub Release:      not created
accepted production:     release/3.5.1 + server-0.14
running VPS code:        temporary qualified 3.5.2/server-0.17 soak candidate
```

Release fixation and permanent production acceptance remain a separate explicit
decision after the observation period.
