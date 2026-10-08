# Observation-scoped Layer reuse

Candidate journal projection now shares one qualified Layer per factory within a single observation. The frame is lazy, thread-owned, cleared and closed before the independent full authority check. Every following observation builds fresh layers; the original reader, qualification gates, authority algorithm and native engine are unchanged. All changes are documentation-only diagnostic overlays.

## Validation

248 final JVMs passed: 54 timing, 6 scope, 60 delete/flush, 30 settlement, 12 serialization, 30 fault and 56 memory controls. 87 successful traces / 10,668 authority views replayed and matched prior traces byte for byte. Failure prefixes and native physical/state/work controls also matched. The 27 expected-negative memory boundaries remain closed. Scope controls cover lazy extraction, one extraction for repeated root buckets, two for child ancestry, rejection after close, preserved native fingerprint and fresh state after mutation.

One preliminary scope JVM failed because its fixture added the same variable/Term pair twice; native add deduplicates it. The fixture now adds a distinct native Term. That failed attempt is archived, making 249 executed JVMs. Recompiled journal/helper class hashes remained identical; only the scope runner changed. Two earlier rejected corpus attempts remain retained and complete corpus equality remains unqualified.

## Measurements

Same inherited fixture: 32/128/512 memory variables, one value each, 16 warmup and 48 measured single-value updates, three JVM repetitions, clean/shadow and on/off/verify. Full authority and equality remain enabled for every shadow observation. Timings ran sequentially without regression JVMs in parallel.

| Variables | Flag | Baseline projection old → new, ms | Update projection / same-JVM full authority | Whole journal / clean full authority |
|---|---|---:|---:|---:|
| 32 | on | 4.857 → 0.378 | 0.580 | 2.32 |
| 32 | off | 3.617 → 0.267 | 0.598 | 1.30 |
| 32 | verify | 4.918 → 0.422 | 0.572 | 1.24 |
| 128 | on | 22.856 → 1.061 | 0.430 | 1.71 |
| 128 | off | 36.532 → 1.023 | 0.436 | 1.44 |
| 128 | verify | 27.157 → 0.987 | 0.415 | 1.61 |
| 512 | on | 147.814 → 1.752 | 0.644 | 2.03 |
| 512 | off | 138.134 → 1.484 | 0.659 | 3.45 |
| 512 | verify | 133.518 → 1.413 | 0.656 | 1.77 |

Baseline projection medians fell substantially, consistent with eliminating repeated full Layer extraction across all buckets. Single-update results are mixed: raw projection medians relative to the previous stage range from 0.60 to 1.74. Same-JVM projection/full ratios range from 0.38 to 0.70 across individual repetitions. The entire diagnostic observer still pays for the full oracle, equality and trace reporting; individual journal/clean ratios range from 1.15 to 3.70. No overall inference speedup is established.

Ratios in the table are medians of paired JVM ratios; duration medians are aggregated separately. These are fixed-order, small sequential JVM samples rather than stabilized JMH measurements. Cross-stage raw times are sensitive to JIT and runtime variation. Scope is sparse edits in native memory fixtures, not general persistent or settlement performance, concurrent observation, or production inference. Caller state must remain quiescent throughout each observation.

## Evidence

`summary.json`, `analyze.py`, build validation, declared source replacements, per-JVM phase/wall CSVs, native outputs, traces, regression matrices and the archived preliminary fixture are under `docs/tvalue-observation-layers/`. Production, defaults and develop are unchanged.
