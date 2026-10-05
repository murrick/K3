# Lazy internal classification sets in Linker

Base `develop/3.8.0 @ 1c943d02d39bd33ebd91fabb7c4190112ab49459`, live verified 2026-10-05. Branch `experiment/3.8.0-lazy-classification-sets`. Flag `kanger.experiment.lazyClassificationSets`, default **false**, selected at JVM startup. No develop merge or default enable.

## Diagnostic lead

The warm eight-optimization profile identified local HashSet/HashMap allocation inside linkDatabase. A separate diagnostic copy of the baseline Linker substitutes counting HashSets only for its five local classification groups. Every add/contains/clear/iterator operation delegates to the original HashSet behavior without extra semantic Domain getters. Diagnostic fields observe native set sizes and record peak, post-classification and final cardinality. A finally block records completed sets. Counters keep no Domain references after the invocation. Additional helper fields, varargs, counters and stack frames change allocation and scheduling, so timings and allocation totals from this diagnostic build are not performance evidence.

Two sequential fresh Java 17 JVMs × six hypothesis optimizations; first two excluded from warm counters. All eight warm sets of counters and histograms match exactly. There are 843,519 classification episodes **after checkSystem succeeds**, each constructing five sets; this is not a count of every linkDatabase entry. No histogram overflow occurs (bucket 63 means at least 63). All 12 complete RAW/OPTIMIZED texts and last Linker statistics match the clean reference; stderr is empty.

| Group | Created | Never populated | Peak cardinality | Add attempts | Clear calls | Iterators |
|---|---:|---:|---:|---:|---:|---:|
| excluded | 843,519 | 481,943 | 0–3 | 429,850 | 59,700 | 353 |
| calculated | 843,519 | 770,280 | 0–1 | 73,239 | 0 | 744 |
| candidates | 843,519 | 3,623 | 0–4 | 1,468,255 | 59,700 | 781,720 |
| assumed | 843,519 | 713,145 | 0–2 | 131,428 | 0 | 0 |
| stored | 843,519 | 274,271 | 0–4 | 646,451 | 0 | 0 |

Overall, 2,243,262 of 4,217,595 sets (53.19%) are never populated. This is a creation-count share, not a CPU share or available speedup. assumed has 119,038 contains calls, none while empty, and 479 duplicate add attempts. Final-empty cardinality alone would overcount avoidable constructors: excluded/candidates can be populated and subsequently cleared. The full peak/classification/final histograms and executable reconciliation are retained in classification-set-forensics/.

## Two boundaries against a different collection strategy

Standalone witnesses execute normal Java HashSets and production Domain objects, not a copied comparator. Two exact Domains with IDs 0 and 4 inserted in that order yield iteration `[4, 0]` under default HashSet capacity and `[0, 4]` under initial capacity two. Members are identical, but the first visited Domain changes. This establishes an order difference, not a demonstrated full Linker logical-result divergence.

A custom Domain.hashCode callback runs twice for two HashSet add attempts and throws its original exception on the second. ArrayList addition invokes it zero times and does not throw. Two scenarios / seven checks pass. These observations reject unqualified small-capacity or linear-list substitution as behavior-preserving replacements. They do not reject delayed creation of the same ordinary HashSet.

## Production change and preserved behavior

Only Linker changes. Four private local groups (excluded, calculated, assumed, stored) can start as null. The first add allocates the same default HashSet and immediately performs the original add. candidates retains eager creation because it is nearly always populated. No smaller table, linked ordering, singleton replacement or rebuilt populated set is used.

Populated groups retain their identity through clear and reuse, preserving cached entry hashes and mutation behavior. Empty checks interpret internal null as empty. Clear of an uncreated group has no escaping iterator or collection identity to affect. The assumed.contains call remains in its original position behind the existing nonempty guard, and always delegates to an actual HashSet; no keyed hash/equality callback is omitted. Foreach loops remain behind their original nonempty tests. Local sets never escape to Domain callbacks, provenance or public context APIs. All Domain classification getters, isStored calls, hydration, cause/solve/hypothesis effects and short-circuit order remain unchanged.

OFF retains eager initialization. There is no persistent/cache state, changed ownership, skipped binding, storage-format change or dependency on the earlier variable-capacity prototype.

## Qualification

