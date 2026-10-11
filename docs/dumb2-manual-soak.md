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

## Experimental whole-source DMZ query

DMZ uses a settled physical Q with no active child transaction/reservation.
Direct TRUST-tagged connections form one protected Q authority; untagged
connections are autonomous external sources for ordinary Console queries. Inspect names and published revisions without
connecting them:

```text
ctx version Q
ctx version A
ctx version B
```

History output includes `UUID:` and the published revision list. Select the exact
Q revision currently open and an explicit published revision of every source:

```text
ctx dmz <Q-UUID>@<revision> "?left(2);" from=A@<A-UUID>@<revision> from=B@<B-UUID>@<revision>
```

Replace each placeholder using the displayed history. `dmz` is an exact keyword;
existing `ctx d A` still means disconnect. A source name containing spaces uses
one quoted token, for example `"from=Named Context@<UUID>@<revision>"`. Names and
pins are supplied separately for every source; omitting sources queries only Q.
There is no implicit CURRENT/HEAD selection or automatic trust connection.

Append named limits when needed, for example `visits=2 work=1000`. Accepted limits
are rows, preparation, audit, lookup, recognition, projection, application, query,
depth, nodes, work and visits. Defaults are 10000 for rows and each phase, depth=8,
nodes=128, work=1000000 and visits=1000. Phase/rows/nodes/visits values must be
1..10000, depth 1..16, work 1..1000000. Unknown/repeated limits, duplicate Context
pins, Q as an external source, noncanonical UUIDs and missing pins are refused.
Use `ctx dmz explain` to inspect the saved profile, authority pins and source inventories.

The explicit policy admits all fixed unit implications before grounds, then
orders source grounds by Context UUID string and source-local rule ID. Supported
external primaries are signed unary integer grounds and signed unary implications
sharing one quantified variable. Every primary must be supported, with at most
16 grounds and 64 units; no unsupported input or capacity overflow is silently
skipped. Source generated caches are reported and excluded. Arbitrary productions,
order-independent/maximal theory enumeration and production alternatives remain
outside this experimental operation.

Smoke fixtures: Q contains `!authority(1);`, A contains `!left(2);`, B contains
`!~left(2); !~authority(1);`. Query `?left(2);` and expect separate TRUE/FALSE
opinions, per-input decisions including DECLINED_Q, and unchanged Q. A source with
`!source(3); !@x source(x) -> result(x);` answers `?result(3);` only inside DMZ.
A production-only source `!@x authority(x) -> more(x);` can answer `?more(1);`
from Q; it does not add more(1) to Q. Named empty sources retain an inventory.

The default Console output lists compact TRUE/FALSE/UNKNOWN opinions with their
actual source Contexts and primary causes. `ctx opinion <N> tree` shows all
retained inference alternatives with applied productions, primary premises and
exact source identities; `ctx dmz explain` shows the technical traversal report.
Both commands read the saved last DMZ result without inference. New/failed query
attempts clear old history. Source advancement does not change saved trees.
The Console distinguishes COMPLETE under the displayed ordered policy from
PARTIAL. A visit stop shows the interrupted input/prefix; conditional stops show
their event/frontier reasons. Completed leaves before a stop are explicitly
partial historical observations. Retry by issuing a fresh complete command with
explicit pins and suitable limits. There is no resume/replay command for a saved
history. Hypotheses are diagnostic and are never accepted automatically.

Planning refuses unsupported primary origins with a typed reason and exact
Context/rule identity, or reports GROUND_LIMIT/PRODUCTION_LIMIT. Revision/name,
acquisition, fixed-unit, audit and query failures display a DMZ refusal and return
to the prompt. Follow any refusal with a valid query, close/reopen Q, and verify
its declarations and topology. Existing connections are not modified by this
operation. The shared canonical processor carries a detached SDK result; Server
has no DMZ transport binding yet and explicitly refuses the new intent.

### `ctx opinion <N> tree`

Read the selected last DMZ opinion's detached inference alternatives. N is a
positive number displayed by that query. Trees retain Context UUIDs/revisions,
source-local rules and configured-by-Q view provenance. UNKNOWN has no resolved
tree. Partial opinions remain labelled partial.

### `ctx dmz explain`

Read the last DMZ query's technical report, including exact authority components,
inputs, ordering, budgets, decisions and conditional events. No inference runs.

Ordinary Console `?predicate(integer);` queries with direct connections select
their current pinned views automatically: Q and all TRUST-tagged members are
authority, untagged members supply external opinions. Explicit `ctx dmz ...`
still selects its external physical pins for debugging. Existing connection
creation, publication and repin qualification boundaries remain in force.
Unsupported connected DMZ grammar is refused; bare `?` program checks and
assertion/mutation commands retain their Core lifecycle.

## Repeat the combined DMZ qualification gate

With a JDK providing javac and Python 3:

```sh
python3 scripts/qualify-dmz.py
```

With a Java runtime and an ECJ jar:

```sh
python3 scripts/qualify-dmz.py --ecj /path/to/ecj.jar --jobs 3
```

The explicit gate manifest is kanger-qualification/test-data/dmz-suite.txt. A run
compiles fresh Java 8-target production and qualification classes, then executes
42 driver processes including the large recursive/cascade matrices, same-JVM
legacy hypothesis cleanup, pinned/portable replay cleanup, public provider and
real Console workflow. Existing repository JLine/Jansi jars are used. This does
not require an installed DUMB plugin or select a user's real storage.

Each invocation writes a fresh run directory under target/dmz-qualification
(or --output), with compiler/driver logs and report.json. --jobs is 1..8; --timeout
sets seconds per driver (default 900). A timeout, nonzero exit or absent final
suite marker fails the command. The aggregate printed check count includes the
recursive matrix repeated inside the legacy cleanup driver; gates without a
published count are still required to pass and are not assigned fictitious
counts. This is a standalone experimental DMZ gate, not full Maven reactor,
packaging or release qualification.

The KANGER DMZ GitHub Actions workflow runs this same manifest for relevant
DMZ/develop changes and PRs targeting develop/3.8.0, and retains compiler/driver
logs plus report.json as an artifact. Repository Maven/Server/bundle checks run
separately. Server's canonical transport intentionally returns
canonical_intent_not_bound for CTX_DMZ; it does not execute the Console-only
operation via a legacy query fallback.
