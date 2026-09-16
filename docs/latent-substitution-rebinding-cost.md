# Reused occurrence list: rebinding cost

2026-09-16. Optional diagnostic `kanger.experiment.profileRebinding=true` measures only the eager Domain.setMind loop on reused occurrence lists. Per Linker invocation it emits reused-list count, occurrence count and inclusive loop nanoseconds. No semantic checks or rebinding are skipped.

One OpenJDK 17 JVM, 512 MiB heap, factory mode, reuse enabled, other optimizations off. Existing benchmark: three warmups and seven measured samples per fixture. traceInvocations markers delimit complete query intervals; all Linker invocations between BENCH_QUERY and BENCH_END are summed. Compilation invocations and warmups are excluded from reported medians.

| Fixture | Reused lists/query | Rebound occurrences/query | Median loop ms | Median query ms | Median per-sample share |
| --- | ---: | ---: | ---: | ---: | ---: |
| Native | 3,042 | 3,444 | 0.523249 | 129.784883 | 0.445% |
| 100 facts | 396 | 396 | 0.051573 | 38.187659 | 0.133% |

Counts are identical across the seven samples for each fixture. All 14 fingerprints match the preceding reuse benchmark. The 16-operation state output with profiling enabled remains byte-identical to the old-path baseline.

Timing includes loop/iterator work and diagnostic clock overhead; profiling and tracing perturb execution. The percentage is the median of sample ratios, not the ratio of the displayed medians. This is diagnostic attribution, not an OFF/ON performance comparison or a precise theoretical speedup bound.

Scope matters: first-time factory materialization, other Domain.setMind callers, selection/memo work, inference and GC effects are outside this measured loop. These results cannot establish the total global cost of context rebinding. They do show that optimizing this particular retained loop is unlikely to deliver a radical end-to-end improvement on these two fixtures. Do not weaken context semantics to save it.

Evidence: [rebinding](latent-substitution-evidence/rebinding-cost/). Reproduce with LatentSubstitutionBenchmarkRunner and properties latent=factory, reuseOccurrenceLists=true, profileRebinding=true, traceInvocations=true, benchFingerprint=true, benchOutput=<absolute CSV>. Separate stdout/stderr so markers and per-invocation observations can be correlated.

This closes the immediate rebinding-cost question. Keep list reuse off by default. Return to whole-query attribution and representative larger rule topology before introducing another optimization; the original goal remains shifting topology discovery to build time, not accumulating caches. Canonical Maven/Java 8/21 qualification remains outstanding.
