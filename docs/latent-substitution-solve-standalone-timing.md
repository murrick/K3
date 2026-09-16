# Standalone versioned solve-sync timing

2026-09-16; source checkpoint `ea1872f`. No production code changes in this measurement checkpoint.

Only `kanger.experiment.versionedSolveSync` changes between OFF and ON. Latent indexing, factory candidates, indexed intersection, membership filtering, argument plans, reference verification and timing profilers are all off. OpenJDK 17, `-Xmx512m`, existing ECJ Java 8-target classes.

Three independent JVMs per mode and fixture family, sequential execution. Each JVM performs three warmups and seven measured samples per fixture with fresh Minds. Default family order is OFF/ON, ON/OFF, OFF/ON across rounds; scaled family reverses those orders. Compilation is timed separately. Query time includes the complete query. Semantic fingerprint construction happens after timing, but can affect subsequent GC equally in both modes.

| Fixture | OFF JVM medians (ms) | ON JVM medians (ms) | Median of medians OFF → ON | Change |
| --- | --- | --- | --- | --- |
| Native values | 117.378, 124.387, 111.691 | 96.566, 96.253, 102.281 | 117.378 → 96.566 | −17.7% |
| 100 singleton facts | 21.104, 22.626, 21.758 | 22.699, 23.019, 24.265 | 21.758 → 23.019 | +5.8% |
| 10 symmetric edges | 23.823, 30.347, 31.915 | 23.772, 21.907, 21.962 | 30.347 → 21.962 | −27.6% |
| 30 symmetric edges | 89.641, 81.883, 85.702 | 99.838, 89.985, 83.611 | 85.702 → 89.985 | +5.0% |

All twelve direct CSV outputs completed: 168 measured samples, 42 per fixture. Each file contains exactly samples 0–6 for each of its two fixtures. Within every fixture, semantic SHA-256, result row count, domain-pair count and unification count are identical across all samples and modes. These fingerprints cover values, solutions, hypotheses and visible rules; the preceding standalone qualification separately compared transaction effects and pass actions.

The native benefit survives removal of the other experimental optimizations: each ON JVM median is below every OFF JVM median in this series. This supports an independent, workload-specific solve-sync benefit. It does not establish a universal speedup or a causal explanation for the regressions. The 10-edge result has substantial OFF variability and almost no improvement in its first pair; the 30-edge result goes the other way in two of three pairs. Neither supports a general scaling claim.

Raw CSVs and process logs are in [evidence](latent-substitution-evidence/solve-standalone-timing/). Reproduce from the repository with the compiled qualification classes on the classpath:

```sh
java -Xmx512m -cp "$CP" \
  -Dkanger.experiment.versionedSolveSync=false \
  -Dkanger.experiment.benchFingerprint=true \
  -Dkanger.experiment.benchOutput=/absolute/path/off.csv \
  org.kanger.LatentSubstitutionBenchmarkRunner
```

Repeat with `true`, alternating order across fresh JVM pairs. For the scaled family additionally set `-Dkanger.experiment.benchScaled=true`. No default enablement is proposed. Canonical Java 8/21 and Maven qualification remain outstanding. A useful next step for the original topology objective is actual factory-index retained memory and build cost; more timing repetitions here are not required to conclude that the current benefit is workload-specific.
