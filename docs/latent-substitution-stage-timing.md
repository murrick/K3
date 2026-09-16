# Opt-in Linker stage timing

Enable `-Dkanger.experiment.timeStages=true` before constructing Mind.
LinkerStatistics records five completed, non-overlapping sections of each
terminal callback / rule visit: candidate selection (including the existing
side-effecting resolved lookup), linkDomains, calcFunctions, linkDatabase,
and updateDatabase. The existing benchmark appends five CSV columns only
when this flag is enabled. Default execution makes no nanoTime calls.

Counters reset, snapshot and aggregate with the other operational statistics.
Exceptions retain existing handling; a stage interrupted by an exception is
not counted. These are wall-clock intervals, including pauses and any GC/JIT
inside them, not CPU samples. No inference decisions use these durations.

## Scope and limitations

Mind exports its existing last child Linker snapshot. This is not new tracing
of every transaction or every Linker invocation throughout a whole query.
The query clock additionally includes work outside that snapshot and outside
the five measured sections. In particular, the remainder must NOT be labelled
variable rotation time. It includes preparation, rotation, transaction work,
timer/counter overhead and other unmeasured work. Profiling that remainder and
checking invocation coverage is the next step.

## Observation

OpenJDK 17.0.20; ECJ 3.33 targeting Java 8. Separate JVMs, each three fresh-Mind
warmups then seven measured samples. Both timed modes use latent=factory and
factoryCandidates=true; only indexedIntersection differs. No verification or
other heavy diagnostic traces during timing. Median milliseconds:

| Fixture / mode | Whole query | Selection | linkDomains | Functions | linkDatabase | updateDatabase |
|---|---:|---:|---:|---:|---:|---:|
| natives / linear | 110.767 | 4.604 | 27.290 | 0.310 | 21.597 | 1.684 |
| natives / indexed | 103.852 | 4.695 | 25.177 | 0.301 | 22.097 | 1.425 |
| 100 facts / linear | 23.043 | 4.252 | 3.923 | 0.029 | 3.465 | 3.267 |
| 100 facts / indexed | 23.030 | 4.339 | 4.062 | 0.029 | 3.188 | 3.134 |

Medians of separate columns need not add to median whole-query time.
Median per-sample selection/query ratios are 4.42% / 4.65% for natives and
19.69% / 18.81% for the facts fixture (linear / indexed). These ratios describe
the exported snapshot's measured selection, not a proven whole-query cost
bound for all possible invocations.

Indexed mode with timers disabled in a separate control JVM has query medians
121.920 ms / 23.306 ms. This noisy control does not isolate timer overhead;
it reinforces that these small samples cannot establish a speedup. All three
runs have identical row, domain-pair and unification counts per sample.

Selection itself shows no measured improvement from indexed intersection.
This supports investigating costs inside the resolved lookup and the large
unattributed portion before another candidate-loop optimization. All
experimental options remain off by default.

## Qualification and evidence

With timers, factory-verify, factoryCandidates and indexedIntersection enabled,
all 123 corpus cases pass. All 16 state operations match the previous oracle
stdout byte-for-byte after excluding the runner's tab-separated timing rows
from stderr (captured together in this run). State projection includes values,
solutions, hypotheses, rules, effects and pass actions. No persistence format
or logical behavior changed. Full Maven / canonical Java 8 and 21 qualification
was not run with this available toolchain.

Raw CSVs, complete corpus log and state capture are in
`latent-substitution-evidence/stage-timing/`. Reproduce timings with
`LatentSubstitutionBenchmarkRunner`, the flags above and indexedIntersection
false/true; omit timeStages for the control. Run qualification separately,
before timing, to avoid competing processes.

Live develop was rechecked at `3ad50f1e5253304f6b530de11c078f332ea4db89`.
This checkpoint continues experimental parent `948f3df`; no develop changes,
merge, release, tag or deploy.
