# Active Mind weak-reference reuse on the eight-optimization base

Base: `develop/3.8.0 @ 1c943d02d39bd33ebd91fabb7c4190112ab49459` (live checked 2026-10-04).

Requalifies only the minimal TVariable.setMind delta from the older experiment. `kanger.experiment.reuseActiveMind` remains default OFF. All eight integrated optimizations retain their current defaults.

For an exact TVariable, reuse the selected thread-local WeakReference when its live referent is the requested Mind. Changed or cleared references are replaced; subclasses retain reference behavior. Owner qualification still executes on every call, in the original order, including custom getNext/getId callbacks and reentrant changes. No strong Mind retention, persistence change, or shared cache.

ActiveMindReuseRunner checks bindings, root/child owner selection, weak-reference clearing, null selection, sibling-thread isolation, custom variable callbacks, exception identity, callback counts and reentrant selection (23 checks). CI covers Java 8/21/26, prior eight OFF/ON, reuse OFF/ON, corpus/reopen/projection/transactions/concurrency and existing boundary runners.

## Qualification

Local Java 17, eight defaults ON, reuse OFF/ON: focused runner (23), resident comparison (48), single lookup (15), compact snapshots (23), reopen, regression corpus, 20 transaction operations and three concurrency iterations all passed. All stderr files empty; transaction states byte-identical and reopen stdout identical. Corpus checks its expected results internally; full stdout includes timing and concurrent enumeration order and is not byte-identical across runs.

Specialized CI run [37179874515](https://github.com/murrick/K3/actions/runs/37179874515) passed all six jobs: Java 8/21/26 × prior eight OFF/ON, each checking reuse OFF/ON including projection and concurrency in both modes. All five workflows passed on production checkpoint `d167b0d`: specialized qualification, general CI (10 Java 8/21 jobs), qualification isolation, distribution bundle and server. CI status is saved separately.

## Fresh measurement

Six fresh sequential JVMs: clean / OFF / ON / ON / OFF / clean. Java 17, 512 MB heap, all eight integrated flags explicitly ON, no diagnostic/JFR counters. Each JVM has six samples; discard its first two and report median of last four. Clean reference compiled from the exact base production; only TVariable differs in experimental production. SonProfileRunner bytecode is identical. Full 36 raw and optimized texts agree: 18 raw / six optimized hypotheses, no solutions or values in this hypothesis workload.

| Run | Wall s | Main CPU s | Main allocated MB |
|---|---:|---:|---:|
| Clean 1 | 4.527 | 4.439 | 4813.4 |
| OFF 2 | 4.645 | 4.542 | 4807.5 |
| ON 3 | 4.464 | 4.372 | 4639.6 |
| ON 4 | 4.335 | 4.233 | 4684.6 |
| OFF 5 | 4.474 | 4.392 | 4848.8 |
| Clean 6 | 4.397 | 4.303 | 4801.2 |

Against clean base: wall reduction about 1.4% in both orders, allocated bytes 3.6% / 2.4%. Against same-build OFF: wall 3.9% / 3.1%, allocations 3.5% / 3.4%. The OFF path is itself slower than clean (about 2.6% / 1.8%), so the latter comparisons overstate the gain over production. Clean controls drift by about 2.9%; this small time benefit remains uncertain. Allocation is measured on the optimization thread, not retained heap or whole-process allocation.

Decision: preserve as a default-OFF experiment; do not extrapolate the older six-optimization timing, request merge, or enable by default from this batch. A controlled laptop comparison could decide whether the small benefit merits another flag/branch; current develop is unchanged.

Reproduce from repository root: `bash docs/active-mind-reuse-eight/build-local.sh`, `python docs/active-mind-reuse-eight/qualify.py`, `python docs/active-mind-reuse-eight/build-reference.py`, `python docs/active-mind-reuse-eight/run-benchmark.py`, `python docs/active-mind-reuse-eight/analyze.py`. ECJ jar is an external prerequisite. Full logs, scripts and class/source hashes are in the adjacent evidence directory.
