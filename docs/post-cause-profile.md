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

## Follow-up: resolve buckets once per position

The initial experiment above is checkpoint ee8359f. This follow-up retains the
same flags and reference verifier, but reads the three bucket references once
per argument position instead of looking them up for every selected ID. Those
references are local to the existing metadata write-lock region: none is returned,
stored elsewhere or modified. The selected snapshot remains independently owned.
The iterator's Long ID is also reused directly, avoiding unboxing/reboxing in
each membership check. The measurements combine these two changes; they do not
attribute the gain to either change independently.

Live develop was rechecked and remains 9d74bb0. Same Java 17 / 512 MiB setup,
three warmups and five samples per sequential JVM, sampling/allocation counters
OFF for timing. Comparison is the original union/copy path versus the revised
membership path in this tree, not versus the earlier membership prototype.

| Order | Original median seconds | Direct membership seconds | Reduction |
| --- | ---: | ---: | ---: |
| OFF then ON | 0.912561000 | 0.860297282 | 5.7% |
| ON then OFF | 0.853357724 | 0.794226614 | 6.9% |

All 20 measured results are 493/493. This is consistent local direction in both
orders, not a confidence interval or a universal/cold-start speedup claim.
Internal work counters remain subject to concurrent scenario variation.

Separate allocation runs (three warmups, three samples each) show median
main-thread cumulative allocation 921,174,616 -> 739,126,200 bytes, approximately
182.0 MB / 19.8% less. All six results are 493/493. These are not retained heap
or worker-thread allocations; timing of these runs is excluded. Do not compare
these absolute numbers across earlier JVM runs as a controlled head-to-head
measurement of the two prototypes.

The existing corpus passes locally in OFF/ON/verify. All 20-operation transaction
state projections match byte-for-byte across modes and the previous checkpoint.
The existing persistent-DUMB four-thread runner passes three verify iterations.
The verifier compares IDs and order after each constrained position. Logs show
no measurement exceptions/assertions. Evidence is direct-*.csv/.counts,
direct-*-state.txt/work.txt and direct-qualification.txt in post-cause-evidence/.

The revised variant warrants Java 8/21 qualification and a focused check of
empty buckets, fallback-only candidates, ordering and snapshot independence
before any integration decision. It remains experimental and default OFF.
