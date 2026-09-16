# Replication and larger symmetric workloads

Parent `13575b2`. This checkpoint changes only the benchmark runner and evidence;
the inference implementation and default-off versionedSolveSync flag are unchanged.

## Protocol

Three independent JVMs per mode, in mode order OFF, ON, ON, OFF, OFF, ON.
Each JVM runs three warmups then seven measured fresh-Mind samples per fixture.
Fixed `-Xms256m -Xmx512m`, OpenJDK 17.0.20, ECJ 3.33 Java 8 target. Factory
latent mode, factoryCandidates and indexedIntersection on; membershipFilter,
timeStages, profileResolved and traceInvocations off for performance runs.
Processes run sequentially. Report individual JVM medians and their median,
not 21 samples treated as independent processes. This remains a small study.

The runner adds optional benchScaled, benchWarmups, benchSamples,
benchFingerprint and benchOutput properties. Default fixture set and sample
counts remain unchanged. Fingerprinting occurs after the query clock stops;
it sorts the same value/solution/hypothesis/visible-rule projections used by
the state runner and computes SHA-256. Its allocations can affect later GC;
both modes use it equally. It is a projection, not a full heap-state comparison.

## Repeated ordinary fixtures

Median query milliseconds from each JVM, ordered within each mode:

| Fixture | Reference JVM medians | Versioned JVM medians | Median of medians, ref -> new |
|---|---|---|---|
| natives | 103.331, 114.855, 121.753 | 89.570, 96.357, 102.380 | 114.855 -> 96.357 |
| 100 facts | 21.295, 22.607, 24.195 | 25.505, 23.270, 24.599 | 22.607 -> 24.599 |

The native advantage appears in all three adjacent OFF/ON pairs, including the
pair with reversed order, with about 16% lower median of JVM medians. Facts
show no advantage (about 9% higher aggregate median). No universal speedup or
statistical significance is claimed. All 84 ordinary-fixture measured outputs
have consistent rows/pairs/unifications and a single semantic fingerprint per
fixture across both modes.

## Larger symmetric fixtures

10 or 30 facts `edge(i,i+1)`, with symmetry `edge(x,y) -> edge(y,x)` and
projection `edge(x,y) -> path(x,y)`, query `?$x $y path(x,y);`.
These exercise two-variable bindings and cycles through symmetry; they are
not a transitive-closure benchmark or a transaction stress test.

| Fixture | Reference JVM medians, ms | Versioned JVM medians, ms | Median of medians, ref -> new |
|---|---|---|---|
| 10 edges | 27.647, 33.335, 29.582 | 31.730, 29.188, 31.103 | 29.582 -> 31.103 |
| 30 edges | 83.916, 76.101, 79.021 | 76.237, 84.913, 78.602 | 79.021 -> 78.602 |

There is no repeatable scaled-fixture speedup. Exported CHECKTRUE pair and
unification counts are 2519 for 10 edges and 19559 for 30 edges, identical
across modes. Values-store row counts are 21 and 61 (reported as store counts,
not interpreted as distinct answer cardinalities).

All 84 accepted scaled-fixture samples match the old Linker (`latent=off`, all
optimization flags off) in semantic fingerprint. A separate factory-verify run
with actual proposed-skip assertions also matches. It observes CHECKTRUE sync
requests/scans/skips of 1980/23/1957 for 10 edges and 14880/63/14817 for 30.
CHECKFALSE has zero sync requests for these fixtures. Verification timings are
not included in performance results. Many skips alone do not guarantee a large
saving: the cost of each old scan depends on the number of tuple groups and
their shape, not merely the number of candidate pairs.

## Incomplete output handling

Some first-attempt scaled stdout captures had 11-13 rows instead of 14, despite
exit status zero. Explicit flush/checkError and completion markers alone did
not eliminate this capture discrepancy. Its root cause is not established.
Both earlier scaled series were excluded in full, including their complete
files, before examining the final comparison. They are preserved under
`discarded/` for audit, not used to select favorable timings.

benchOutput writes the accumulated CSV once through Files.write after successful
runner completion. The entire scaled series was repeated with this path. Every
accepted file contains both fixtures with sample IDs 0..6 exactly once. The
six ordinary-fixture stdout files were complete and retained. The final scaled
files plus a final verifying run use direct-file output.

## Decision and next work

Keep versionedSolveSync optional. Native improvement is now replicated in this
environment, while facts and these larger symmetric workloads do not benefit.
Next useful measurements are tuple-group count, total tuples and group entries
visited by the old scan, followed by a workload with many distinct tuple groups
and a transaction lifecycle comparison. Do not enable the flag globally from
native results alone. Original latent-domain topology and dynamic TSolve index
remain distinct optimization boundaries.

Raw evidence: `latent-substitution-evidence/solve-sync-replication/`.
Existing 123-case/core lifecycle qualification is from the preceding checkpoint;
no core code changed here. New workloads were checked against the old path and
factory-verify as described above. Full Maven / canonical Java 8 and 21 runs
remain outstanding. Live develop remains
`3ad50f1e5253304f6b530de11c078f332ea4db89`; no merge/release/tag/deploy.
