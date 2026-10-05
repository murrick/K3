# Alpha-equivalent rule scan forensics

Baseline `develop/3.8.0 @ 1c943d02d39bd33ebd91fabb7c4190112ab49459`, fetched and rechecked 2026-10-05. Branch `experiment/3.8.0-alpha-scan-forensics`; parent diagnostic `a8b0292fc68ff40b973b3deb4fa3de26d653c860`. Tracked production, qualifications and workflows remain identical to live develop. All eight integrated optimizations retain default ON. No optimization or merge is proposed.

## Warm counters per son(John,x) hypothesis optimization

Two sequential fresh Java 17 JVMs, six samples each; discard first two in each from warm counters. All stage counters and scan histograms are identical across all eight warm samples. Count the main optimization thread only; compilation, the original query and rendering are outside the counter window.

| Stage | Count |
|---|---:|
| findAlphaEquivalent calls | 11,793 |
| Query source: rejected without scan | 23 |
| Source without C-variable: rejected without scan | 122 |
| Eligible scan calls | 11,648 |
| Successful scans | 9,828 |
| Unsuccessful scans | 1,820 |
| Total hydrated Rule visits | 829,545 |
| Non-stored Rule: rejected | 27,614 |
| Query Rule: rejected after stored check | 6,341 |
| Opposite polarity: rejected | 153,926 |
| Different predicate: rejected after polarity check | 547,116 |
| Different arity: rejected after predicate check | 0 |
| Argument comparison attempts | 94,548 |
| Argument positions inspected | 109,187 |

The 701,042 polarity/predicate/arity rejects account for 84.51% of all Rule visits. Including stored/query rejects, 88.60% of visited Rules do not reach argument comparisons. These are stage count shares, not CPU shares or available speedups. Every counted Rule visit occurs **after** ordinary cache hydration and Rule/Domain binding.

Argument comparisons reject 32,342 candidates on C-variable kind, 52,377 on concrete Term equality and one on C-variable bijection. Empty arguments and domini mismatch cause zero rejects in this workload. The remaining 9,828 comparisons produce matches. No exceptions occurred. Calls, gates, visits, rejection stages, outcomes and weighted scan-length histograms reconcile in every sample.

| Scan outcome | Calls | Visits | Mean visits | Median | p95 | Maximum |
|---|---:|---:|---:|---:|---:|---:|
| Found equivalent Rule | 9,828 | 557,711 | 56.75 | 59 | 94 | 159 |
| No equivalent Rule | 1,820 | 271,834 | 149.36 | 146 | 175 | 242 |

These histograms count the loop length before its existing return, not distinct candidate sets. Successful scans stop at the first match; misses exhaust the iterator. An index must preserve first-match order and eligibility. This checkpoint does not measure how many rejection callbacks can safely be skipped.

## Three built-in counterexamples against narrowing before hydration

`AlphaScanWitness` uses exact Mind, RuleFactory, Rule, Domain, Predicate, ArgumentsList, Argument, TVariable, TValue and Term objects. No user callback or subclass is needed. The fixture assigns IDs and seeds the private factory cache through ICache.add via reflection to control iteration order; it does not exercise the full public rule publication pipeline. Both ordinary lookup and the narrowed comparison execute production code. Reflection invokes the unchanged private alpha comparator rather than duplicating its implementation.

A TVariable has value `a` in the root Mind and `b` in a child. A source Domain has a literal C-variable plus that shared TVariable. The target Rule has an alpha-equivalent literal C-variable plus term `a`. The iterator first visits another Rule with a different predicate whose Domain uses the shared TVariable. That visit reselects the TVariable into root before the source/target comparison. A metadata-narrowed lookup reads only the target, whose literal arguments do not reselect the shared variable.

| Fixture | Ordinary full scan | Narrowed scan |
|---|---|---|
| Stored unrelated Rule precedes target | Finds target; shared variable shows root `a` | No match; shared variable shows child `b` |
| Source predicate has no target | No match; shared variable shows root `a` | No match; shared variable shows child `b` |
| Non-stored unrelated Rule precedes target | Finds target; shared variable shows root `a` | No match; shared variable shows child `b` |

The third case matters even for a stored-status prefilter: ordinary iteration binds the non-stored Rule before rejecting its flags. The first and third cases change the returned match, not just diagnostic callback counts. The second changes visible state despite identical null results.

All three scenarios pass 45 checks on a clean uninstrumented base and separately on the instrumented build, with identical witness output and empty stderr. These witnesses disprove an unconditional predicate/stored/shape index that bypasses unrelated Rule hydration. They do not prove that every guarded optimization is impossible; a sufficient binding/effect qualification boundary has not been established.

## Method and validation

Only a temporary copy of GeneratedCVarMaterializer is instrumented. Boolean probes return the original evaluated value, retain short-circuit evaluation and introduce no additional semantic getters. Constant-name events record stages without inspecting semantic objects. A stack of diagnostic scan frames handles nested calls and tracks outcomes/lengths in finally. Original hydration, effective views, flags, comparator reads, argument callbacks and exception paths remain in place.

All 12 complete RAW/OPTIMIZED texts match the prior clean baseline reference `e5a6f6608df967491dfb86295e6542cc84c7e8bf:docs/active-mind-reuse-eight/reuse-1-reference.log`: 18 raw and six optimized hypotheses, no solutions or values. Last Linker statistics are identical across samples: passes=6, rules=1292, rotations=6824, pairs=13281, unifications=3080. All profile stderr files are empty. These observations qualify the diagnostic evidence, not a new optimization or concurrency behavior.

Instrumentation adds maps, counters and scan frames and changes allocation/scheduling. **Recorded elapsed/CPU/allocation values are excluded from performance conclusions.** No timing experiment or production flag is introduced. General regression CI is not requested for this diagnostic-only checkpoint.

## Decision and next candidate

Reject a simple narrowed alpha scan that skips ordinary reads of irrelevant Rules. Metadata mismatch does not make the effects of hydration irrelevant. Do not replace the current iterator with an unbound cache read or ordinary candidate-index result on this evidence.

Return to the smaller proposal in the earlier rule-read report (`248d1e8be2cb7146553dc9dcced744e47a1c34a1`): lazy allocation of the internal variable list used by Domain binding. It can aim to avoid an empty temporary list while retaining the complete argument/deletion traversal and only binding variables after enumeration finishes. Public getTVariables must still return a fresh mutable owned result; custom list overrides need the original virtual path. Both deletion calls, getObject evaluation before duplicate checks, nested functions, callback order and exception identity remain requirements. This candidate is not implemented or timed here.

## Reproduce

From repository root, with Java 17 and external ECJ at `../tooling/ecj.jar`:

```sh
python docs/alpha-scan-forensics/instrument.py
python docs/alpha-scan-forensics/run.py
python docs/alpha-scan-forensics/witness.py
python docs/alpha-scan-forensics/analyze.py
git diff --exit-code origin/develop/3.8.0 -- kanger kanger-qualification .github
```

ECJ targets Java 8. Temporary source/classes and clean witness classes are under `../build/alpha-scan-source`, `../build/alpha-scan-classes` and `../build/alpha-scan-reference`. Runner subprocesses have explicit deadlines and validate log completeness. Full source patch, helper, runners, analyzer, stage/length logs, both witness outputs and summary are in `alpha-scan-forensics/`.
