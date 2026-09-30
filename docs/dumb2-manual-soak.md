# DUMB 2.0 manual soak

This slice exposes DUMB2 as an explicit RuntimeBootstrap storage provider for
hands-on Console and local Server testing without changing the normal
production/default DUMB runtime layout.

## Isolation

The DUMB2 launchers deliberately:

- put `kanger-data-dumb2` on the runtime classpath;
- leave `kanger-data-dumb` off that classpath;
- use a separate `user.home` under `target/dumb2-soak`;
- use separate `KANGER_HOME` roots.

Old DUMB physical databases are not inputs for this soak. DUMB2 has no M1
backward-compatibility requirement for the old DUMB physical format.

## Console

From the repository root:

```bash
bash scripts/run-dumb2-console.sh -S
```

A useful first pass inside Console:

```text
status runtime
storage
use soak
!baseline;
?baseline;
start
!committed;
commit
?committed;
start
!rolled_back;
rollback
?rolled_back;
close
use soak
?baseline;
?committed;
?rolled_back;
```

Expected semantic result: `baseline` and `committed` survive close/reopen;
`rolled_back` does not.

Then exercise normal source loading, larger databases, queries, collisions,
repeated close/open, and process restart.

`storage reindex` is intentionally not part of the M1 runtime surface and is
not a manual-soak target yet.

Set `KANGER_DUMB2_REBUILD=0` to reuse already-built artifacts.

## Local Server

From the repository root:

```bash
bash kanger-server/scripts/run-local-dumb2.sh
```

The launcher creates an isolated server home under
`target/dumb2-soak/server`, copies the existing local server configuration,
and starts the ordinary Server runtime with DUMB2 as its only storage provider.

Use the existing browser/API workflow against the local server. The most useful
checks are the same as Console: create/open storage, compile/query, explicit
transaction commit/rollback, storage close/reopen, session reconnect, and
server process restart.

## What remains unchanged

Normal Console and Server builds still stage the stable
`kanger-data-dumb` provider only. This manual-soak slice does not change the
production default, release branch, deployment, or server cutover.
