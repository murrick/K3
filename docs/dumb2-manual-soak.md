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
reindex soak
?baseline;
?committed;
?rolled_back;
```

Expected semantic result: `baseline` and `committed` survive close/reopen;
`rolled_back` does not.

Then exercise normal source loading, larger databases, queries, collisions,
repeated close/open, and process restart.

`storage reindex` is part of the completed M1 runtime surface. It performs
in-place physical canonicalization inside the same Context identity. Historical
supported layouts are materialized through their descriptor/adapter and
re-encoded through the latest canonical layout, then published as one atomic
next revision. If the Context is already fully canonical, reindex is a semantic
no-op and does not invent a revision.

A failed reindex must leave the previously published revision authoritative and
must not expose a partially canonicalized generation.

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

## Source opinions

After a normal query, collect isolated answers for that **entire last query**:

```text
?male(Tom);
ctx opinions
ctx opinions natives
ctx when natives
ctx solves natives
ctx values natives
```

`ctx opinions` runs live X and explicit direct pins separately. TRUE/FALSE include
available proofs and values; UNKNOWN includes that source's locally qualified
hypotheses. UNKNOWN without hypotheses is omitted. Hypotheses are neither
accepted nor qualified in X. This can differ from a federated conclusion using
facts from other sources.

`ctx values`, `ctx solves` (proof trees), and `ctx when` read the saved collection
without inference. Selecting one source when collecting replaces the collection
with that source. Run a new query after authoring, transaction/storage/topology
changes before collecting again. An optional «Возможно, есть другие мнения» notice
uses only already observed frontier responses; absence of the notice does not
mean the source hypotheses agree. `ctx v` retains its history meaning;
`ctx values` selects the saved opinion values.

Java: `mind.collectContextOpinions(null)` returns an immutable map keyed by source
locator; `mind.getContextOpinions("natives")` reads the saved view.
`mind.hasOtherContextOpinions()` reports the optional hint. Canonical Server
commands return `context_opinions`; ordinary query responses include
`other_opinions_possible`. Sources include Context UUID and exact revision, and
result payloads survive closure of the isolated runtime.

## Find and open a historical revision

`ctx version N` (or `ctx v N`) reads any existing named Context history without
opening or connecting it. It also works with no storage open. A connected target
retains its PINNED marker; an unconnected target only has CURRENT. Missing names
fail without creating a storage or changing the active Context.

```text
ctx version N
use N@7
```

Historical `use X@8` is immutable even when CURRENT is 10. `ctx publish` rejects
before asking for a description; it neither overwrites 10 nor creates 11.
`use X` returns to CURRENT for ordinary authoring and publication.

Qualification for this history follow-up: changed runtime classes compile;
all 316 DUMB2 tests pass, including two added history/publication regressions.
Real Console confirms history with storage closed and with another Context
open, then rejects historical publication without consuming a description or
advancing CURRENT. The ordinary full-reactor results remain attributed to the
preceding implementation head.