Local Java 17 compiles at Java 8 target. LazyClassificationSetsRunner uses an independent ordinary HashSet oracle for the internal helper: 26 checks in OFF and ON. Coverage includes empty groups, sizes 1/2/3/4/16/80, duplicates/null keys, collision tree bins using the same key objects, callback traces, original hash/equality exception identity, clear/reuse identity, ordinary iteration order, and mutable Domain IDs after insertion. These helper boundaries are not by themselves a qualification of the full classification control flow.

Both modes also pass existing compact snapshots (23 checks), resident comparisons (48 / custom reads 3), single lookup (15), TValue reopen, latent corpus, 20-operation transaction and candidate concurrency (three iterations). Serialized transaction states are byte-identical. Dedicated CI covers Java 8/21/26 × prior eight optimization flags OFF/ON, testing this flag OFF/ON, cross-context projections, transactions and concurrency.

## Benchmark protocol and result

Clean → OFF → ON → ON → OFF → clean, six samples each, discard first two per JVM. Sequential fresh Java 17.0.20 JVMs, 512 MiB heap, all eight integrated flags explicitly ON. Diagnostic counters/profiler/shadow flags are absent. Clean and prototype use byte-identical SonProfileRunner. Timings, main-thread CPU and cumulative allocation are measured over ordinary optimizeHypothesis, not retained heap or all worker allocations.

Warm medians of the final four samples:

| Run | Wall seconds | Main CPU seconds | Allocated MB |
|---|---:|---:|---:|
| Clean, forward | 4.752 | 4.657 | 4,844.0 |
| OFF, forward | 4.897 | 4.799 | 4,723.2 |
| ON, forward | 4.634 | 4.528 | 4,591.3 |
| ON, reverse | 5.060 | 4.932 | 4,623.5 |
| OFF, reverse | 4.863 | 4.752 | 4,850.4 |
| Clean, reverse | 4.641 | 4.544 | 4,731.8 |

ON reduces allocated bytes versus OFF by 2.79% and 4.68%; versus clean by 5.22% and 2.29%. The direction is consistently favorable in these comparisons, with significant control variation. These are whole-phase main-thread allocations, not retained heap or an attribution of every byte to the four constructors.

Timing reverses direction: versus OFF, ON is 5.38% faster forward and 4.05% slower reverse; CPU changes similarly (+5.64% and −3.79% reduction). Versus clean, ON is 2.49% faster forward and 9.02% slower reverse. OFF versus clean also shows substantial timing/allocation variation. No specific JIT, scheduling or GC cause is established. The series does not demonstrate a reliable elapsed/CPU benefit. All 36 complete RAW/OPTIMIZED texts, logical result/solution/value counts and last Linker statistics match the independently built clean reference, with empty stderr.

**Keep as a qualified allocation improvement with inconclusive timing. Do not integrate or default-enable.** No additional indiscriminate timing batch is warranted. This result does not justify changing HashSet ordering, skipping Domain reads, or omitting keyed callbacks. Default remains OFF.

All five workflows passed: dedicated Java 8/21/26 × prior eight flags OFF/ON qualification, general KANGER CI, server, distribution and qualification isolation. This flag is tested OFF/ON in the dedicated matrix, with cross-context projections, transactions and concurrency. Final workflow statuses and URLs are in lazy-classification-sets/ci-status.json. Evidence/status commits change only documentation and logs; tested production, runner and workflow sources remain identical to the code commit.

## Reproduce

External ECJ at `../tooling/ecj.jar` and Java runtime:

```sh
python docs/classification-set-forensics/build.py
python docs/classification-set-forensics/run.py
python docs/classification-set-forensics/analyze.py
python docs/classification-set-forensics/witness.py
bash docs/lazy-classification-sets/build-local.sh
python docs/lazy-classification-sets/qualify.py
python docs/lazy-classification-sets/build-reference.py
python docs/lazy-classification-sets/run-benchmark.py
python docs/lazy-classification-sets/analyze.py
```

The diagnostic build restores baseline Linker from git before adding its counter copy. Temporary classes/source are under ../build. Runner, helper, exact baseline instrumentation patch, full snapshots/counters, scripts and transaction states are preserved with the reports. The code CI commit is `14418d90d18c0050ac638b2327f1bc6824a58669`; later evidence changes do not alter tested production, qualifications or workflows.
