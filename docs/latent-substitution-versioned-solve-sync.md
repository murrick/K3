# Skip unchanged solve-index synchronization

Parent `d2ba62c`. Optional `kanger.experiment.versionedSolveSync=true` skips
the full ruleSolves group scan when its owned collection has not changed.
Default remains the reference scan. This is dynamic tuple-index maintenance,
separate from compile/build latent domain topology.

## Ownership and invalidation audit

ruleSolves is a final, per-Mind LinkedHashMap; internal publication occurs in
addTSolve after deduplication. A newly appended tuple increments a version;
returning an existing tuple does not. Transient cleanup clears the map and
increments the version, then replaces Linker. At link entry the map and
Linker's derived index are cleared together; the synchronization baseline is
reset. No Rule/TValue action flag is used to infer tuple changes. This matters
because new relations can appear without new Rule/TValue actions.

The public getRuleSolves returns a mutable map and mutable lists. Calling it
sets a sticky exposure flag. For that Mind all future sync requests use the
reference scan, even after clearing: aliases can survive a clear. Internal
Linker reads use a package-private accessor, and supported internal writes
remain tracked. There is no wrapper collection, new manager or service.

Subclasses of Mind always fall back and retain virtual getRuleSolves access,
including in addTSolve/findTSolve and Linker. This protects custom-map getters.
The optimization does not establish new guarantees for concurrent unsynchronized
mutation, reflection, or future package-internal writes that bypass tracking.

No eager indexing on tuple publication is introduced. A changed version still
runs the exact old lazy synchronization, at the same validation point and in
the same group/list order. Ordinary hypothesis/checkpoint rollback behavior is
unchanged: this adds no journal and removes no tuple on rollback. Transient
state remains local to the existing Mind lifecycle.

## Reference verification and safety test

In factory-verify, every proposed skip runs the old synchronization too. The
indexed group counts and indexed tuple identity-set size must remain unchanged.
The reference method changes its candidate/unary indexes only when adding a
previously unindexed tuple identity, so this checks whether a skip omitted any
reference index update. Verification scans are diagnostic and are not counted
as normal scans in the four operation counters.

LatentSolveSyncSafetyRunner tests new tuple discovery, duplicate publication,
unchanged-version skipping, mutation through an exposed list without a version
change, an exposed map alias retained across clearing, and an overridden getter
in a Mind subclass. The tests check discovery of the externally appended tuple
and permanent fallback, not just counter arithmetic.

Corpus: 123/123 in factory-verify; 123/123 with actual skipping in factory mode.
Lifecycle passes, including storage reopen / replacement. All 16 state outputs
with actual skipping match the prior reference byte-for-byte. After adding the
conservative subclass guard, the focused safety test, verifying corpus and
actual-skipping state projection were rerun. Timings below were captured before
that guard; benchmark fixtures use exact Mind, whose behavior is unchanged.

## Work counts, both query-check invocations

All seven measured samples had identical counts:

| Fixture / phase | Sync requests | Reference scans | Versioned scans | Versioned skips |
|---|---:|---:|---:|---:|
| natives / CHECKFALSE | 2548 | 2548 | 13 | 2535 |
| natives / CHECKTRUE | 6652 | 6652 | 18 | 6634 |
| 100 facts / CHECKFALSE | 0 | 0 | 0 | 0 |
| 100 facts / CHECKTRUE | 300 | 300 | 1 | 299 |

No exposed-map/subclass fallback occurred in benchmark fixtures. Native scans
fall from 9200 to 31. The change avoids repeated map traversal, count hashing
and count writes; scans after real publication remain.

## Timings and interpretation

Separate JVMs, three warmups then seven fresh-Mind samples, factory mode,
factoryCandidates/indexedIntersection on; membershipFilter off. Median whole
query milliseconds without timers or invocation tracing:

| Fixture | Reference | Versioned |
|---|---:|---:|
| natives | 106.870 | 90.266 |
| 100 facts | 22.923 | 21.408 |

Separate timeStages/traceInvocations runs:

| Fixture / phase | Reference sync ms | Versioned sync ms |
|---|---:|---:|
| natives / CHECKFALSE | 4.202 | 0.364 |
| natives / CHECKTRUE | 12.203 | 0.823 |
| 100 facts / CHECKTRUE | 0.064 | 0.081 |

Profiled whole-query medians were 121.892 -> 126.265 ms native and 28.055 ->
33.980 ms facts. Thus the profiling runs confirm reduced synchronization work
and native stage cost, but do not reproduce a whole-query gain. Timers, JIT,
GC and run-to-run variance are not isolated. The roughly 15.5% native reduction
in the unprofiled sample is preliminary, not a demonstrated general speedup.
Rows, domain-pair and unification counts match all paired samples.

Keep the flag off by default. Next qualification should examine larger tuple
and transaction workloads and reproducibility of unprofiled timings, before
considering enabling it. The public alias fallback deliberately sacrifices
speed for compatibility.

## Reproduction and checkpoint

Evidence: `latent-substitution-evidence/versioned-solve-sync/`. Benchmark names
encode profile-enabled then versioned-enabled. Trace sync counters are requests,
normal scans, proposed skips, exposed-map/subclass scans; statistics reset,
snapshot and aggregate them normally.

Toolchain: OpenJDK 17.0.20 / ECJ 3.33 Java 8 target. Full Maven and canonical
Java 8/21 qualification not run. Live develop remains
`3ad50f1e5253304f6b530de11c078f332ea4db89`; no merge/release/tag/deploy.
