# KANGER Server 0.18 deployment contract

Status: post-baseline-insertion integrated candidate; fresh operations package required.  
Date: 2026-08-07

This is the deployment contract for KANGER repository candidate `3.5.2`, Core `3.3`, API `1` and deployable Server identity `server-0.18`.

The complete historical Server 0.16 procedure remains preserved as [`DEPLOYMENT-0.16.md`](DEPLOYMENT-0.16.md). Server 0.17, its operations package and damaged soak database are immutable failed-soak evidence and must not be reused. The damaged database must not be opened, repaired, reindexed or deleted.

## Fixed source identities

```text
integration branch:                develop/3.5.2.9-integration-release-shelf
explicit lifecycle shelf:          03310482cebdf55b34829f3d59bdd197edb6275b
qualified Server 0.18 code:        a16ec7abb9b2df1aebbaed921088184f0e571c47
integrated Server 0.18:             b967846832586858d42a5e21091154c682948d00
Server 0.18 release contract:       b0ed1cee70d6a4bbaf3b7690df766b9eae41f891
Console shutdown integrated shelf:  ddbf5ab380b4124013f58bbd655a2131ccba536b
post-shutdown contract shelf:       f9419c9428424b958bd938db0f6cf29650acf3f0
baseline insertion qualified:       6b4b8e51c8ab4023cb5e81c2b2d9ec9ad9d5cdc3
baseline insertion integrated:      a70dd388576882aa4cf827a31b3f4724ac339b16
baseline insertion PR:              #81, merged
```

The final operations source must be the exact post-baseline-insertion **release-contract-qualified** head descended from `a70dd388576882aa4cf827a31b3f4724ac339b16`. Packaging must record that exact head in `SOURCE.txt`; no earlier Server 0.18 package is valid deployment provenance for the current complete candidate.

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

## Lifecycle included

The complete candidate includes the integrated explicit-storage, shutdown and storage-baseline lifecycle corrections:

- transaction commit never implicitly closes physical storage;
- a level-zero checkpoint is durable and leaves storage open;
- storage `close`, `checkpoint` and `use` require a quiescent root: visible transaction level zero and no pending child reservations;
- opening a database while an ordinary L1+ stack is active is rejected before storage acquisition or factory rebinding;
- a non-empty offline L0 workspace can open a database by inserting the persistent DB as L0 and replaying the workspace as provisional L1;
- that replay intentionally recompiles logical source in the database context so canonical identities and generated consequences are rebuilt;
- explicit commit publishes the workspace once; rollback leaves the database unchanged;
- generalized multi-level rebase is deliberately unsupported;
- interactive Console JVM shutdown retains the active `IMind`, rolls unfinished child transactions back to root and delegates physical shutdown to ordinary `closeStorage()`.

These corrections do not change Server 0.18 product identity, but they change complete-repository release provenance and require a fresh operations package.

## First VPS qualification route

Use a dedicated account and fresh disposable databases. Start at the previous failure and newly corrected lifecycle boundaries:

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

close database
offline workspace assertion
use same/fresh disposable database
verify database is L0 and workspace is provisional L1
rollback; close/reopen; verify workspace was not persisted
repeat insertion; explicit commit; close/reopen; verify exactly one persisted copy
attempt use while L1+ is active; verify typed rejection and unchanged stack

clean service restart
reopen and verify again
exercise abrupt termination / shutdown recovery on disposable state
reopen and verify integrity
logout and token rejection
```

Stop immediately on any manifest, storage identity, persistence, duplication, transaction-topology or lifecycle anomaly. Only after this route passes may the broader soak continue.

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

`allow-same-origin` is forbidden. The bearer token remains parent-owned and all child API traffic crosses the generation-bound parent broker.

## Build boundary

Build only from a fresh operations branch derived exactly from the post-baseline-insertion release-contract-qualified shelf. The operations branch may add packaging, snapshot, deployment, rollback and evidence files, but no product, test or qualification-code delta.

Canonical Maven build:

```bash
mvn -B -ntp \
  -f kanger-server/pom.xml \
  -Dkanger.build.branch.override=develop/3.5.2.9-integration-release-shelf \
  clean verify
```

Before packaging:

```bash
test -f kanger-server/target/kanger-server.jar
unzip -p kanger-server/target/kanger-server.jar org/kanger/build.properties
git merge-base --is-ancestor a70dd388576882aa4cf827a31b3f4724ac339b16 HEAD
```

The package workflow must additionally prove the operations-only delta from its exact canonical source head.

## Host topology

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

Neither Java listener may bind to `0.0.0.0`, `[::]`, a public address or a container bridge. nginx proxies only the application listener on port `1964`.

Current accepted production anchors before the Server 0.18 soak remain:

```text
release:              release/3.5.1
server:               server-0.14
JAR SHA-256:          e089497d0a8f041a872a3a5a09581f8d94f5962a277794747b7f54e209882a19
editable UI:          /home/murray/sites/kanger
public UI symlink:    /var/www/html/kanger
public UI target:     /home/murray/sites/kanger-server-0.14-20260804T181706Z
```

## Required operations package

The fresh Server 0.18 package must contain at least:

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

`SOURCE.txt` must fix the post-baseline-insertion release-contract-qualified source head, packaging branch/head, product identities, browser inventory, current production anchors and UI publication mode. The bundle and each component must have verified SHA-256 checksums.

All earlier Server 0.18 packages are superseded as deployment provenance. They may remain historical build evidence but must not be deployed for this soak.

## Pre-deployment safety gate

Before installing anything:

1. verify Server 0.14 health/readiness, JAR checksum, service state, listeners, nginx and exact public UI target;
2. create a transactionally quiet host snapshot while KANGER is stopped;
3. verify removal of `KANGER/kanger.active` before archiving persistent state;
4. restart and re-verify Server 0.14;
5. copy the snapshot and checksum off-host over SSH;
6. create a matching off-host receipt.

Deployment must refuse to proceed without a matching off-host receipt.

## Soak deployment

The guarded soak procedure must:

- verify complete package checksums and exact canonical source head;
- verify current Server 0.14 and exact JAR checksum;
- copy the complete currently published UI into a new versioned Server 0.18 candidate directory;
- overlay only the qualified 15 browser files;
- preserve unrelated files inherited from the prior public target;
- install Server 0.18 through the rollback-capable installer;
- verify `/health`, `/ready`, exact `server-0.18`, loopback confinement and nginx;
- atomically repoint `/var/www/html/kanger` with `mv -Tf`;
- verify the containment boundary through the local HTTPS origin;
- write immutable deployment evidence and print the exact rollback command.

Any failure before final success must restore Server 0.14 and the exact prior public UI target.

## Installed-service verification

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

## Normal rollback

Normal rollback restores the matched Server 0.14 JAR/UI pair while preserving database state produced during the soak. It must atomically restore the exact prior public UI symlink target and verify Server 0.14 health/readiness.

The candidate UI directory, deployment record and full pre-soak snapshot remain available as evidence.

## Full disaster recovery

Full snapshot restore is reserved for config or persistent-state corruption that cannot be handled by normal code/UI rollback. It restores config, state, installation, systemd, nginx, editable UI, published UI and symlink state and therefore discards changes after the snapshot.

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
baseline-insertion commit/rollback/reopen evidence
clean restart and abrupt-shutdown persistence evidence
rollback command and prior artifact identity
```

Integration and package qualification do not create `release/3.5.2`, a tag or a GitHub Release. Production acceptance remains a separate decision after VPS soak evidence is reviewed.
