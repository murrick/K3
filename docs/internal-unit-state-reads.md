# Internal unit-state reads: guarded prototype

This experiment removes unnecessary exposure of authoritative deletion/restoration maps from four internal read paths. It is a foundation for further research, not a measured speedup or a deletion cache. The experiment remains **OFF by default**, with no integration into develop.

Base: `develop/3.8.0 @ 1c943d02d39bd33ebd91fabb7c4190112ab49459`.
Tested code / CI checkpoint: `1b9515c74d7ed49fa2473a93c9f0b913854d3a50`.
Property: `kanger.experiment.internalUnitStateReads` (default `false`).

## Implementation and boundaries

Four production files change: `Mind`, `PortableMindLayer`, `DictionaryFactory`, and `Rule`. Exact `Mind` instances may use private maps for child-state merge reads, local owned copies, packing deleted IDs, and local restoration membership. `IMind` and the authoritative public `getDeleted` / `getRestored` contract remain unchanged.

Subclasses and custom implementations retain virtual getter calls, their ordering, mutable-ID behavior, and exception identity. Copies remain owned and mutable; absent sets and null-set failure behavior are preserved. Custom collection receivers can retain the source set through `addAll`, so they explicitly expose the map before that callback. Only the exact internal `LinkedHashSet` receiver takes the nonexposing packing path. Visibility writes still use the original authoritative getters.

No deletion calls, membership probes, synchronization, or ancestor traversal are removed. There is no result cache, epoch, version scheme, or claim of immutable state.

## Local qualification

Java 17.0.20, ECJ Java-8 target. Eight integrated flags ON; new flag tested OFF and ON in separate JVMs.

- New boundary runner: 40 checks in each mode, including exposed-map mutation, owned copies, custom callbacks, retaining receivers, virtual getter counts, mutable rule IDs, and exception identity.
- Compact snapshots, resident comparison, single-value lookup, reopen, substitution corpus, transactions, and candidate concurrency: PASS in both modes.
- Transaction states equal; all 16 runner invocations passed with empty stderr.

Reproduction: `bash docs/internal-unit-state-reads/build-local.sh`, then `python docs/internal-unit-state-reads/qualify.py`.

## Exposure guard diagnostics

Four fresh JVMs: OFF → ON → ON → OFF. Six samples each, first two excluded from warm counts. The observer begins before User creation and compilation. It marks each temporary Mind with a boolean on main-thread public getter exposure; it retains no owner registry or Mind references. This is diagnostic instrumentation, not production state tracking.

| Observation per warm optimization | OFF | ON |
| --- | ---: | ---: |
| Deletion calls | 17,573,710 | 17,573,710 |
| Traversed layers | 46,897,159 | 46,897,159 |
| Calls whose traversed chain had no observed map exposure | 0 | 17,573,710 |
| Coverage of that condition | 0% | 100% |

All 24 complete result snapshots match the fixture oracle: 18 raw hypotheses, six optimized, unknown Boolean result, zero solutions/values. All 16 warm caller/deletion observations preserve the previous totals, types, depth histograms, and outcomes. Both original deletion expressions still execute. Rebuilt diagnostic class files exactly match those used in profiling; hashes are recorded alongside the evidence.

This shows that these internal reads were responsible for the fixture's observed exposure. Nonexposure alone does **not** prove immutability, thread safety, stable unit IDs, or permission to suppress repeated calls. Custom sets and arbitrary API callbacks remain observable. Diagnostic timing is not a speed measurement and no acceleration is claimed.

Reproduction: `python docs/internal-unit-state-reads/guard-profile/build.py`, `python docs/internal-unit-state-reads/guard-profile/run.py`, then `python docs/internal-unit-state-reads/guard-profile/analyze.py`.

## CI status and next boundary

The five workflows for the code checkpoint are queued in the repository's shared CI queue; cross-JDK qualification is pending. The dedicated matrix covers Java 8/21/26, prior flags OFF/ON, and this flag OFF/ON. Run IDs and observed statuses are in `internal-unit-state-reads/ci-status.json`. Local success is not cross-JDK approval.

This evidence-only checkpoint uses `[skip ci]` to avoid adding duplicate workflows; the already queued code workflows remain required. Keep the experiment OFF. Before another optimization, establish a guard for actual mutation and all callback boundaries, then measure clean/OFF/ON runs without diagnostic counters.
