# Remaining performance investigation scope

Checkpoint: 2026-10-06, develop/3.8.0 remains `1c943d02d39bd33ebd91fabb7c4190112ab49459`. Eight integrated optimizations are enabled by default. Unmerged prototypes remain isolated and OFF. This is a bounded investigation backlog, not an exhaustive proof that no other optimization exists.

## Two remaining substantial directions

| Priority | Direction | Evidence and next boundary | Status |
|---|---|---|---|
| 1 | RuleFactory.findByResolvedDomain candidate hydration | Prior source census: 328,854 Rule reads cause 1,633,243 Domain selections per optimization, across 367 Rule objects. Measure candidate-list sizes, repeated selections and downstream use. Preserve every necessary binding effect, deletion check, order and promotion; repeated identity alone is insufficient. | Not yet investigated at this detailed caller/effect boundary; no qualified prototype. |
| 2 | HypothesisStore.optimize validation and query replay | Expanded RAW reconstruction plus per-candidate compile/link/analyze/replay performs large repeated work. Measure phase invocation counts and fresh warm CPU distribution on the current eight-default base. Retain candidate isolation and exact positive/negative/unknown result semantics before considering sharing or pruning. | Broader algorithmic investigation; no proposed semantic change or promised gain. |

The older sampled phase split suggested replay was a substantial fraction of optimization, but it predates later integrations and is not used here as a current speedup estimate. Candidate hydration counts are likewise work counts, not CPU shares. Once these two investigations produce a qualified candidate or a clear negative/safety result, close this micro-optimization round and reassess from a fresh profile or broader execution-model design. Do not keep trying local substitutions solely because a named method remains hot.

## What should not be repeated without new evidence

| Area | Current evidence |
|---|---|
| Factory.get and TVariable.isEmpty map-probe shortcuts | Probes removed as intended, opposite-order timing changes sign; no stable gain. Latest variable-emptiness prototype passes all 20 CI jobs but stays OFF. |
| Predicate name construction | Fresh String sharing reduces allocation on the measured workload; full SON timing is inconsistent and SET is slower in the measured batch. No general acceleration established. |
| Internal collector capacity/lazy allocation, temporary classification sets and hash-key reuse | Small or mixed allocation effects; no stable elapsed improvement demonstrated. |
| Same-context Domain/Rule early return | Exact built-in counterexamples change selected variable values. Outer Mind identity does not certify inner bindings. |
| Alpha scan metadata narrowing before hydration | Exact built-in counterexamples change matching/state when unrelated Rule hydration is skipped. |
| Deletion-chain skipping or result reuse based on nonexposure alone | Mutable IDs, concurrent deletion/context transitions and callbacks invalidate the proposed boundaries. |
| Repeated Domain.isStored result reuse in Linker | New census: 1,292,948 repeated checks with equal booleans, but native witnesses show that the skipped second lookup can lose binding effects or stale deletion state. No simple cache. |
| Redundant weak-reference reselection | Already investigated; lower allocation with mixed timing. Do not count that mechanism as a new untried direction without fresh evidence. |

The current [stored-lookup report](stored-lookup-forensics.md) records the new call census and safety witnesses. Prior [Rule-read evidence](https://github.com/murrick/K3/blob/experiment/3.8.0-rule-read-source-forensics/docs/rule-read-forensics.md) identifies the remaining resolved-domain route. No merge, default enablement or architecture rewrite is made by this checkpoint.
