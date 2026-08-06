# KANGER Server 0.18 deployment contract

Status: integrated source candidate; operations package and VPS evidence are separate artifacts.  
Date: 2026-08-06

This is the deployment contract for KANGER repository candidate `3.5.2`,
Core `3.3`, API `1` and deployable Server identity `server-0.18`.

The complete historical Server 0.16 procedure remains preserved as
[`DEPLOYMENT-0.16.md`](DEPLOYMENT-0.16.md). This document supersedes the failed
Server 0.17 deployment contract.

Server 0.17, its operations package and its damaged soak database are immutable
failed-soak evidence. They must not be reused. The damaged database must not be
opened, repaired, reindexed or deleted.

## Fixed source identities

```text
integration branch:        develop/3.5.2.9-integration-release-shelf
corrected shelf:           03310482cebdf55b34829f3d59bdd197edb6275b
qualified Server 0.18 code: a16ec7abb9b2df1aebbaed921088184f0e571c47
qualified documentation:   0213e82023a313641b05ff62d7381da5adc6da09
integrated Server 0.18:     b967846832586858d42a5e21091154c682948d00
integration PR:             #75, merged
```

Every non-empty JSON response must report:

```json
{
  "version": "3.3",
  "core_version": "3.3",
  "api_version": "1",
  "server_version": "server-0.18"
}
```

The generated resource must report:

```properties
branch=server-0.18
server.version=server-0.18
source.branch=<exact source branch>
```

## Corrected storage lifecycle included

Server 0.18 contains the integrated explicit-storage-lifecycle correction:

- transaction commit never closes physical storage;
- a level-zero checkpoint is durable and leaves storage open;
- `use` requires the current storage to be CLOSED and rejects before validating
  the target;
- `close` rejects active transaction levels without implicit commit or rollback;
- ordinary Core `User` owns `use`, `checkpoint` and `close`;
- typed lifecycle failures are shared across Core, Console and Server.

The first VPS qualification route must therefore use a fresh disposable database
and start at the previous failure boundary:

```text
nested transaction
-> commit to level zero
-> explicit close
-> use/reopen
```

Only after that boundary passes may the broader soak continue.

## Matched browser distribution

Publish the qualified 15-file browser artifact together with Server 0.18:

```text
codemirror.css
codemirror.js
config.js
console.html
containment.js
error.js
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

The console iframe remains:

```html
sandbox="allow-scripts"
referrerpolicy="no-referrer"
```

`allow-same-origin` is forbidden. The bearer token remains parent-owned and all
child API traffic crosses the generation-bound parent broker.

## Build boundary

Build only from a fresh operations branch derived exactly from the integrated
Server 0.18 shelf head. The operations branch may add packaging, snapshot,
deployment, rollback and evidence files, but no product-code delta.

The canonical Maven build is:

```bash
mvn -B -ntp \
  -f kanger-server/pom.xml \
  -Dkanger.build.branch.override=develop/3.5.2.9-integration-release-shelf \
  clean verify
```

Before packaging, verify:

```bash
test -f kanger-server/target/kanger-server.jar
unzip -p kanger-server/target/kanger-server.jar \
  org/kanger/build.properties
git diff --name-only \
  b967846832586858d42a5e21091154c682948d00..HEAD
