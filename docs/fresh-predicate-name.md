# Fresh predicate-name String experiment

Base: live `develop/3.8.0 @ 1c943d02d39bd33ebd91fabb7c4190112ab49459`, verified 2026-10-06.
Branch: `experiment/3.8.0-fresh-predicate-name`.
Code checkpoint: `049317a427b6ac11edc757a4ce4798039f026e12`.
Flag: `kanger.experiment.freshPredicateName`, default **false**.
No develop integration, default activation, or storage-format change.

## Distinct hypothesis

The previous `directPredicateName` experiment returned the existing String directly. It reduced allocation but did not establish consistent elapsed-time improvement and changed result identity. That variant is not revived here.

This candidate uses `new String(value)` for a current nonempty String name. It creates a distinct result object using the immutable String representation, rather than rendering the name through a StringBuilder and copying its character storage. It retains fresh result identity. Empty Strings remain on the original concatenation path because empty StringBuilder rendering has JDK-specific identity behavior. Nulls and all non-String values likewise keep the original conversion.

Only `Predicate.getName` changes. Every invocation still resolves the same name reference when necessary and reads `ITerm.getValue` once. There is no name cache, stale classification, suppressed custom value read, earlier hydration, or cross-context lookup. Custom object `toString` callback counts, null-return conversion, and original thrown exception instances are retained. Predicate overrides remain virtual. Mutating or replacing a name is observed on the next read. Public result identity is checked against the original Object-plus-empty-String expression rather than assuming empty Strings behave identically across JDKs.

## Local qualification

Java 17.0.20; ECJ Java-8 target. Eight integrated flags use their ON defaults; new flag explicitly OFF/ON in fresh JVMs.

FreshPredicateNameRunner passes 52 checks in each mode: mutable/custom values and callback counts; original exception identity; null, numeric, Boolean, StringBuilder and arbitrary rendering; Unicode, unpaired surrogate and NUL; nonempty and empty result/reference identity; name replacement, persistent-ID lazy hydration and missing-name failure. It compares repeated result identity to independently evaluated reference expressions.

Compact snapshots, resident comparison, single-value lookup, reopen, latent corpus, 20-operation transactions and three candidate-concurrency iterations pass in both modes. All 16 runner invocations succeed with empty stderr, and serialized transaction states are byte-identical.

Reproduction: `bash docs/fresh-predicate-name/build-local.sh`, then `python docs/fresh-predicate-name/qualify.py`.

## Performance protocol

Six sequential fresh JVMs: clean → OFF → ON → ON → OFF → clean. Six samples per JVM, first two excluded from warm medians. All eight integrated flags explicitly ON, `-Xmx512m`, Java 17.0.20. The normal logged `?$x son(John,x);` query is followed by separately measured `optimizeHypothesis`. Wall time, main-thread CPU, and cumulative main-thread allocation are measured; allocation is not retained heap.

The clean reference restores only Predicate.java from the base. Its SonProfileRunner class is byte-identical to the experiment's runner. No JFR, diagnostic counter or stack sampling is enabled. All complete raw/final result texts and linker statistics must match the established oracle. The runner requires an unknown Boolean result; the analyzer requires 18 raw / six optimized hypotheses, zero solutions and values, and empty stderr.

Reproduction: local build, `python docs/fresh-predicate-name/build-reference.py`, `python docs/fresh-predicate-name/run-benchmark.py`, `python docs/fresh-predicate-name/analyze.py`.

## Initial results

| Sequence | Mode | Median wall, s | Median main CPU, s | Median allocated MB (decimal) |
| --- | --- | ---: | ---: | ---: |
| 1 | clean | 5.449 | 5.337 | 4717.8 |
| 2 | OFF | 5.698 | 5.568 | 4854.7 |
| 3 | ON | 5.400 | 5.276 | 4344.5 |
| 4 | ON | 5.343 | 5.242 | 4294.0 |
| 5 | OFF | 5.845 | 5.716 | 4851.4 |
| 6 | clean | 5.568 | 5.460 | 4801.8 |

ON versus OFF reduces wall time 5.23% / 8.58%, main CPU 5.25% / 8.29%, and allocation 10.51% / 11.49%. Against clean, wall reductions are smaller at 0.91% / 4.03%, main CPU 1.13% / 4.00%, and allocation 7.91% / 10.58%. Both OFF controls are slower than their clean controls, so ON-versus-OFF alone overstates the case for integration. All 36 complete result snapshots and linker statistics equal the oracle.

A separate bounded confirmation uses clean → ON → ON → clean, again six samples per JVM with the first two excluded. Its purpose is to compare the candidate directly against develop without the OFF bytecode shape. Both classpaths are rebuilt before confirmation and every class hash must match the first measured build. Confirmation does not modify production or qualification code.

Reproduction: `python docs/fresh-predicate-name/run-confirmation.py`, then `python docs/fresh-predicate-name/analyze-confirmation.py`.

## Direct-clean confirmation and decision

| Sequence | Mode | Median wall, s | Median main CPU, s | Median allocated MB (decimal) |
| --- | --- | ---: | ---: | ---: |
| 1 | clean | 5.785 | 5.646 | 4804.1 |
| 2 | ON | 5.183 | 5.075 | 4165.2 |
| 3 | ON | 5.253 | 5.140 | 4292.3 |
| 4 | clean | 5.675 | 5.545 | 4811.4 |

Against the paired clean controls, ON reduces wall time 10.40% / 7.42%, main CPU 10.12% / 7.31%, and allocation 13.30% / 10.79%. All 24 confirmation raw/final snapshots and linker statistics match the same oracle, bringing the total to 60 snapshots. Stderr is empty throughout.

