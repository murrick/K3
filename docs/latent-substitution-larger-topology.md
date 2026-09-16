# Larger symmetric-edge workload: topology filtering limit

2026-09-16. Benchmark-only change: `benchEdgeSizes` accepts comma-separated positive sizes, retaining default `10,30`. No production code changes.

## Method

Existing fixture enlarged to 50 and 100 edge facts, symmetry rule `edge(x,y) -> edge(y,x)` and projection `edge(x,y) -> path(x,y)`, querying both path variables. This is not transitive path closure. Compare latent=off with factory, all other optimizations off. OpenJDK 17, 512 MiB, one warmup and three measured samples per size, one JVM per mode, OFF then factory. Stage timing and invocation tracing enabled; fingerprints outside query timing. This is exploratory attribution, not statistically qualified comparative performance.

All Linker invocation stage arrays between BENCH_QUERY and BENCH_END are summed per sample, covering both query passes. CSV candidate counters, however, are the existing last-invocation snapshot; they must not be called full-query totals.

| Edges | OFF visited Domain pairs | Factory pairs | Unifications in either mode | Visits removed |
| --- | ---: | ---: | ---: | ---: |
| 50 | 54,400 | 52,599 | 52,599 | 3.31% |
| 100 | 208,800 | 205,199 | 205,199 | 1.72% |

Counts are identical across samples. Doubling size increases unifications by 3.90x in this snapshot. This workload concentrates domains in common signatures: the occurrence prefilter has very little rejected-pair work to remove. These are observed runtime acceptance ratios of the first topology gate, **not** global P/(S*A) density measurements. Do not generalize the native corpus's sparse graph to this fixture.

## Query attribution

Median milliseconds, computed separately for each column. Stage nesting means columns must not be added blindly: resolved lookup is within selection; solve sync is within validity; callbacks are within rotation.

| Mode / size | Whole query | linkDomains | Selection | linkDatabase | Rotation excluding callbacks | Validity | Solve sync |
| --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| OFF / 50 | 238.293 | 103.693 | 35.282 | 31.844 | 38.717 | 22.464 | 9.123 |
| Factory / 50 | 321.690 | 148.519 | 43.385 | 38.351 | 47.960 | 23.685 | 10.355 |
| OFF / 100 | 661.097 | 291.030 | 100.860 | 89.650 | 125.934 | 70.580 | 30.472 |
| Factory / 100 | 767.678 | 356.401 | 109.150 | 94.230 | 155.808 | 75.924 | 32.200 |

Factory is slower in this small profiled series, but fixed order, limited samples, JIT/GC and instrumentation preclude a stable regression percentage. The count-based conclusion is stronger: there are few incompatible visits available to eliminate, while the retained semantic work grows rapidly. linkDomains is the largest of the listed disjoint work categories; this measurement does not identify its internal bottleneck or justify skipping semantic pairs.

## Qualification and next boundary

All 12 measured results match across modes in semantic SHA-256, row count and unification count. Result row counts are 101 and 201. A separate factory-verify run, one sample per size with no warmup, also matches fingerprints and checks every occurrence list against the exhaustive tree traversal. Verification times are excluded.

Evidence: [larger topology](latent-substitution-evidence/larger-topology/). Reproduce via LatentSubstitutionBenchmarkRunner with benchScaled=true, benchEdgeSizes=50,100, benchWarmups=1, benchSamples=3, timeStages=true, traceInvocations=true, benchFingerprint=true and an absolute benchOutput CSV; select off or factory. Verify separately with factory-verify and zero warmups/one sample.

Next useful distinction is between many incompatible signatures (where topology indexing can help) and many genuinely compatible semantic pairs (where this prefilter cannot help). Before another optimization, inspect the internal cost of linkDomains on the latter and measure a comparably sized heterogeneous-signature workload. Pair memoization remains rejected by earlier counterexamples; neither direction nor semantic checkpoints may be removed on the strength of these timings. All experiments remain default-off; canonical Maven/Java 8/21 qualification is outstanding.
