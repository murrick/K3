# Inside resolved candidate lookup

Parent checkpoint: `340812c`. This adds observation only, no candidate pruning
or persistence change. `kanger.experiment.profileResolved=true` supplies an
invocation-local 12-slot sink through the existing factory recursion. With the
flag off the sink is null and no additional phase clocks run. Original public
two-argument findByResolvedDomain delegates to the same implementation.

## Semantic boundary

RuleFactory visits parents first, then its own candidate index, using each
factory's own Mind to resolve source arguments. Hoisting one resolved array
outside that recursion requires proof of equivalent context-dependent lookup;
it cannot be assumed. Argument resolution precedes even the empty-signature
check in the reference implementation.

Each local selection copies signature IDs, copies fallback IDs, then for each
resolved position copies exact and wildcard sets, unions them with fallback,
and retains compatible selected IDs. IdIndex.get returns a fresh
LinkedHashSet, including for missing keys. These are temporary allocations.

Batching runs after the index lock is released. Generated, non-substitutable
pairs may mark domains and rules used and disappear from returned candidates.
A versioned per-Mind summary caches batched IDs; cache hits still mark the
source used. The final factory lookup materializes visible Rules in accumulated
ID order. Removing batching or replacing this lookup with pure adjacency would
change behavior. Lock ordering must also remain unchanged: no hydration,
TValue resolution or Mind-dependent operations under the index lock.

## Measurements

Factory mode, factoryCandidates/indexedIntersection/timeStages/profileResolved
enabled. TraceInvocations captures both CHECKFALSE and CHECKTRUE, separately
from CSV stdout. Three warmups, seven fresh-Mind measured samples per fixture.
OpenJDK 17.0.20, ECJ 3.33 Java 8 target. Durations are diagnostic wall time,
including instrumentation and lock waiting, not CPU time or allocation counts.

Counts for CHECKTRUE are identical in all seven samples:

| Counter | natives | 100 facts |
|---|---:|---:|
| Resolved lookup calls | 1271 | 901 |
| Factory layers visited | 2542 | 1802 |
| Initially empty local signatures | 1177 | 901 |
| Selected IDs before batching, summed per layer | 6715 | 40800 |
| Batch-summary initial hits | 1232 | 597 |
| Batch-summary initial misses | 44 | 3 |
| Returned visible Rule IDs | 2895 | 800 |

The selected and returned counts are different boundaries and cannot simply be
subtracted to count batched IDs: layer deduplication and visibility also apply.
Native CHECKFALSE adds 445 lookups / 890 layers / 433 empty signatures,
2163 selected IDs, 400 hits / 22 misses, and 987 returned IDs. Fact CHECKFALSE
does no resolved lookup. The last-snapshot CSV describes CHECKTRUE only.

Median phase milliseconds for CHECKTRUE:

| Phase | natives | 100 facts |
|---|---:|---:|
| Entire resolved lookup | 6.001 | 4.706 |
| Signature, argument resolution, batch eligibility | 0.267 | 0.078 |
| Locked ID copies/filtering and summary lookup | 2.141 | 3.182 |
| Batching and union into accumulated IDs | 1.121 | 0.947 |
| Final Rule get / deletion checks / result construction | 1.798 | 0.206 |
| ensureDomainIndex across layers | 0.083 | 0.053 |

Separate medians do not sum exactly. Filter time includes lock acquisition and
release; it does not distinguish allocation, hashing, retainAll, or contention.
Batch time includes result.addAll even when batching is ineligible. Materialize
time does not imply every get loaded a previously unhydrated Rule. Interrupted
phases may be incomplete; filter time is recorded in finally, including early
returns. Timers perturb these very small phases, so no speedup is claimed.

## Decision

Repeated argument resolution is not the dominant measured cost. Empty layers
are common, but an early topology test must preserve resolution behavior and
concurrency semantics. The most specific next experiment is reducing temporary
ID-set copies/unions within the existing locked filtering phase, retaining
selected iteration order and the exact same membership condition. Qualify
against the current copy/union/retainAll implementation before enabling it.
Do not change batching, move hydration under the lock, or introduce a new cache.

## Evidence and qualification

`latent-substitution-evidence/resolved-profile/` contains complete corpus and
state captures, CSV and both-invocation trace. All 123 corpus cases pass in
factory-verify with the new observer. All 16 state operations match the prior
reference stdout byte-for-byte; stderr is captured separately. Full Maven and
canonical Java 8 / 21 qualification were not run. Live develop was rechecked
at `3ad50f1e5253304f6b530de11c078f332ea4db89` and remains untouched.

Sink slot order: calls, layers, empty signatures, resolution ns, filter ns,
batch ns, materialize ns, selected IDs, initial batch hits, initial batch
misses, ensure ns, returned IDs. It follows normal statistics reset, snapshot
and aggregation. Only counters are passed to factories, not an inference owner
or a new service. All flags remain optional and off by default.
