# Warm hypothesis profile after compact candidate snapshots

Pinned live base: develop/3.8.0 at 1c943d02d39bd33ebd91fabb7c4190112ab49459.
Branch: experiment/3.8.0-post-snapshot-profile. Production and qualification sources
are byte-identical to this base. All eight integrated optimization properties are
unset and therefore ON. Diagnostic/shadow/verification properties remain unset.

## Protocol and validation

Two fresh JVMs, Java 17.0.20, 512 MB heap, six son(John,x) samples per JVM. An
OptimizationPhase duration event brackets optimizeHypothesis. Only main-thread
ExecutionSample and ObjectAllocationSample events inside samples 2–5 are used;
compilation, query execution, initial two samples and textual rendering are excluded.
JFR stack depth is 256. Both CPU and allocation traces have zero truncation.
Diagnostic Java17 classes live under docs and are excluded from the Java8 reactor.
Build/run/analyze scripts are in docs/post-snapshot-profile. Raw recording paths,
sizes and hashes are in recording-manifest.json; reproducible intermediate JFR
binaries are local, while derived TSVs and full runner output are committed.

All twelve raw/final hypothesis texts and counts agree across both recordings and
with the earlier unprofiled ON snapshot control in resident-snapshots-evidence.
Every JVM emitted six samples and empty stderr. No new regression matrix was run:
production code is unchanged and the merged base already passed all eleven CI
workflows. This work measures attribution, not a new implementation.

ExecutionSample shares are sampled observations, not exact CPU time or call counts.
Inclusive stacks overlap and cannot be summed. Weighted allocation estimates are
not retained heap or exact allocation counts. Profiler timings are not used to infer
speedup, and changes in allocation share across profiles do not prove absolute gain.

## Repeatable observations

| Observation | Recording 1 | Recording 2 |
| --- | ---: | ---: |
| Warm execution samples | 2344 | 2367 |
| isUnitDeleted inclusive share | 19.88% | 19.22% |
| equalsBase inclusive share | 8.11% | 8.32% |
| activeMind inclusive share | 9.51% | 7.52% |
| Current TValue lookup inclusive route share | 7.25% | 6.51% |
| Predicate.getName allocation weight share | 15.18% | 12.89% |
| Escalera.findCandidates allocation weight share | 5.65% | 5.72% |
| TVariable.setMind allocation weight share | 2.24% | 2.31% |

Public Escalera.find attribution is near zero (0.00% / 0.01%); its internal compact
replacement still contributes allocation, including singleton-list snapshots and
multi-candidate HashSet copies. This is consistent with the accepted snapshot
change, but does not replace the earlier clean timing/allocated-byte comparisons.
Predicate name conversion remains prominent; the isolated latency-negative name
prototype is not revived based only on allocation share.

Top leaves differ in JIT attribution: recording 1 attributes 511 samples to
HashMap.getNode and 221 directly to isUnitDeleted; recording 2 attributes 245 and
455 respectively. The repeatable inclusive deletion share is more useful than
interpreting either leaf alone as a different bottleneck.

## Concrete deletion route

415 of 466 deletion observations (89.06%) and 397 of 455 (87.25%) pass through
ArgumentsList.getTVariables. Main callers include Domain.setMind and
ArgumentsList.getStamp / equalsStamp. Source inspection found two adjacent
`!a.isDeleted(mind)` checks in the TVARIABLE branch before contains/getObject.
This is a concrete repeated runtime check in a caller, distinct from the earlier
unsuccessful empty-deletion-layer shortcut inside Mind.isUnitDeleted.

The source also exposes live mutable deleted/restored maps through public getters;
PortableMindLayer writes through them. A maintained empty-state flag/cache would
miss such writes without changing representation/API. Mind.next is mutable, and
getNext may be overridden on custom contexts. These facts preclude assuming a
compile-time deletion state or parent topology for this runtime check.

## Next bounded investigation

Determine whether the adjacent deletion checks can be coalesced for an explicitly
proven built-in runtime case. Preserve both original calls for custom arguments,
variables and Mind/parent callbacks, including a second call changing its result
or throwing. Establish concurrent mutation and restored-before-deleted behavior
before choosing a guard. Do not delete the second check unconditionally or infer
half of deletion CPU cost is removable. If safe guards are too costly or cannot
preserve observable behavior, retain the reference path.

A separate fallback lead is requalifying the previously opt-in active-Mind reuse
prototype on this eight-flag base. It targets repeated WeakReference replacement
in TVariable.setMind, not removal of activeMind's necessary context read or owner
qualification callbacks. Its older timing results are not assumed to transfer.

No production optimization, default change, merge, tag, release or deploy is made
by this profiling branch.
