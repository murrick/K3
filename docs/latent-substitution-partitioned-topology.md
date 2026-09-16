# Partitioned predicates with a shared result

2026-09-16. Benchmark-only extension: benchPartitions defaults to 1, preserving the previous fixture. With 10 partitions, input edges are assigned round-robin to edge0 through edge9. Each predicate has its own symmetry and edge-to-path rule; all feed the same path query. This changes rule count from 2 to 20 and is not an isolated density experiment or a transitive path query.

OpenJDK 17, 512 MiB heap; 50 and 100 edges; one warmup, three measured samples per size in each of two sequential JVMs (off then factory). Other optimizations disabled; stage profiling, trace markers and fingerprints enabled. A separate factory-verify run uses zero warmups and one sample per size. Timings are exploratory, not a replicated speedup claim.

| Edges | OFF domain visits | Factory visits / unifications in both modes | Visits removed | Median query OFF → factory ms |
| --- | ---: | ---: | ---: | --- |
| 50 | 107,120 | 89,110 | 16.81% | 3,220.627 → 3,124.137 |
| 100 | 354,220 | 318,210 | 10.17% | 12,445.289 → 11,666.343 |

Visit/unification counters describe the final Linker invocation, not full-query totals. The previous single-predicate workload had 52,599 and 205,199 unifications at the same sizes. Partitioning therefore did not simply reduce runtime work: additional rules and convergence on shared path change the inference workload. This observation does not determine which of those changes causes the increase.

Whole-query stage values below sum all invocation arrays inside each BENCH_QUERY/BENCH_END interval before taking sample medians. Sync is nested inside validity/rotation, not an additive extra.

| Mode / edges | linkDomains ms | Selection ms | Rotation excluding callbacks ms | Solve-sync ms |
| --- | ---: | ---: | ---: | ---: |
| OFF / 50 | 2,189.149 | 76.825 | 690.912 | 446.240 |
| Factory / 50 | 2,082.903 | 73.195 | 730.884 | 466.315 |
| OFF / 100 | 8,636.363 | 277.904 | 2,843.067 | 1,804.503 |
| Factory / 100 | 8,034.001 | 250.893 | 2,724.822 | 1,693.791 |

The largest measured component remains linkDomains. Factory mode removes incompatible visits but leaves substantial compatible-pair work. More predicate names do not guarantee an easy sparse runtime workload when rule outputs converge. No global P/(S*A) density was measured by this runner.

All 12 measured samples and both reference-verified samples match within each fixture in semantic fingerprint, rows and unification count. Result row counts remain 101 and 201. Fingerprints are compared across modes of this fixture, not against the differently named single-predicate knowledge base. Evidence: [partitioned topology](latent-substitution-evidence/partitioned-topology/).

Reproduce with LatentSubstitutionBenchmarkRunner: benchScaled=true, benchEdgeSizes=50,100, benchPartitions=10, benchWarmups=1, benchSamples=3, timeStages=true, traceInvocations=true, benchFingerprint=true, absolute benchOutput; select latent=off/factory. For factory-verify use zero warmups and one sample. No production changes or default enablement. Canonical Maven/Java 8/21 qualification remains outstanding.

Next: instrument disjoint internal phases of linkDomains (checkpoint setup, compatibility checks, substitution, completion) while preserving semantic order. Do not infer a safe pair cache from repeated arguments: earlier counterexamples still apply. A truly disconnected heterogeneous fixture would answer a different topology question and should not be conflated with this shared-output graph.
