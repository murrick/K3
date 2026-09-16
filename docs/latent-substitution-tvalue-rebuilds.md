# TValue Escalera rebuild attribution

2026-09-16. Diagnostic only; release still invalidates exactly as before.

`profileTValueRebuilds=true`, selected before Mind creation, enables counters only for Escalera schema `tvalues`. Thread-local numeric totals aggregate all participating caches on the benchmark thread; they retain no Mind/cache objects. The benchmark snapshots before/after query outside its timer, reporting deltas rather than compilation totals.

Counter order: rebuilds, walked steps, rebuild ns, releases, same-root releases, same-root releases with coherent valid index, releases while already invalid, rebuilds following a release since previous rebuild, their walked steps, their ns. The release marker means chronological association; intervening mutations are not individually classified and it is not a general causal label. Empty-chain rebuilds also count. Counters describe completed rebuilds.

## Results

Factory mode, other optimizations off; partitioned shared-path fixture at 50/100 edges. OpenJDK 17, 512 MiB heap; one warmup and two measured samples per size in one JVM. TValue substitution timing, tracing and fingerprints enabled. Counts reproduce exactly across the two measured samples; time is their median.

| Edges | Rebuilds | Walked steps | Rebuilds following release | Rebuild time ms | Query time ms |
| --- | ---: | ---: | ---: | ---: | ---: |
| 50 | 17,528 | 33,170,902 | 17,527 | 2,479.606 | 3,768.577 |
| 100 | 35,088 | 131,521,122 | 35,087 | 8,624.812 | 12,824.216 |

| Edges | Releases | Same-root releases | Same-root/coherent-valid releases | Already-invalid releases |
| --- | ---: | ---: | ---: | ---: |
| 50 | 78,028 | 78,028 | 17,528 | 60,500 |
| 100 | 278,088 | 278,088 | 35,088 | 243,000 |

At 100 edges, rebuilds following release walked 131,520,322 steps and consumed 8,624.732 ms; at 50, 33,170,502 steps and 2,479.571 ms. Almost all measured rebuild work follows release. This directly identifies repeated full-chain indexing as a major cost in this workload. Rebuild times include metadata clearing/allocation and indexStep work, not just pointer traversal. Profiling changes execution costs; these are not guaranteed savings or an uninstrumented speedup estimate.

## Qualification and boundary

All four benchmark fingerprints, row counts and unification counts match the old-path partitioned fixture. Profiling-enabled factory-verify state output (16 operations) and nested transaction output (20 operations) are byte-identical to their old-path baselines. No release behavior changed. Full corpus and canonical Maven/Java 8/21 were not run for this diagnostic addition.

Evidence: [TValue rebuilds](latent-substitution-evidence/tvalue-rebuilds/). Reproduce the partitioned benchmark with profileTValueRebuilds=true, profileSubstitutionWork=true, traceInvocations=true, benchFingerprint=true, one warmup/two samples and direct CSV output. Each TVALUE_REBUILDS stderr row names its fixture/sample, including excluded warmups.

Next experiment should be narrow: TValue-only preservation of an already coherent index when checkpoint restoration leaves the identical root. Before implementation, audit indexed metadata mutations, in-place chain changes, nested release/commit and persistent entries. Root equality by itself is not yet a universal proof. Qualification must independently rebuild the expected maps and compare them, then run actual preservation without the verifier repairing state. Changed-root and already-invalid cases must retain reference rebuilding behavior. Do not extend the exception to all schemas or alter persistence semantics based on these measurements.