```

The diff must contain operations-only files declared by the package workflow.

## Host topology

The production topology remains:

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

Neither Java listener may bind to `0.0.0.0`, `[::]`, a public address or a
container bridge. nginx proxies only the application listener on port `1964`.

Current accepted production anchors before the Server 0.18 soak are:

```text
release:              release/3.5.1
server:               server-0.14
JAR SHA-256:          e089497d0a8f041a872a3a5a09581f8d94f5962a277794747b7f54e209882a19
editable UI:          /home/murray/sites/kanger
public UI symlink:    /var/www/html/kanger
public UI target:     /home/murray/sites/kanger-server-0.14-20260804T181706Z
```

## Required operations package

The Server 0.18 package must contain at least:

```text
kanger-server.jar
html/                         exact 15-file browser distribution
deploy/install.sh
deploy/verify-installed.sh
deploy/kanger-admin
deploy/snapshot-current.sh
deploy/deploy-soak.sh
deploy/rollback-soak.sh
docs/DEPLOYMENT.md
docs/VPS-SOAK-3.5.2.md
SOURCE.txt
SHA256SUMS
```

`SOURCE.txt` must fix the canonical integrated head, packaging branch and head,
product identities, browser inventory, current production anchors and UI
publication mode. The bundle and each component must have verified SHA-256
checksums.

## Pre-deployment safety gate

Before installing anything:

1. verify current Server 0.14 health/readiness, JAR checksum, service state,
   listeners, nginx and the exact public UI target;
2. create a transactionally quiet host snapshot while KANGER is stopped;
3. verify removal of `KANGER/kanger.active` before archiving persistent state;
4. restart and re-verify Server 0.14;
5. copy the snapshot and checksum off-host over SSH;
6. create an off-host receipt containing the archive basename and digest.

Deployment must refuse to proceed without a matching off-host receipt.

## Soak deployment

The guarded soak procedure must:

- verify the complete package and canonical source head;
- verify current Server 0.14 and its exact JAR checksum;
- copy the complete currently published UI into a new versioned Server 0.18
  candidate directory;
- overlay only the qualified 15 browser files;
- preserve unrelated files inherited from the prior public target;
- install Server 0.18 through the rollback-capable installer;
- verify `/health`, `/ready`, exact `server-0.18`, loopback confinement and
  nginx;
- atomically repoint `/var/www/html/kanger` with `mv -Tf`;
- verify the containment boundary through the local HTTPS origin;
- write immutable deployment evidence and print the exact rollback command.

Any failure before final success must restore Server 0.14 and the exact prior
public UI target.

## Installed-service verification

Run the package verifier after installation:

```bash
sudo bash deploy/verify-installed.sh
```

It must prove:

```text
systemd service enabled and active
/health reports server-0.18
/ready reports server-0.18
application listener confined to 127.0.0.1:1964
operator listener confined to 127.0.0.1:1965
neither Java listener publicly bound
nginx configuration valid
```

## Initial Server 0.18 qualification route

Use a dedicated account and a fresh disposable database. Do not reuse any
Server 0.17 database generation.

Required first route:

```text
login and token rotation
create/use fresh disposable database
begin transaction level 1
begin nested transaction level 2
commit to level 1
commit to level 0
explicit close
use/reopen the same disposable database
verify persisted facts and storage identity
logout and token rejection
clean service restart
reopen and verify again
```

Record exact UTC timestamps, responses and relevant journal excerpts. Stop the
soak immediately on any manifest, storage identity, persistence or lifecycle
anomaly.

## Normal rollback

Normal rollback restores the matched Server 0.14 JAR/UI pair while preserving
database state produced during the soak. It must atomically restore the exact
prior public UI symlink target and verify Server 0.14 health/readiness.

The candidate UI directory, deployment record and full pre-soak snapshot remain
available as evidence.

## Full disaster recovery

Full snapshot restore is reserved for config or persistent-state corruption that
cannot be handled by normal code/UI rollback. It restores config, state,
installation, systemd, nginx, editable UI, published UI and symlink state, and
therefore discards all changes after the snapshot.

## Evidence boundary

The deployment record must capture:

```text
canonical source and packaging heads
JAR SHA-256
bundle SHA-256
component SHA256SUMS
15-file browser inventory and hashes
pre-deployment Server 0.14 anchors
snapshot archive and off-host receipt
health/readiness before and after
listener and nginx evidence
prior and candidate public UI targets
nested close/reopen persistence evidence
rollback command and prior artifact identity
```

Integration and package qualification do not create `release/3.5.2`, a tag or a
GitHub Release. Production acceptance remains a separate decision after the VPS
soak evidence is reviewed.
