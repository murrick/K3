# Internal unit-state reads: guarded prototype

This experiment removes unnecessary exposure of authoritative deletion/restoration maps from four internal read paths. It is a foundation for further research; the uninstrumented benchmark establishes no repeatable speedup. It does not cache deletion results. The experiment remains **OFF by default**, with no integration into develop.

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

On 2026-10-06 all five workflows for the code checkpoint completed successfully: distribution, general CI, qualification isolation, server, and the dedicated matrix. All 20 jobs passed. The dedicated matrix covers Java 8/21/26, prior flags OFF/ON, and this flag OFF/ON. Run IDs, job names, and observed statuses are in `internal-unit-state-reads/ci-status.json`.

Evidence-only checkpoints use `[skip ci]` to avoid duplicate workflows; production, qualification, and workflow files remain identical to the successful code checkpoint. Keep the experiment OFF: qualification passed, but performance does not justify default enablement.

## Uninstrumented performance qualification (2026-10-06)

Six fresh JVMs in clean → OFF → ON → ON → OFF → clean order, with six samples each and the first two excluded. Java 17.0.20, `-Xmx512m`, all eight integrated flags ON. The reference restores the four changed production sources from the base commit; the runner class is byte-identical. These classpaths contain no diagnostic counters, JFR, or stack sampling. Main-thread CPU and allocated bytes come from ThreadMXBean. Results describe the separate optimization phase of the normal logged `?$x son(John,x);` fixture, not whole-query or application throughput.

| Sequence | Mode | Median wall, s | Median main CPU, s | Median allocated MB (decimal) |
| --- | --- | ---: | ---: | ---: |
| 1 | clean | 5.208 | 5.093 | 4807.8 |
| 2 | OFF | 5.818 | 5.678 | 4916.1 |
| 3 | ON | 5.957 | 5.819 | 4731.2 |
| 4 | ON | 5.565 | 5.441 | 4817.8 |
| 5 | OFF | 5.664 | 5.524 | 4764.4 |
| 6 | clean | 5.711 | 5.579 | 4812.1 |

ON versus OFF is 2.38% slower forward and 1.75% faster reverse; main CPU follows the same direction (2.49% slower / 1.51% faster). Allocation decreases 3.76% forward but increases 1.12% reverse. ON versus clean is 14.37% slower forward and 2.56% faster reverse. The two clean controls differ materially. This bounded run therefore supports no repeatable timing or allocation benefit, and is not a statistical performance guarantee.

All 36 raw/final snapshots and linker statistics match the oracle; the runner requires the unknown Boolean result and six optimized hypotheses, and the analyzer requires 18 raw hypotheses, zero solutions/values, and empty stderr. Class hashes, logs, sample medians, and pair comparisons are retained under `internal-unit-state-reads/benchmark`.

Reproduction: local build, `python docs/internal-unit-state-reads/benchmark/build-reference.py`, `python docs/internal-unit-state-reads/benchmark/run-benchmark.py`, then `python docs/internal-unit-state-reads/benchmark/analyze.py`. Run the boundary witness separately from timing runs.

## Closed-map mutation boundary

`benchmark/ClosedMapMutationWitness.java` stages a writer between the two deletion expressions, using latches to make the interleaving deterministic. Exact built-in Mind, TVariable, and Argument instances are used. The witness never obtains deletion/restoration maps or installs custom sets. It does not instrument or modify the production collector.

Three separate scenarios change deletion via `setUnitDeleted`, unit identity via `TVariable.setId`, and ancestry via `Mind.setNext`. In each, the first call returns false and the second true: the original conjunction rejects the variable while reusing the first result accepts it. All 24 checks passed with the experiment OFF and ON. This proves nonexposure alone is insufficient; it does not assert that this interleaving occurred in the performance fixture.

Source audit additionally finds state changes in commit merge, rejection restoration, `clearMind`, and `pack`; mutable Argument binding/hydration and imported TVariable identity must also be considered. A map epoch alone would miss identity, reference, and topology changes. Any future reuse must preserve custom callbacks, original exception identity, and visibility at the second call, or be restricted by a separately proved execution contract. No such contract is introduced here.

Reproduce after the local build: `python docs/internal-unit-state-reads/benchmark/run-mutation-witness.py`.
