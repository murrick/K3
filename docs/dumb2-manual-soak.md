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

Use `ctx ask family ?male(Tom);` for an isolated diagnostic query.
The locator after `ask` is literal, including command names such as `opinions`
or `ask`. The legacy `ctx family ?male(Tom);` shorthand remains supported for
names that do not collide with Context commands.

### Private connection initialization

`ctx ask N !p(Mary);`, `ctx ask N -p(John);`, and `ctx ask N +p(John);`
edit a private layer over N's exact pinned revision. They never publish, commit
to, or change N itself. The commands are native KANGER operations, including
quantified rules, and their original source is retained in order in X's
connection vector. Commands targeting live X should be entered normally in X.
The short diagnostic form remains query-only.

Queries, frontier inference, rules inspection and opinions all use the effective
layer. Views label modified sources `configured by X`; their conclusions are
those of X's configured source rather than N's unmodified published artifact.
Each edit is validated in a private child; a rejected command preserves the
previous layer and initialization block. Previous commands are not replayed
for each edit or query. The complete block is replayed once on reopening X.

Initialization is part of X's transaction topology: rollback restores all
connection changes and initialization commands, while commit retains them.
Use `ctx publish "description"` to publish X with this environment. Historical
X remains read-only. Switching N's pin replays and qualifies the block against
the requested revision; failure leaves the previous pin and layer in place.
Disconnecting removes that connection and its configuration.

Exported `.k` includes commands immediately after their connection:

```text
//! ctx connect N@2
//! ctx init -p(John);
//! ctx init !p(Mary);
//! ctx init !@x
//! ctx init+  p(x) -> q(x);
```

`ctx init+` continues a multi-line command. These are declarative source
metadata, not Console commands. Compilation prepares and checks the dependency
block before installation. Connection format 4 stores the ordered source;
existing formats 2 and 3 remain readable. Each connection supports up to 4096
initialization commands.

`ctx solves [N]` expands native rule/donor trees only for resolved opinions
(TRUE or FALSE). UNKNOWN opinions contain only source-local hypotheses; their
Solutions and Values are empty in the Core/SDK/Server projection. `ctx opinions`
omits Solutions/Values blocks for UNKNOWN. Dedicated `ctx solves` and `ctx values`
views return zero elements. Conditional inference artifacts are not answers and
are not exposed as solutions. Hypotheses remain assumptions local to their source.

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
hypotheses, optimized locally against the original query before projection.
UNKNOWN without hypotheses is omitted. Hypotheses are neither
accepted nor qualified in X. This can differ from a federated conclusion using
facts from other sources.

`ctx values`, `ctx solves` (proof trees), and `ctx when` read the saved collection
without inference. Repeating `ctx opinions` with the same source selection reuses
the saved optimized collection, including an empty result. Selecting a different
source when collecting replaces the collection
with that source. Run a new query after authoring, transaction/storage/topology
changes before collecting again. The `Other opinions may be available (ctx opinions)`
notice uses already observed frontier differences and also appears for UNKNOWN
when direct connections exist. It does not probe sources or promise meaningful
opinions. Absence of the notice does not mean the source hypotheses agree.
`ctx v` retains its history meaning;
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

Pair-rejection smoke: connect a target containing male(John) and ~(male(x),female(x)); accept !female(John) in X. Expect a normal STORAGE_CONTEXT_CONFLICT error with witnesses, no stack trace, unchanged X revision and no female(John) rule. Then run ?female(John), bare ?, a valid assertion and close/reopen. Rejected explicit commit must retain U1 for rollback. For UNKNOWN B, ctx opinions lists its hypotheses only and ctx solves has no solutions.

Console ctx opinions progress: fast/cached collections remain silent. After 500 ms, a running collection prints `Collecting context opinions...`, then adds a dot every 10 seconds. Completion or failure closes the line before the result/error. Inference remains on the command thread; the daemon timer only prints progress.

Ordinary federated proof smoke: A:p→q, B:q→r, C:p(John), X:p(Mary). Query ?$x r(x), then solution tree for each returned id. Mary must expand B→A→X and John B→A→C, including both native rules and the original p fact, exact revisions, and `[configured by X]` for an edited connection view. Repeat after a fresh query to check stale provenance is cleared. ctx opinions may remain empty: isolated sources cannot prove the entire federated query. Java combined trees are available through ContextProofProjection.solution(mind, rule).causes; Server adds context_proof alongside legacy causes. No provenance is published to storage.
