# KANGER Server 0.17 deployment

This document is the deployment contract for the qualified KANGER Server
`server-0.17` artifact.

The complete Server 0.16 installation procedure is preserved byte-for-byte as
[`DEPLOYMENT-0.16.md`](DEPLOYMENT-0.16.md). Systemd, nginx, loopback-listener,
operator-plane, backup and host-hardening mechanics that are not explicitly
changed below remain applicable.

This document does **not** authorize deployment from a draft pull request.
Create the integrated release shelf and complete its qualification before any
production cutover.

## Release identity

Every non-empty JSON response continues to carry independent semantic, protocol
and deployable identities:

```json
{
  "version": "3.3",
  "core_version": "3.3",
  "api_version": "1",
  "server_version": "server-0.17"
}
```

`version` and `core_version` identify KANGER semantic compatibility.
`api_version` identifies the public protocol family.
`server_version` identifies the exact deployable server artifact.

Release verification must check `server_version`; it must not infer the server
artifact from `version`.

## Server 0.17 contract delta

Server 0.17 adds one canonical workspace projection to authenticated responses.
It does not change KANGER core version, API version, account-storage format,
DUMB format, listener topology or operator-plane authentication.

```text
workspace
  schema = 1
  source
    logical_name
    has_text
    bytes_utf8
    repository_state = unbound | missing | saved | modified
    persisted
    dirty
  storage
    active
    logical_name
    canonical_name
    physical_generation
      present
      artifacts
      wal_segments
  transaction
    level
    empty
```

The projection is post-operation state. It is returned after successful and
failed authenticated operations whenever the session runtime remains available.
A failed storage switch therefore reports the confirmed previous storage rather
than an optimistic target.

Logical storage names use dotted notation. `canonical_name` preserves the
runtime storage separator. `physical_generation` reports generation evidence
separately from logical identity.

Workspace operation failures receive typed codes where the lower boundary
reported only `operation_failed`, including:

```text
source_load_failed
source_save_failed
source_delete_failed
source_compile_failed
storage_switch_failed
storage_close_failed
storage_drop_failed
storage_reindex_failed
storage_not_used
```

The original diagnostic description remains present.

## Qualified browser artifact

The supported browser distribution contains exactly these top-level files:

```text
codemirror.css
codemirror.js
config.js
console.html
favicon.ico
gateway.js
index.html
javascript-mode-vendor.js
javascript-mode.js
javascript.js
jquery-3.6.0.min.js
operation.js
workspace.js
```

The capability order is:

```text
parent gateway session authority
    -> trusted rendering boundary
    -> operation and coherent snapshot protocol
    -> canonical workspace state authority
    -> historical console callback
```

Deploy the qualified `html/` artifact together with Server 0.17. Do not publish
`console.html` as an independently supported entry point.

## Build the immutable artifact

After integration, build from the qualified Server 0.17 shelf:

```bash
git fetch origin
git switch develop/server/0.17
git status --short

mvn -B -ntp \
  -f kanger-server/pom.xml \
  -Dkanger.build.branch.override=develop/server/0.17 \
  clean verify
```

The generated resource must contain:

```properties
branch=server-0.17
server.version=server-0.17
source.branch=develop/server/0.17
```

The deployable JAR is:

```text
kanger-server/target/kanger-server.jar
```

Before copying any distribution, run:

```bash
bash kanger-server/scripts/run-local.sh
```

In another terminal:

```bash
bash kanger-server/scripts/smoke-local.sh
bash kanger-server/scripts/smoke-auth-local.sh
```

The authenticated smoke proves session rotation, canonical workspace schema,
transaction levels `0`, `1` and `2`, nested logout cleanup and operator-plane
availability.

## Host topology

The required topology is unchanged:

```text
Internet
   |
   +--> https://kanger.org
   |      nginx static UI
   |
   +--> https://api.kanger.org
          nginx reverse proxy
             |
             v
        127.0.0.1:1964
        public application API

host operator
   |
   +--> sudo kanger-admin
             |
             v
        127.0.0.1:1965
        owner-only operator API
```

Both Java listeners must remain loopback-only. nginx proxies only port `1964`.
Port `1965` and its owner bearer token must never enter nginx or browser
configuration.

