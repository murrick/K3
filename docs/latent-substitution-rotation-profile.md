# Preparation, rotation and solve-index synchronization

Parent checkpoint `062c4f3`. Observation only; optional membershipFilter remains
off. No inference scheduling, tuple semantics or index maintenance changes.

## Measurement boundaries

timeStages adds slots 7..13 to LinkerStatistics, with existing reset/copy/add:

| Slot | Interval |
|---|---|
| 7 | Per-pass Rule selection, native expansion, sorting and shared groups |
| 8 | Rotator topology/mode setup |
| 9 | Per-Rule scratch clearing, gathering TVariables and initial used check |
| 10 | Top-level branch rotateVariables call, including all terminal callbacks |
| 11 | Complete terminal callbacks, including their internal sections 0..3 |
| 12 | isValidFor with tailSet construction, outside terminal callbacks |
| 13 | synchronizeSolveIndex, included in slot 12 |

Rotation outside callbacks is slot 10 minus slot 11. Its validation portion is
slot 12; remaining rotation overhead is 10 minus 11 minus 12. Slot 13 is nested
inside 12 and must not be added again. Invocation residual is
`6 - 7 - 8 - 9 - 10 - 4`. It includes pass/action bookkeeping, loop overhead,
unmeasured boundaries and timers. Exceptions can leave completed-section
statistics partial; timing analysis below uses successful invocations only.

Timers do not run when timeStages is off. Diagnostic stderr is emitted after
the inclusive invocation clock stops; whole-query time includes that output.
No timing result is used by inference.

## Evidence

Factory mode, factoryCandidates/indexedIntersection/timeStages/traceInvocations
on; membershipFilter/profileResolved and verification off during timing.
Three warmups then seven fresh-Mind measured samples per fixture, isolated JVM.
Every query again contains CHECKFALSE then CHECKTRUE. All computed exclusive
intervals and residuals are nonnegative in the measured samples.

Final profile, median milliseconds (each column computed per sample):

| Fixture / pass | Invocation | Preparation (7+8+9) | Rotation excluding callbacks | isValidFor | Included solve-index sync |
|---|---:|---:|---:|---:|---:|
| natives / CHECKFALSE | 25.789 | 0.750 | 5.473 | 4.677 | 3.871 |
| natives / CHECKTRUE | 70.673 | 1.820 | 15.902 | 13.700 | 11.081 |
| natives / both | 96.462 | 2.569 | 21.286 | 18.351 | 14.809 |
| 100 facts / CHECKFALSE | 0.057 | 0.038 | 0.000 | 0.000 | 0.000 |
| 100 facts / CHECKTRUE | 17.051 | 0.502 | 1.528 | 0.078 | 0.057 |
| 100 facts / both | 17.108 | 0.540 | 1.528 | 0.078 | 0.057 |

Combined callbacks: 70.975 ms native / 11.423 ms facts. Combined updateDatabase:
1.731 / 3.139 ms. Remaining rotation outside validation: 2.938 / 1.454 ms.
Invocation residual: 0.491 / 0.133 ms. Whole query: 101.434 / 22.625 ms.
Separate medians do not add exactly.

The first, coarser profile (before validation timers) is also preserved: native
combined invocation 136.179 ms, rotation outside callbacks 26.171 ms. This
variation between short JVM runs is not a speedup: the second change only adds
observation. GC/JIT/scheduling and timer overhead are not separately isolated.
Use these figures to locate work, not to promise performance improvement.

## Finding and next boundary

Preparation is small in these examples. Native variable rotation spends a
substantial part of its observed time synchronizing the solve index. In the
current code isValidFor calls synchronizeSolveIndex every time, even before
checking suffix size. Synchronization traverses all ruleSolves groups, compares
list sizes with indexedSolveCounts, indexes appended tuples and rewrites counts.
This is repeated discovery of changes in dynamic derived state. It is separate
from the compile/build latent domain topology.

The next investigation should audit all TSolve publication, clearing, rollback
and external mutation paths before proposing incremental maintenance or a safe
unchanged-generation check. Mind.addTSolve appends tuples, but the public
ruleSolves map/list surface must also be considered. An action flag alone is
not sufficient: prior experiment evidence includes new tuple relations without
new Rule or TValue actions. Keep the existing synchronization as oracle until
candidate availability and final state are shown equivalent.

## Qualification and checkpoint

Final code with timeStages and factory-verify passes all 123 corpus cases.
Complete stdout from all 16 state operations matches the existing reference
byte-for-byte; stderr captured separately. No full Maven / canonical Java 8/21
qualification: available toolchain is OpenJDK 17.0.20 with ECJ 3.33 Java 8 target.

Raw initial/final CSVs and both-invocation traces, final corpus and state output:
`latent-substitution-evidence/rotation-profile/`.
Live develop remains `3ad50f1e5253304f6b530de11c078f332ea4db89`.
Only experimental branch changes; no merge, release, tag or deploy.
