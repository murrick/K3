# Post-TValue baseline on 3.8.0

Base: `9479cd4acf6d14cc5195c20a9eaacdec6ec4a67c`, 2026-09-17.
Branch: `experiment/3.8.0-latent-substitution-index`. No production changes.
The older experiment remains an archive; no latent index or other optimization
was imported into this baseline. TValue preservation alone is enabled for ON.

Fresh compilation of core, command, bootstrap, UDF, DUMB and console sources
with ECJ (Java 8 source target), using only the jline dependency, into a separate
classes38 directory. Runs use OpenJDK 17 and 512 MiB heap. DUMB2 is not exercised
by these in-memory fixtures. These are local measurements, not a new CI gate.

## Unprofiled control

Same 100-edge/10-predicate symmetry/shared-path fixture as the preceding
experiment. One warmup and three measured queries, fresh Mind per sample.
Fresh JVM order: ON, sampled ON, OFF; no concurrent benchmark JVMs.

| Mode | Median query seconds |
| --- | ---: |
| OFF | 10.955 |
| ON | 3.704 |
| Sampled ON (diagnostic only) | 3.672 |

Unprofiled OFF/ON ratio: 2.96. This is one pair on this base, not a new broad
replication series. All nine measured rows have 201 results, 354,220 domain
pairs, 318,210 unifications, and identical SHA-256
`95ab83d6b37f1cd06a869b0af45b4322cd6297bac6768bf42675ebc7da1384a0`.
The fingerprint covers sorted values (including duplicates), solutions,
hypotheses and active rule text/generated flags, outside the query timer.
Counters remain the existing last-Linker snapshot, not whole-query totals.

## Remaining work: independent stack sampling

The qualification runner optionally samples the query thread's stack, sleeping
10 ms between captures. It only counts stacks containing Mind.query; compilation
before the query and fingerprint rendering are excluded. Multiple Linker calls
inside one query are included. Inclusive method counts deduplicate recursion
within each captured stack. Raw leaf and inclusive counts are retained.

This is approximate wall-time sampling, not CPU accounting. Stack capture and
safepoints perturb execution; intervals are not exact; nested percentages overlap
and must not be summed. The sampled median being lower than unprofiled ON is
ordinary run variability, not evidence that sampling has no overhead.

| Inclusive method, large fixture | Samples / 1,087 | Fraction |
| --- | ---: | ---: |
| synchronizeSolveIndex | 482 | 44.3% |
| isValidFor (includes synchronization) | 602 | 55.4% |
| linkDomains | 190 | 17.5% |
| linkDatabase | 152 | 14.0% |
| selectDomainCandidates | 70 | 6.4% |

TVariableSet.hashCode appears inclusively in 245 samples and as the leaf in 217.
HashMap tree-node methods also occur often. This does not alone prove a hash
quality defect. Source inspection shows synchronizeSolveIndex walks all solve
groups and repeatedly hashes keys for contains/get/put even if no new solves
exist. Measuring unchanged-sync frequency is the next causal test.

Small-workload sampling uses three warmups and twenty measured queries per
fixture. Native yields 167 samples, with 27 (16.2%) in synchronizeSolveIndex,
22 (13.2%) in linkDomains and 38 (22.8%) in linkDatabase. Singleton facts yield
only 42 samples; that is insufficient for a strong bottleneck ranking. Each
fixture's twenty semantic fingerprints is stable. There is no small-fixture
OFF comparison in this measurement, so it establishes no small-workload speedup.

## Next experiment

Investigate version-guarded solve-index synchronization on this new base, using
the old experimental prototype as reference only. Verify mutable-map exposure,
tuple publication and nested transaction boundaries before skipping any scan.
Keep the existing synchronization path as oracle. The latent topology goal
remains separate: quantify direct traversal savings after removing redundant
maintenance. No simplification of bidirectional Linker traversal is justified
by the current sample counts alone.

## Reproduction

Runner: `org.kanger.TValueBaselineBenchmarkRunner` (qualification sources).
Set `kanger.experiment.benchOutput` to a CSV path; parent directory must exist.
Large fixture options: `benchScaled=true`, `benchEdgeSizes=100`,
`benchPartitions=10`, `benchWarmups=1`, `benchSamples=3`, all under
`kanger.experiment.`. ON adds `preserveTValueIndex=true`; diagnostic ON additionally
adds `sampleQuery=true`. Small sampled fixtures omit benchScaled, use
benchWarmups=3 and benchSamples=20. Do not enable verifyTValueIndex for timings.
Raw CSV/stdout/stacks: `latent-substitution-evidence/post-tvalue-38/`.
