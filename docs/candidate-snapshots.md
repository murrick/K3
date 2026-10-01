# Candidate snapshot forensics

Live develop/3.8.0 verified at 1828d7164159cb04df00dedd5c27e18d556af4ea. No production changes in this checkpoint.

Java 17, all six integrated flags ON, two son(John,x) query/optimization samples. Diagnostic histogram instrumentation archived; source restored after compilation. Diagnostic times are not performance evidence. Counters cover all nested find calls during each phase, excluding compilation and rendered snapshots. Histogram bins: exact size 0..7, size >=8, total member visits if fully enumerated.

| Optimization sample | rules empty | rules singleton | rules size 2 | TValue empty | TValue singleton | TValue size 2 |
|---|---:|---:|---:|---:|---:|---:|
| 0 | 1,926,682 | 1,247,935 | 47,122 | 4,587 | 657,818 | 77 |
| 1 | 1,932,324 | 1,244,372 | 45,043 | 4,589 | 657,887 | 6 |

No bucket larger than two in this workload. Rule lookups: 98.5–98.6% empty or singleton. TValue lookups: >99.98% empty or singleton. This is workload-specific; hash collisions can produce arbitrarily large buckets in other inputs.

Escalera.find currently returns a fresh mutable HashSet, including empty results. Factories iterate it without explicit mutation. Nevertheless, hydration and equality callbacks may reenter runtime and mutate the backing index. A live collection is not a safe replacement. Multi-member HashSet copy iteration order also matters: the reference copied set can differ from backing-set order because capacity changes.

Next minimal candidate: retain public ICache.find semantics; use an internal immutable empty/singleton snapshot only for built-in factory iteration, falling back to the old copied HashSet for size >=2 and custom cache implementations. Capture the singleton ID before callbacks. Do not introduce persistent topology, new managers, or caching/invalidation complexity yet. Required checks: result ownership, mutations after snapshot acquisition, reentrant callbacks, collisions, custom cache fallback, rollback and hydration failures.

Both diagnostic runs retained 18 raw and six final hypotheses, zero solutions/values; final text snapshots match. No semantic optimization has been implemented here.

Preceding direct predicate-name experiment ca91a1e: all five workflows passed, including all six Java 8/21/26 × prior OFF/ON qualification jobs (run 36871542025). Allocation reduction remains 9.8–11.8%; elapsed speedup remains unproven. No merge performed.
