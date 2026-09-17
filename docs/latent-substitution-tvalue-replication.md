# TValue index preservation: reversed-order replication

2026-09-17. Source remains experiment checkpoint `a788b99`; no production changes in this report.

## Live baseline reconciliation

Live develop is now `74654935b78465ade2043e40b6ff147b43ad8147`, one commit beyond historical experiment base `3ad50f1`. Its diff changes CanonicalCommandProcessor, Console, CanonicalTimeZoneCommandTest and Version. Linker, Escalera, TValue and TValueFactory are unchanged. Measurements deliberately use the unchanged experimental checkout; they are not a qualification of a rebased or merged branch. Neither branch was merged or rebased.

## Large workload repeat

Same 100-edge, ten-predicate shared-path fixture. Factory mode enabled in both runs; preserveTValueIndex is the only changing optimization. OpenJDK 17, 512 MiB heap, one warmup and three measured samples per fresh JVM. This time ON runs before OFF, reversing the previous pair. No profiling or verifier; fingerprints outside query timing.

| Pair / execution order | OFF median ms | ON median ms | OFF/ON |
| --- | ---: | ---: | ---: |
| Previous OFF then ON | 11,379.076 | 3,838.471 | 2.96x |
| New ON then OFF | 11,149.358 | 3,576.077 | 3.12x |

New OFF samples: 11,360.103; 11,149.358; 10,924.314 ms. New ON samples: 3,657.901; 3,563.594; 3,576.077 ms. Thus the large effect survives reversed JVM order. These are two paired runs, not a universal throughput guarantee or a statistical confidence interval.

## Other workloads

Three JVM pairs in OFF/ON, ON/OFF, OFF/ON order; three warmups and seven measured samples per fixture/JVM. Both fixtures run in each JVM in their usual native-then-facts order. Same flags and environment as above.

| Fixture | OFF JVM medians ms | ON JVM medians ms | Median of medians OFF → ON |
| --- | --- | --- | --- |
| Native | 120.631, 141.163, 140.295 | 90.214, 104.558, 94.236 | 140.295 → 94.236 |
| 100 facts | 21.677, 25.006, 31.945 | 21.472, 26.231, 19.806 | 25.006 → 21.472 |

Native improves in all three pairs, with every ON JVM median below every OFF median in this series (32.8% lower median-of-medians). Facts improve in two pairs and regress in one with substantial baseline spread; do not promote its 14.1% median reduction as a stable improvement.

All eight direct output files have complete sample ranges: 90 measured samples overall (6 large, 84 small). Within each fixture all semantic fingerprints, row counts, domain visits and unification counts agree across modes/samples. Previous actual-preservation and independent-map qualification remains applicable because production code did not change; no additional corpus run was needed for this measurement-only checkpoint.

Evidence: [replication](latent-substitution-evidence/tvalue-replication/). Reproduce using the existing benchmark runner with latent=factory, benchFingerprint=true and an absolute benchOutput; toggle preserveTValueIndex. Large: benchScaled=true, benchEdgeSizes=100, benchPartitions=10, benchWarmups=1, benchSamples=3, ON then OFF. Small: defaults, three alternating pairs. All verifier/profiler and other optimization flags omitted.

The result supports a repeatable benefit on the two tested nontrivial workloads. It does not establish standalone behavior without the factory occurrence path, performance with storage, synergy with versionedSolveSync, or universal benefit. Next useful gates are isolated preservation with latent mode off and canonical build/runtime qualification. Keep default off; no merge/release/tag/deploy authorization follows.