All four ON-versus-clean comparisons across the initial and confirmation sequences favor ON for time and allocation, but the initial timing effect was smaller (0.91% / 4.03%). The range is evidence of run-to-run variation, not a guaranteed speedup. This is a positive bounded candidate on local Java 17 for the optimization phase of this fixture. It does not establish performance on other JDKs, workloads, whole queries, or prior-flags-OFF configurations; the cross-JDK matrix qualifies behavior, not timing. Cumulative allocation reduction does not measure retained heap.

The prototype preserves a separate String result and has passed local and cross-JDK boundaries. It is suitable for review as a candidate, with no automatic integration or default enablement. The flag remains **OFF**. Full sample output, medians, pair comparisons, byte-identical rebuild hashes, and CI observations are retained under `fresh-predicate-name/`.

## Cross-JDK qualification

All five workflows / 20 jobs for code checkpoint `049317a427b6ac11edc757a4ce4798039f026e12` completed successfully: distribution, general CI, server, isolation, and dedicated qualification. The dedicated matrix covers Java 8/21/26 × prior eight flags OFF/ON, with this flag tested OFF and ON in each job. It includes the new 52-check boundary runner, regressions, cross-context predicate projections, transactions with equal state files, and concurrency. IDs and observed job statuses are in `fresh-predicate-name/ci-status.json`.

Evidence-only updates use `[skip ci]`; production, qualification and workflows remain identical to the successful code checkpoint.

## Broader workload qualification: decision update

The additional local Java-17 run uses all eight prior flags ON and separate clean → ON → ON → clean sequences for two workloads. Each JVM has six samples, first two excluded. `workloads/build.py` requires every original production class hash to match the earlier measured build. The common diagnostic classpath contains only qualification classes, compiled against the clean reference. Production, qualifications, and workflow sources remain unchanged from the successful CI checkpoint.

For **son**, a single interval contains the ordinary logged query, reading the raw hypothesis count, and immediate optimization. Compilation and final snapshot rendering occur outside it; there is no intermediate raw snapshot rendering. This defines the query-plus-optimization cycle, not application launch, compilation, UI rendering, or the differently shaped earlier separate-phase benchmark. All 24 final texts and linker statistics match the existing oracle; every sample has unknown result, 18 raw / six optimized hypotheses, zero solutions/values.

| Sequence | Mode | Wall s | Main CPU s | Main allocated MB |
| --- | --- | ---: | ---: | ---: |
| 1 | clean | 4.876 | 4.765 | 4770.8 |
| 2 | ON | 4.590 | 4.506 | 4374.2 |
| 3 | ON | 4.584 | 4.501 | 4314.1 |
| 4 | clean | 4.557 | 4.472 | 4898.0 |

Main allocation decreases 8.31% / 11.92%. Wall time decreases 5.87% forward but increases 0.59% reverse; main CPU decreases 5.43% / increases 0.64%. This confirms the allocation benefit on this cycle, while elapsed-time improvement is inconsistent. The two clean controls themselves differ noticeably.

For **set_08_02**, the runner invokes the original KangerTest method, preserving its four threads, query streams, commits, final read and stdout. User/storage setup and post-test cleanup are outside the interval; the native method's own workspace reset and result printing remain inside. CPU uses OperatingSystemMXBean process time, including all workers, GC and JVM compilation. Main-thread allocation is deliberately not presented as total allocation for this workload.

The native test permits either conflicting writer to roll back. State A contains groups 2/3/4 with x=0..163 plus 3:1003; state B contains groups 1/3/4 plus 2:7002. Both contain exactly 493 distinct pairs. The runner checks the true query result, 493 solutions and values, no hypotheses, and the full exact pair set against these independently constructed models. It does not force thread ordering.

| Sequence | Mode | Wall s | Process CPU s | Warm outcomes A / B |
| --- | --- | ---: | ---: | ---: |
| 1 | clean | 0.918 | 2.325 | 4 / 0 |
| 2 | ON | 0.947 | 2.315 | 4 / 0 |
| 3 | ON | 0.947 | 3.115 | 2 / 2 |
| 4 | clean | 0.887 | 2.235 | 3 / 1 |

Wall time increases 3.21% / 6.76%; process CPU decreases 0.43% forward and increases 39.37% reverse. The reverse pair mixes different rollback outcomes and cannot isolate the constructor's CPU effect. Even the first pair does not show a wall-time benefit. These bounded observations do not establish a universal regression or speedup. All 24 new set samples meet the exact permitted models, with empty stderr.

The first diagnostic attempt mistakenly required only state A. A writer-2 rollback produced state B: the native test passed, but the extra oracle failed. Its source, hashes, complete/partial outputs, and error are retained under `workloads/first-attempt/`; the entire incomplete sequence is excluded from the reported workload medians. The corrected sequence was rebuilt and rerun in full. This is a diagnostic oracle correction, with no production change.

**Updated decision:** allocation reduction is supported on the son cycle; broad elapsed-time benefit is not established and set_08_02 does not show an improvement. The earlier positive separate-phase timing result remains valid for its stated scope, and must not be generalized to ordinary execution. Keep `freshPredicateName` **OFF**, with no develop integration or default enablement on this evidence.

Reproduction: `python docs/fresh-predicate-name/workloads/build.py`, `python docs/fresh-predicate-name/workloads/run.py`, then `python docs/fresh-predicate-name/workloads/analyze.py`. Raw gzip outputs are compressed only after each JVM exits. `workloads/summary.json` retains exact medians, comparisons and both total/warm outcome counts.
