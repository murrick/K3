# Post-cause-selection profiling experiment

Base: develop/3.8.0 at 9d74bb06c93dc27d416eee16fcffb238be577c87 (PR 143).
All six post-merge workflows passed before this experiment. Production defaults
are unchanged. This branch is not an integration proposal.

## Fresh baseline

Core, command, bootstrap, UDF, DUMB, console and historical test sources were
compiled from this tree into a fresh output directory with ECJ, source/target 8,
and lib/jline-3.13.0.jar. Execution used Java 17, -Xmx512m. No old experimental
classes were on the runtime classpath. The retained Set0802ProfileRunner removes
dependencies on unmerged rotation/cause-memo instrumentation.

All runs enable preserveTValueIndex, versionedSolveSync, resolvedCauseWeights,
and compactCauseWeights. Sampling uses two warmups and three measurements,
5 ms all-thread wall-stack observations, including worker threads. It is not CPU
attribution. Inclusive frames overlap and must not be added as percentages.
ORIGIN annotates the leaf with its nearest org.kanger frame, not a full call path.

Hash collection operations in RuleCandidateIndex.IdIndex.get and
collectResolvedLocal recur in the profile. The second sample run attributes 20
HashMap.putVal leaves, 13 AbstractCollection.addAll leaves and 10 HashMap.hash
leaves to IdIndex.get. This motivates examining temporary bucket copies; it
does not by itself establish an optimization benefit. All six sampled results
are 493 solutions / 493 values, with 986 eligible cause-weight selections and
zero fallback selections on the main thread. Sampling times are not benchmarks.

## Minimal membership prototype

Flags, both default OFF:

```text
-Dkanger.experiment.candidateMembershipFilter=true
-Dkanger.experiment.verifyCandidateMembershipFilter=true
```

The second flag enables comparison with the reference and is omitted for timing.
Only collectResolvedLocal changes. The initial signature snapshot remains an
independent LinkedHashSet. Under the existing metadata write lock, each selected
ID is tested against exact, wildcard, and fallback buckets instead of constructing
their temporary union. Removal preserves selected order. Index bucket references
do not escape and stored buckets are never mutated by selection. Resolution,
hydration, batching side effects, versioning, journals and lock scope are retained.
collectLocal and the OFF reference remain available unchanged in behavior.

Verify compares ordered ID lists after every constrained argument position.
Local OFF/ON/verify each pass the existing 123-case corpus and 20-operation
transaction fixture; the state files compare byte-for-byte. Three iterations
of the existing persistent-DUMB four-thread concurrency runner pass in verify.
This is Java 17 experimental qualification, not a new Java 8/21 qualification.

## Measurements

Whole unmodified set_08_02 method, including publishing workers and formatted
output redirected to a file. Four sequential JVMs, three warmups and five
measurements each; no concurrent benchmark or compilation. The only mode change
is candidateMembershipFilter. Shadow verification and sampling are OFF.

| Order | OFF median seconds | ON median seconds | ON change |
| --- | ---: | ---: | ---: |
| OFF then ON | 0.926656555 | 0.956694696 | 3.2% slower |
| ON then OFF | 0.906501913 | 0.888894955 | 1.9% faster |

There is no consistent timing win. All 20 results remain 493/493. Concurrent
internal rule-visit/rotation counts can vary; these are not identical-work claims.

Separate allocation runs, three warmups and three measurements each, show
main-thread median cumulative allocated bytes falling from 906,657,920 to
839,819,880: 66.8 MB / 7.4%. All six results are 493/493. These counters exclude
worker allocations and are not retained heap. Allocation-run timings are excluded.

Conclusion: temporary-copy reduction is measurable in allocation but the current
prototype has not demonstrated a time benefit. No merge or default activation.
A possible next experiment is to avoid repeated keyed bucket lookup per ID while
keeping bucket reads strictly inside the existing lock; measure before choosing
that direction. Do not broaden lock scope or expose live bucket views.

Raw CSV, stack observations, allocation counters, qualification summary and
transaction projections are in post-cause-evidence/. The historical runner is
org.kanger.test.Set0802ProfileRunner; its first argument is the CSV output path,
with bench.warmups, bench.samples, bench.sample and bench.allocations properties.
