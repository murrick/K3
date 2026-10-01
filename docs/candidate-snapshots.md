# Candidate snapshot forensics

Live develop/3.8.0 verified at 1828d7164159cb04df00dedd5c27e18d556af4ea. The initial forensics checkpoint had no production changes; the prototype below is experimental and default OFF.

Java 17, all six integrated flags ON, two son(John,x) query/optimization samples. Diagnostic histogram instrumentation archived; source restored after compilation. Diagnostic times are not performance evidence. Counters cover all nested find calls during each phase, excluding compilation and rendered snapshots. Histogram bins: exact size 0..7, size >=8, total member visits if fully enumerated.

| Optimization sample | rules empty | rules singleton | rules size 2 | TValue empty | TValue singleton | TValue size 2 |
|---|---:|---:|---:|---:|---:|---:|
| 0 | 1,926,682 | 1,247,935 | 47,122 | 4,587 | 657,818 | 77 |
| 1 | 1,932,324 | 1,244,372 | 45,043 | 4,589 | 657,887 | 6 |

No bucket larger than two in this workload. Rule lookups: 98.5–98.6% empty or singleton. TValue lookups: >99.98% empty or singleton. This is workload-specific; hash collisions can produce arbitrarily large buckets in other inputs.

Escalera.find currently returns a fresh mutable HashSet, including empty results. Factories iterate it without explicit mutation. Nevertheless, hydration and equality callbacks may reenter runtime and mutate the backing index. A live collection is not a safe replacement. Multi-member HashSet copy iteration order also matters: the reference copied set can differ from backing-set order because capacity changes.

Next minimal candidate: retain public ICache.find semantics; use an internal immutable empty/singleton snapshot only for built-in factory iteration, falling back to the old copied HashSet for size >=2 and custom cache implementations. Capture the singleton ID before callbacks. Do not introduce persistent topology, new managers, or caching/invalidation complexity yet. Required checks: result ownership, mutations after snapshot acquisition, reentrant callbacks, collisions, custom cache fallback, rollback and hydration failures.

Both diagnostic runs retained 18 raw and six final hypotheses, zero solutions/values; final text snapshots match. The diagnostic stage did not change inference logic.

Preceding direct predicate-name experiment ca91a1e: all five workflows passed, including all six Java 8/21/26 × prior OFF/ON qualification jobs (run 36871542025). Allocation reduction remains 9.8–11.8%; elapsed speedup remains unproven. No merge performed.

## Compact snapshot prototype

Code checkpoint: 8c2d68e43826356187c0f2d21c22ec47049535da.
Flag `kanger.experiment.compactFindSnapshots`, default OFF.
Only RuleFactory.find(Solve), RuleFactory.find(IRule) and TValueFactory.find use the internal helper. Public ICache.find retains independent mutable Set ownership. Only exact Escalera instances use the shortcut; subclasses/custom caches call their original find once. Empty results use an empty list; singleton ID is captured into a singleton list before candidate callbacks; two or more IDs retain the original HashSet copy and order. No index cache/invalidation or persistence changes.

23 boundary checks passed OFF/ON: empty/singleton snapshots survive additions, public result mutation cannot affect backing index, multi-member iteration matches reference copies, snapshots survive rollback/root reset and reentrant collision additions, custom find identity/order/call count/exception identity retained. Regression corpus and 20-operation transaction runner pass both modes; transaction states byte-identical. Dedicated CI run 36877401817: all six Java 8/21/26 × prior flags OFF/ON jobs succeeded; each tests new flag OFF/ON, cross-context projection, corpus, transaction state equivalence and candidate concurrency.

Sequential six JVM benchmark, Java 17 Xmx512m, prior six flags ON, name optimization OFF, four samples per JVM, warm median of last three. All 24 raw and final hypothesis snapshots equal, with zero solutions/values; no benchmark stderr. Main-thread allocation is cumulative during optimizeHypothesis, not retained heap.

| Pair | OFF seconds | ON seconds | OFF MB | ON MB | Time reduction | Allocation reduction |
|---|---:|---:|---:|---:|---:|---:|
| 1 OFF/ON | 12.837 | 14.522 | 5254.4 | 4765.3 | -13.13% | 9.31% |
| 2 ON/OFF | 11.643 | 9.808 | 5352.5 | 4790.8 | 15.76% | 10.49% |
| 3 OFF/ON | 11.684 | 10.009 | 5353.0 | 4803.6 | 14.34% | 10.26% |

Allocation reduction is consistent, roughly 0.49–0.56 GB per optimization here. Time results favor ON in two pairs but reverse in one; large within-JVM jitter prevents a stable speedup claim. Do not merge/default-enable yet. Next: a focused repeat to determine elapsed-time stability, then assess a combined name+snapshot path only after independent evidence. Do not add percentage improvements from the two experiments.
