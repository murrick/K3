# Invocation coverage and resolved lookup

Continuation of stage timing, parent `b2578e1`. No inference optimization here.

## Correcting the scope of the previous profile

Both benchmark queries execute **two sequential Linker invocations**:
CHECKFALSE, followed by CHECKTRUE. This was observed in every one of the
20 fresh-Mind runs (including warmups) and agrees with Mind.query: when the
false check returns null, the true check runs. These are distinct logical
checks, not evidence of redundant work that may simply be removed.

Mind.getLinkerStatistics exports the second invocation in these examples.
Every exported seven-slot timing array exactly matches the CHECKTRUE trace.
The previous 4-5% native selection/query ratio therefore covered only that
second invocation. It was not a whole-query selection cost. The previously
unattributed remainder included CHECKFALSE as well as other work.

## Instrumentation

With `kanger.experiment.timeStages=true`, slots 0..4 retain their original
meaning. Slot 5 measures findByResolvedDomain and is INCLUDED in selection
(slot 0). Slot 6 measures the whole Linker invocation and INCLUDES all stages.
Never sum all seven slots. The benchmark adds resolved_ns and invocation_ns.

`kanger.experiment.traceInvocations=true` prints completed status, QueryPass,
and stage timings for every invocation to stderr, including failures. Use
timeStages as well to obtain durations. Benchmark stderr markers distinguish
compile and query. Trace output itself perturbs whole-query time; separate
untraced runs are used for the resolved lookup profile. This is diagnostic
wall time, not CPU sampling. Failed inner sections remain omitted; the outer
invocation interval is recorded in finally. No timing influences inference.

## Measured coverage

Labelled trace run: factory mode, factoryCandidates and indexedIntersection
enabled, three warmups then seven samples per fixture. Median milliseconds:

| Fixture | CHECKFALSE Linker | CHECKTRUE Linker | Sum per query | Whole query |
|---|---:|---:|---:|---:|
| natives | 29.741 | 85.196 | 114.937 | 120.649 |
| 100 facts | 0.060 | 16.738 | 16.790 | 22.529 |

Median combined selection is 8.675 ms / 4.152 ms; combined resolved lookup
7.451 ms / 3.897 ms. Median time inside both invocations but outside the five
sections is 27.798 ms / 2.280 ms. Time outside both invocations is 5.844 ms /
5.755 ms, including diagnostic output. Separate medians need not add.
An earlier unlabelled coverage run independently observed the same invocation
counts and snapshot correspondence; both raw runs are preserved.

## Resolved lookup, without stderr invocation tracing

Separate JVM, same settings and warmup/sample counts. Exported CHECKTRUE
snapshot only; median milliseconds:

| Fixture | Selection | Resolved lookup | Per-sample difference | Median resolved / selection |
|---|---:|---:|---:|---:|
| natives | 5.802 | 4.966 | 0.824 | 85.13% |
| 100 facts | 4.820 | 4.479 | 0.341 | 93.54% |

The difference includes topology gates, counters, allocation, intersection and
timer overhead; it is NOT a pure intersection measurement. Resolved lookup
dominates this observed selection cost. RuleFactory recursively visits parent
indexes, resolves candidates, then gets each Rule and checks deletion. Its
generated-rule handling has existing semantic effects, so replacing the whole
call with topology pointers remains unsafe without preserving those effects.

Next useful steps: separate the remaining cost inside Linker (preparation and
variable rotation) and inspect resolved lookup's layer traversal / hydration.
No speedup is claimed and no CHECKFALSE bypass is proposed.

## Qualification

123/123 corpus cases pass with timeStages, factory-verify, factoryCandidates
and indexedIntersection. Complete stdout for all 16 state operations matches
the existing reference byte-for-byte, with stderr captured separately.
Evidence: `latent-substitution-evidence/invocation-coverage/`.

Toolchain remains OpenJDK 17.0.20 / ECJ 3.33 Java 8 target. Full Maven and
canonical Java 8 / 21 qualification have not been run. Live develop remains
`3ad50f1e5253304f6b530de11c078f332ea4db89`; only the experimental branch changes.
