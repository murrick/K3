# Isolated compact candidate snapshots

Historical experiment report: the default-OFF statements below describe
experiment/3.8.0-resident-snapshots. The proposed default-ON integration is
documented in docs/optimization-defaults.md.

Base: develop/3.8.0 at 3a2a6f0bccac11ac0d646178c4384ba73d7a5260.

This experiment contains only compactFindSnapshots (default OFF). Predicate.java is byte-identical to the base; no directPredicateName code is included. Public ICache.find ownership remains unchanged. Internal factory iteration uses immutable empty/singleton snapshots for exact Escalera instances and the original HashSet copy for multiple candidates. Custom caches retain their original find calls.

Build locally with `bash docs/resident-snapshots-evidence/build-local.sh`, then `python docs/resident-snapshots-evidence/qualify.py`. The benchmark runs six fresh JVMs in reference / OFF / ON / ON / OFF / reference order, six samples per JVM, discarding the first two. Reference classes must be compiled from the base into ../build/defaults-classes. All seven integrated optimizations are explicitly ON. No diagnostic counters or profilers are enabled.

## Qualified result

Code: f866587c1c078d5be88f8be715faedecf7f70cf9. Java 17.0.20, 512 MB heap. The following are medians of samples 2–5 for the optimizeHypothesis phase; allocation is main-thread allocated bytes, not retained heap. Query/compilation time is excluded.

| Fresh JVM order | Wall, s | Main CPU, s | Allocated, MB |
| --- | ---: | ---: | ---: |
| Clean base | 4.680 | 4.574 | 5396.3 |
| Experiment OFF | 4.567 | 4.457 | 5435.1 |
| Experiment ON | 4.387 | 4.288 | 4856.0 |
| Experiment ON | 4.379 | 4.278 | 4808.8 |
| Experiment OFF | 4.617 | 4.523 | 5433.6 |
| Clean base | 4.711 | 4.600 | 5384.4 |

ON vs clean base reduced wall time 6.26% / 7.05%, CPU 6.26% / 7.01%, allocation 10.01% / 10.69%. ON vs OFF in the same build reduced wall time 3.93% / 5.17%, CPU 3.80% / 5.42%, allocation 10.65% / 11.50%. OFF itself was about 2% faster than clean base in this batch; the two comparisons are therefore reported separately. No claim generalizes these warm, single-corpus measurements to full application or cold-start speed. Earlier combined-prototype measurements are not added to these gains.

All 36 full RAW and OPTIMIZED textual snapshots are equal across six JVMs (18 raw hypotheses, 6 optimized, 0 solutions/values). Every measured JVM emitted all six samples and empty stderr. Runner and Predicate bytecode are identical between builds; only the three production classes differ, with Escalera.WalkIterator differing solely in source line numbers. See verify-build.py and build-manifest.json.

The initial incomplete batch is archived under incomplete-control-batch and excluded from performance claims: its forward OFF process returned zero without any output. The complete validated rerun above is the comparison used.

Local OFF and ON passed snapshot ownership/callback/order boundaries (23), resident comparison boundaries (48), single lookup boundaries (15), regression corpus, DB reopen and 20 transaction operations. Transaction state bytes are equal, SHA256 c230b44a51bb11b8b19a198c9064000db28e26a29d6c956a7183a35c10d74e0c. No runner stderr was emitted.

All five GitHub workflows passed on the code commit: resident snapshot qualification, general CI, qualification isolation, server and distribution. The dedicated workflow covers Java 8/21/26, each with seven prior optimizations OFF and ON, and snapshot OFF/ON, cross-context projection, equal transaction states and three concurrency iterations. See ci-status.json.

## Laptop check

Branch: experiment/3.8.0-resident-snapshots. Leave the seven integrated flags at their defaults. Compare `-Dkanger.experiment.compactFindSnapshots=false` with `-Dkanger.experiment.compactFindSnapshots=true`, restarting the JVM between modes (the flag is read statically). Use repeated warm `?$x son(John, x);` queries and options test 08_02 in both orders; also check DB reopen, transactions and collisions. A single timing is not enough to infer a small speedup.

Rick completed laptop OFF/ON timing checks on 2026-10-03 and accepted retaining this optimization ("ок ) оставляем )"). All reported timings are in laptop-results.json. Excluding the first of four runs, warm medians were 1.239 s OFF / 1.159 s ON for 08_02 (6.46% reduction), and 7.83 s OFF / 7.48 s ON for son(John, x) (4.47% reduction). Only one OFF-then-ON sequence was reported; sample variability limits precision. No additional manual DB/transaction/collision outcome was reported in this message; their automated qualification remains green.

The seven prior optimizations were ON: six were explicitly set in vmArgs, and residentBaseComparison used its default true. The predicate-name prototype is excluded entirely. This records local timing acceptance and the decision to retain the experiment; the flag remains default OFF pending integration. Develop was not changed and no merge/default-enable is performed.
