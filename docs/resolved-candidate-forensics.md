# Resolved-domain candidate hydration: bounded investigation

Checkpoint: 2026-10-06. Production, qualification sources and workflows remain identical to develop/3.8.0 at `1c943d02d39bd33ebd91fabb7c4190112ab49459`. All eight integrated flags use their enabled defaults. This branch adds evidence only: no optimization flag, default change or merge.

**Outcome:** the measured resolved path is already selective. Only 8,887 of 328,854 returned Rule occurrences fall outside Linker's bucket (2.7024%). Native witnesses show that skipping those reads or replaying an earlier candidate list can change shared-variable selection even with an identical downstream candidate. No hydration-skipping prototype is qualified. Close this bounded local direction and proceed to the remaining validation/replay investigation; this does not prove that a broader execution-model redesign could not improve hydration.

## Scope and verification

The runner compiles `natives.k`, executes the ordinary logged `?$x son(John,x);` query, renders RAW hypotheses, then separately calls `optimizeHypothesis()`. Counters cover only that main-thread optimization interval, across all its internal Linker invocations. One clean JVM and two independent diagnostic JVMs each execute six samples. The first two samples in each JVM are excluded from warm census summaries. All **18 snapshots** match full RAW and optimized texts, unknown Boolean, 18 RAW / six optimized hypotheses, zero solutions and values, and the last-Linker statistics `passes=6 rules=1292 rotations=6824 pairs=13281 unifications=3080`.

All 34 counters, identity cardinalities, list-length histograms, parent-depth counts and binding-stage counts match between diagnostic JVMs at each sample index. The eight warm diagnostic samples also match one another. Instrumented elapsed, CPU and allocation measurements are excluded; these are work counts, not removable CPU percentages or a speedup estimate.

Temporary source copies instrument six production sources: Linker, RuleFactory, RuleCandidateIndex, Rule, Domain and TVariable. The original production files are unchanged. The patch retains original factory reads, metadata-lock boundaries, batching/used effects, deletion checks, ID getter order and all setters. It records IDs that the original expressions already evaluate. Extra collection-size reads operate on private native lists/ID sets. Identity maps retain observed objects until the interval ends; that retention and counter overhead make these runs unsuitable for performance comparison.

## Caller and downstream work

| Event per warm optimization | Count |
|---|---:|
| `selectDomainCandidates` calls | 858,101 |
| Branch Domain count differs from one; returns empty | 710,028 |
| Single-Domain branch with no opposite bucket | 188 |
| `findByResolvedDomain` calls | 147,885 |
| Final IDs / factory reads / returned Rule occurrences | 328,854 |
| Null or deleted final results | 0 |
| Empty resolved results | 0 |
| Bucket occurrences tested against allowed IDs | 933,350 |
| Retained occurrences passed into `linkDomains` | 319,967 |
| Resolved ID occurrences outside that call's bucket | 8,887 |
| Calls with zero retained candidates | 2,348 |
| Domains whose selection is requested, by identity | 2,191 |

Every observed resolved factory is exactly `RuleFactory.class`. Runtime Domain class coverage was not measured. IDs are unique within each returned call in this census, so returned occurrences minus retained occurrences equals the outside-bucket count. The outside-bucket calculation uses the original returned/bucket ID reads, rather than additional Rule getters.

The existing intersection removes 613,383 bucket occurrences (65.7184% of bucket visits) before linking. That is filtering already performed by the current code. The separate 8,887 outside-bucket occurrences are Rules read by the resolved factory and subsequently unused as candidates. Neither count proves that the corresponding reads are side-effect free.

All observed buckets, resolved lists and filtered lists have at most 29 entries. The most frequent length tuple is `(bucket=3, resolved=1, returned=1, filtered=1)`, occurring 40,150 times. The full 129-bin warm histogram is in [summary.json](resolved-candidate-forensics/summary.json). This is not a large-list scan in this workload.

Across the entire optimization interval, downstream linking visits 319,967 candidate Rules, traverses 1,599,204 Domain pairs and enters 367,807 unification attempts. Those totals cover many Linker invocations; the oracle's last-Linker statistics cover only the final invocation.

## Two hydration stages and existing memoization

| Read stage | Factory reads | Distinct returned Rule objects | Repeated object reads | Rule.setMind | Domain.setMind | TVariable.setMind |
|---|---:|---:|---:|---:|---:|---:|
| Final resolved result hydration | 328,854 | 367 | 328,487 | 328,854 | 1,633,243 | 2,779,801 |
| Generated-pair batch summary computation | 36,402 | 2,012 | 34,390 | 36,402 | 58,204 | 46,939 |

