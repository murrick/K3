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
```

The soak package changes no KANGER product source after the qualified `3.5.2`
head. It packages the matched Server 0.17 JAR and 15-file browser distribution,
plus backup, deployment and rollback tools.

## Safety model

The procedure has three separate recovery levels:

1. **Automatic failed-deployment rollback** restores the previous JAR and UI
   symlink if installation or verification fails.
2. **Normal soak rollback** restores the matched `server-0.14` JAR/UI pair but
   intentionally keeps data produced during the soak.
3. **Full snapshot restore** restores config, state, server installation, nginx,
   systemd and the previous UI. It is disaster recovery and discards state
   changes made after the snapshot.

The full snapshot contains account data and configuration secrets. Keep it mode
`0600`, transfer it over SSH, and store it privately.

## 1. Obtain and verify the package

Use the successful `KANGER 3.5.2 VPS soak package` GitHub Actions artifact named:

```text
kanger-3.5.2-server-0.17-vps-soak
```

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
```

Do not deploy if the canonical source head in `SOURCE.txt` is not exactly
`7946d3969302aa198fea506f419a885565db118a`.

## 2. Create a transactionally quiet host snapshot

From the extracted package directory:

```bash
sudo bash deploy/snapshot-current.sh | tee /tmp/kanger-snapshot-result.txt
```

The script refuses to continue unless all known production anchors match:

```text
server_version: server-0.14
JAR SHA-256:    e089497d0a8f041a872a3a5a09581f8d94f5962a277794747b7f54e209882a19
UI target:      /home/murray/sites/kanger-server-0.14-20260804T181706Z
```

It stops KANGER cleanly, verifies removal of the active marker, captures the
state, and starts and re-verifies Server 0.14 before returning success.

The snapshot includes:

```text
/etc/kanger-server
/var/lib/kanger-server
/opt/kanger-server
/etc/systemd/system/kanger-server.service
/etc/nginx
/home/murray/sites/kanger
resolved current UI target
health/readiness responses
JAR and UI hashes
systemd unit/status
listener evidence
nginx effective configuration
Java identity
```

## 3. Copy the snapshot off-host

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

The deployment script will refuse to run unless this receipt matches the
on-host snapshot byte-for-byte.

## 4. Start the soak deployment

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
- records the previous JAR and UI target;
- stages the new UI in a timestamped directory;
- installs Server 0.17 through the rollback-capable installer;
- proves health, readiness, loopback confinement and nginx validity;
- atomically switches `/home/murray/sites/kanger`;
- verifies the origin UI contains the containment boundary;
- writes a deployment evidence directory under `/root/kanger-deployments`;
- prints the exact rollback command.

Any failure before final acceptance restores the previous JAR and UI pair.

## 5. Soak checks

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
```

Keep the deployment evidence directory and record meaningful incidents in
`06_KANGER` with exact UTC timestamps.

## 6. Normal rollback after or during the soak

Use the exact command printed by `deploy-soak.sh`, or:

```bash
sudo bash "${bundle}/deploy/rollback-soak.sh" \
  /root/kanger-deployments/3.5.2-soak-<UTC-STAMP>
```

This restores Server 0.14 and the previous UI while preserving current data.
The full pre-soak snapshot remains untouched.

## 7. Full disaster recovery

Use only if config/state was corrupted and code rollback is insufficient.
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

Then verify Server 0.14, the prior UI target, loopback listeners and public
routing.

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