Minimum persistent configuration:

```properties
server.bind.address=127.0.0.1
server.port=1964

server.admin.enabled=true
server.admin.bind.address=127.0.0.1
server.admin.port=1965
server.admin.token.file=KANGER/admin.token

server.url=https://api.kanger.org
server.confirmation.redirect.url=https://kanger.org/
server.cors.allowed.origin.1=https://kanger.org
server.cors.allowed.origin.2=https://www.kanger.org
server.cors.allow.credentials=false
```

Never bind either Java port to `0.0.0.0`, `[::]`, a public address or a
container bridge.

## Install or update

Use the existing rollback-capable installer:

```bash
sudo bash /tmp/kanger-deploy/install.sh \
  /tmp/kanger-server.jar
```

The installer preserves the previous JAR, restarts the service, waits for health
and readiness, and restores the previous artifact automatically if startup
qualification fails.

Server 0.17 introduces no durable account or database format migration. The
workspace projection is an additive transport contract. Nevertheless, take a
transactionally quiet backup before production cutover:

```bash
stamp="$(date -u +%Y%m%dT%H%M%SZ)"
sudo systemctl stop kanger-server.service
sudo tar -C / -czf "/root/kanger-server-${stamp}.tar.gz" \
  etc/kanger-server \
  var/lib/kanger-server
sudo systemctl start kanger-server.service
```

Copy the archive off-host.

## Verify the installed service

Run:

```bash
sudo bash /tmp/kanger-deploy/verify-installed.sh
```

It must prove:

```text
systemd service enabled and active
/health reports server-0.17
/ready reports server-0.17
application listener confined to 127.0.0.1:1964
operator listener confined to 127.0.0.1:1965
neither Java listener publicly bound
nginx configuration valid
```

Manual identity checks:

```bash
curl --fail --silent --show-error \
  http://127.0.0.1:1964/health
echo

curl --fail --silent --show-error \
  http://127.0.0.1:1964/ready
echo
```

Expected identity:

```text
"version":"3.3"
"api_version":"1"
"server_version":"server-0.17"
```

The detailed `/ready` route remains local. A public request to `/ready` must be
rejected, and port `1965` must not be publicly reachable.

## Verify the workspace response

With an authenticated token, a ping must include schema 1:

```json
{
  "context": "command",
  "parameters": {
    "token": "<session-token>",
    "ping": ""
  }
}
```

Required response properties:

```text
result = OK
workspace.schema = 1
workspace.source.repository_state is recognized
workspace.source.dirty is boolean
workspace.storage.active is boolean
workspace.transaction.level is integer
```

Do not log or persist the bearer token while performing this check.

## Browser cutover

Set the public API endpoint in `html/config.js`:

```javascript
window.KANGER_API_HOST = "https://api.kanger.org";
```

Publish the qualified 13-file browser artifact. Purge or version CDN/browser
caches so `javascript-mode.js`, `operation.js` and `workspace.js` cannot be
served from incompatible generations.

Post-cutover checks:

```text
login creates one parent-owned session
source indicator reflects missing/saved/modified state
active DB indicator survives dropping a different database
failed storage switch preserves the confirmed active DB
nested transaction indicator reaches levels 1 and 2
logout revokes the token and destroys the console frame
```

## Rollback

Rollback the server and browser artifacts as a matched release pair.

```bash
sudo systemctl stop kanger-server.service
sudo cp \
  /opt/kanger-server/kanger-server.jar.previous \
  /opt/kanger-server/kanger-server.jar
sudo chown root:kanger /opt/kanger-server/kanger-server.jar
sudo chmod 0640 /opt/kanger-server/kanger-server.jar
sudo systemctl start kanger-server.service
```

Restore the matching prior `html/` artifact, purge caches, then run the prior
release verifier.

Rollback changes code and response shape, not account or database data. Review
all later migration notes before rolling back from a release newer than 0.17.

## Production evidence

A production cutover record must capture:

```text
release shelf and exact commit
JAR SHA-256
browser artifact inventory and digest
pre-cutover backup location
/health and /ready responses
loopback listener evidence
nginx validation
workspace schema smoke result
rollback artifact identity
```

Draft PR qualification is not production evidence and must not be described as a
deployment.
