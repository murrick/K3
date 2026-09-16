# Substitution cost: canonical TValue lookup

2026-09-16. Default-off diagnostic `kanger.experiment.profileSubstitutionWork=true`. Existing find/add and the two ordered Domain.setUsed calls in each substitution direction are wrapped with counters/timers; operation order and returned objects are unchanged. No lookup, checkpoint or semantic optimization is introduced.

Per-invocation output: find calls/ns, add calls/ns, two-domain used batches/ns. Timed add includes its own internal find, so the explicit-find column is not all find work globally. Used time covers only the two-call batches inside substitution, not used markings in completion or other code. These timers are nested inside the substitution phase; they must not be added again to parent phase time.

## Measurement

Factory mode, all optimizations otherwise off; ten-predicate shared-path fixture, 50/100 edges. OpenJDK 17, 512 MiB heap; one warmup and two measured samples per fixture, one JVM. Pair phases, stage timing and invocation markers enabled. All invocation observations between BENCH_QUERY and BENCH_END are summed per sample, then medians taken. Profiling perturbs timing; two samples give exploratory attribution rather than precision or comparative speed evidence.

| Edges | Explicit find calls | Find ms | Add calls | Add ms | Used batches | Used ms | Query ms |
| --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| 50 | 37,800 | 1,734.203 | 1,742 | 1.146 | 37,800 | 79.264 | 2,966.707 |
| 100 | 75,600 | 7,063.681 | 3,442 | 2.222 | 75,600 | 292.315 | 11,756.818 |

Counts repeat exactly across measured samples. The explicit canonical lookup path dominates these subcomponents. Calls double while measured lookup time grows about fourfold. This warrants investigating work per lookup; it does not prove hash collisions, index rebuilds or any particular cause.

All four fingerprints, result row counts and unification counts match the old-path partitioned fixture. StateRunner with factory-verify and the diagnostic enabled remains byte-identical to the old 16-operation baseline. A full corpus was not rerun for this diagnostic addition. Canonical Maven/Java 8/21 qualification remains outstanding.

## Concrete next hypothesis

TValueFactory.find constructs a temporary TValue key, calls cache.find(hash), resolves IDs and compares canonical `(TVariable ID, value ID)`. It deliberately includes deleted entries for identity-preserving resurrection. Escalera.find first calls ensureIndex and then copies the matching ID bucket.

Source inspection found that Escalera.release unconditionally invalidates its indexes after restoring the checkpoint root. The next ensureIndex clears/rebuilds indexes by walking the full root chain. This is a plausible source of repeated lookup cost following pair rollback, even where a checkpoint ultimately restores the same root, **but rebuild frequency and time have not yet been measured**. Root identity alone also does not prove all indexed metadata stayed unchanged; no shortcut is justified yet.

Next: measure rebuild count, walked steps and elapsed time specifically for the TValue Escalera, separating checkpoint releases, actual root changes and already-invalid states. Preserve canonical resurrection, nested checkpoints and all mutation invalidation behavior. No new cache or changed release semantics at this stage.

Evidence: [substitution work](latent-substitution-evidence/substitution-work/). Reproduce the partitioned benchmark with profileSubstitutionWork=true, profilePairPhases=true, timeStages=true, traceInvocations=true, benchWarmups=1, benchSamples=2 and direct CSV output. Existing switch names and the latent factory selection are unchanged.
