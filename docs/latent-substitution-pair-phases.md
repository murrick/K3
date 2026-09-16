# Compatible pair processing: phase attribution

2026-09-16. Default-off diagnostic `kanger.experiment.profilePairPhases=true`. It records per invocation: pair count, preparation ns, compatibility guards ns, substitution ns, completion ns, and deferred-tuple ns. Statements and checkpoint order remain unchanged.

Preparation starts after predicate/polarity admission and includes pair arrays, diagnostics and TValue/FValue marks. Guards cover both directional constant checks. Substitution includes argument/value access, canonical TValue find/add, possible CVariable handling, Domain used markings and substitution-array writes. Completion includes commit/release, both markExcluded calls, Rule used markings and effect bookkeeping. Deferred tuple work covers the final variants loop and addTSolve. Timers exclude occurrence selection and loop overhead outside those boundaries; failed invocations may have incomplete phase totals and are marked completed=false.

Run: factory mode, other optimizations off, ten-predicate shared-path fixture at 50/100 edges. OpenJDK 17, 512 MiB heap; one warmup and three measured samples per size in one JVM, stage tracing and fingerprints enabled. Sum all phase arrays between query markers before taking medians. These are profiled attribution numbers, not uninstrumented timings or a comparison of optimization performance.

| Edges | Pairs/query | Preparation ms | Guards ms | Substitution ms | Completion ms | Deferred tuples ms | Whole query ms |
| --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| 50 | 89,110 | 9.791 | 16.242 | 2,388.695 | 96.255 | 48.885 | 3,637.302 |
| 100 | 318,210 | 32.807 | 50.299 | 8,695.693 | 366.646 | 149.332 | 12,998.252 |

Every measured sample has the stated pair count. Substitution is about 93–94% of the sum of displayed phase medians (not a per-sample percentage or all query work). Increasing graph size preserves this dominance. The data points inside the retained substitution loop rather than preparation or constant guards. It does not identify the dominant individual method within that loop.

Source inspection identifies at least two relevant paths: TValueFactory.find constructs a lookup value and visits a hash bucket, while Domain.setUsed consults context-specific argument histories through isUsed and may convert current arguments. Neither should be replaced without separate measurement and semantic analysis; canonical resurrection and used effects are existing semantics.

Qualification: the 16-operation state output with factory-verify and profiling is byte-identical to the old-path baseline. All six measured benchmark fingerprints, row counts and unification counts match the earlier old-path partitioned fixture. No incomplete query invocation appears in the parsed measurements. A full corpus was not rerun for this diagnostic-only addition; canonical Maven/Java 8/21 remain outstanding.

Evidence: [pair phases](latent-substitution-evidence/pair-phases/). Reproduce the prior partitioned benchmark command with profilePairPhases=true; keep timeStages, traceInvocations, benchFingerprint and direct benchOutput. Clock/branch overhead perturbs execution and is partly charged to phases, so absolute numbers are approximate.

Next bounded attribution: split time inside substitution between canonical value lookup/creation and used-domain history checks, without moving or skipping either operation. No optimization/default change follows from this checkpoint.
