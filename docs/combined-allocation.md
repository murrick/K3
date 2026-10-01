# Combined name and candidate allocation experiment

Live develop/3.8.0 verified before experiment: 1828d7164159cb04df00dedd5c27e18d556af4ea. Separate branch experiment/3.8.0-combined-allocation, based on candidate snapshot evidence 6896428. Only additional production delta is the previously qualified Predicate direct String path; no broad perf-branch merge or persistence change. Code checkpoint b5ae6ab2fc25250a40078e8e170fc2977a57163c.

Both new flags remain default OFF:
- kanger.experiment.directPredicateName
- kanger.experiment.compactFindSnapshots

Mode notation: name/snapshot bits. 00 reference; 10 direct String names; 01 compact snapshots; 11 both. Same Java 17 Xmx512m build, six prior integrated flags ON. Eight sequential fresh JVMs in order 00/10/01/11/11/01/10/00; six samples each, first two discarded; warm median of last four. Allocated bytes cover main thread during optimizeHypothesis, not retained memory. CPU covers main thread, not process/GC worker CPU.

| Mode/order | Wall s | CPU s | Allocated MB |
|---|---:|---:|---:|
| 00 forward | 5.422 | 5.302 | 5302.1 |
| 10 forward | 5.060 | 4.953 | 4785.6 |
| 01 forward | 4.932 | 4.837 | 4808.5 |
| 11 forward | 4.989 | 4.912 | 4334.4 |
| 11 reverse | 4.814 | 4.725 | 4204.4 |
| 01 reverse | 5.038 | 4.941 | 4818.0 |
| 10 reverse | 5.155 | 5.058 | 4930.8 |
| 00 reverse | 5.105 | 4.999 | 5386.1 |

| Mode | Forward wall reduction | Reverse wall reduction | Forward allocation reduction | Reverse allocation reduction |
|---|---:|---:|---:|---:|
| 10 | 6.69% | -0.99% | 9.74% | 8.45% |
| 01 | 9.04% | 1.29% | 9.31% | 10.55% |
| 11 | 7.99% | 5.69% | 18.25% | 21.94% |

Combined main-thread CPU reductions: 7.34% forward, 5.49% reverse. Both together show positive elapsed/CPU evidence in both orders on this workload, unlike their standalone prior warmed qualification. Do not add individual percentages; combined allocation savings are directly measured. Combination is not consistently faster than snapshot-only within each order. This is a small mirrored experiment on one corpus/query and JDK17, not a universal speedup or statistical proof. Laptop Java26 performance remains unmeasured. Favor combined manual comparison before integration; no merge/default-enable authorized or performed.

All 48 raw/final hypothesis snapshots equal; every sample has 18 raw and six final hypotheses, zero solutions/values; no benchmark stderr. Four-mode local qualification passes 24 name boundaries, 23 snapshot boundaries, regression corpus and 20 transaction operations per mode; all transaction states byte-identical.

Dedicated CI run 36898354727: six Java8/21/26 × prior flags OFF/ON jobs passed. Each runs all four new-flag combinations, both boundary runners, regression corpus, cross-context projection and transactions; compares all states; candidate concurrency runs with both new flags ON. General CI, server and distribution passed; isolation workflow was still running when report was written.

Raw logs, scripts and summary: combined-allocation-evidence/. Previous individual reports remain valid and must not be relabeled as stable standalone acceleration.

## Second workload: original set_08_02

Four fresh JVMs OFF/ON/ON/OFF, eight repetitions each; both new flags toggled together, prior six ON; Java17 Xmx512m, no storage selected. Each iteration creates a fresh User/Mind and executes KangerTest.test("set_08_02"), retaining the historical four worker threads and assertions. Discard first two, median last six; method times are millisecond-resolution values printed by the original harness. Do not compare against main-thread allocation/CPU measurements from the single-thread hypothesis scenario.

| Pair | OFF median s | ON median s | Time reduction |
|---|---:|---:|---:|
| OFF/ON | 0.835 | 0.856 | -2.51% |
| ON/OFF | 0.896 | 0.931 | -3.91% |

All 32 tests passed; empty stderr. Within-JVM times continue declining after two warmups, so these are harness medians, not fully stabilized JIT steady-state estimates. No speedup observed; both comparisons show a small slowdown. Do not promote the two paths as universally faster or default-enable on this evidence. A local comparison should include both hypotheses and set_08_02 before considering integration. This does not invalidate the directly measured allocation savings in the hypothesis workload. Local launch instructions: combined-allocation-local-check.md. No further production changes.