Distinct identities are measured independently for the two stages; their overlap is not measured. A repeated read means the object was returned earlier anywhere in this optimization interval. It does not certify the same Mind, unchanged variable bindings, adjacent reads, unchanged IDs or unchanged visibility. The final stage reproduces the prior [Rule-source census](https://github.com/murrick/K3/blob/experiment/3.8.0-rule-read-source-forensics/docs/rule-read-forensics.md): 328,854 reads, 367 objects and 1,633,243 Domain selections.

Parent collection traverses 394,540 local index layers: depth 1 has 147,885 entries, depth 2 has 147,885, and depth 3 has 98,770. Summed across those layers, signature snapshots contain 995,012 ID occurrences; positional filtering leaves 880,929. Batch removal excludes 552,075 occurrences, leaving 328,854 final IDs after parent aggregation. Layer counts are not global distinct IDs.

Of 207,124 nonempty, batch-eligible local selections, 199,776 already have a batch summary and 7,348 need computation: **96.4524% memo hits**. The current implementation therefore already avoids most repeated batch computation. All original used effects and memo publication remain in place in the diagnostic. No cache extension is proposed.

## Binding witnesses

[ResolvedCandidateWitness.java](resolved-candidate-forensics/ResolvedCandidateWitness.java) creates exact native Mind, RuleFactory, Rule, Domain, Predicate, Argument, TVariable, TValue and Term objects. A controlled native cache fixture contains two compatible Rules. Linker's upstream index contains only one; the other owns a shared variable. The fixture first resolves both Rules and initializes the index, then selects the variable in a child Mind with a different value.

| Scenario | Ordinary selection | Modeled shortcut |
|---|---|---|
| Direct variable selection between calls; hydrate only upstream IDs | Same retained Rule; variable selects root value `a` | Same retained Rule; variable remains child value `b` |
| Another Domain selects the shared variable between calls; hydrate only upstream IDs | Same retained Rule; root `a` | Same retained Rule; child `b` |
| Reuse earlier resolved IDs without rehydration | Same retained Rule; root `a` | Same retained Rule; child `b` |

Each reference arm invokes the real private `buildDomainIndex` and `selectDomainCandidates` methods. The shortcut arms model the proposed skipped hydration and produce the same downstream singleton; they are not a production implementation. No custom callbacks, subclasses or concurrency are needed for the divergence. This establishes a counterexample to the suggested generic boundary, not a claim that those exact transitions occurred in the measured SON trace or a qualification of the public publication pipeline.

Both clean and diagnostic builds pass three scenarios / **35 checks**, with byte-identical output and identical witness class hashes. The first witness attempt incorrectly expected a logically deleted native Rule to rehydrate its variable. Native visibility prevented that hydration; its failed logs and [correction](resolved-candidate-forensics/witness-correction.md) are retained and excluded from qualification. The final witness makes no deleted-Rule hydration claim.

## Decision and reproducibility

No pre-hydration upstream narrowing, object-identity early return or candidate-list replay is introduced. A container-only substitution would still execute the measured hydration and would need to preserve the separation between all factory reads and later Rule ID reads. This investigation provides no evidence of a material gain from that substitution; it does not open another micro-variant loop.

The remaining bounded direction is [HypothesisStore validation and query replay](remaining-optimization-directions.md). Any future hydration redesign needs an explicit model of context-selection effects rather than treating canonical object identity as permission to skip them.

From this worktree, with Java 17 and ECJ targeting Java 8:

```sh
python docs/resolved-candidate-forensics/build.py
python docs/resolved-candidate-forensics/witness.py
python docs/resolved-candidate-forensics/run.py
python docs/resolved-candidate-forensics/analyze.py
```

Build hashes show 11 changed shared classes confined to the six instrumented sources, including Linker and RuleFactory anonymous classes. Anonymous-class bytecode differences were not separately decoded. New helpers/runners are diagnostic-only. Production, qualification and workflow diffs against the base are empty, so no new compatibility CI run is needed for this evidence-only branch.

[Manifest](resolved-candidate-forensics/manifest.json), [temporary-source patch](resolved-candidate-forensics/instrumentation.patch), [columns](resolved-candidate-forensics/columns.json), full compressed logs and analysis scripts accompany the report. Logs are compressed only after each JVM exits.
