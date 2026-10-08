# Journal cost in a qualified memory-only contour

Parent: `c8d4fa5adc17b206e97c90e25487bb869a591956`. Diagnostic branch only: production/defaults/develop unchanged.

## Result

The incremental projection phase is cheaper than independent full authority capture in this exact sparse-edit contour. Across all 27 shadow JVMs, the ratio of their update-phase medians is 0.49–0.78; the nine aggregated cell medians are 0.53–0.75. This is potential savings in one phase, not an end-to-end inference speedup.

The current diagnostic journal performs projection AND full capture, validates equality, and formats traces. Its aggregated observer-wall ratios against clean full capture are 1.17–2.19. Individual repetition ratios range 0.94–2.78: three short repetitions are not a statistical performance guarantee. Removing correctness checks is not treated as an optimization or measured here.

Initial baseline projection is expensive: approximately 3.6–4.9 ms at 32 variables, 22.9–36.5 ms at 128, and 133.5–147.8 ms at 512. This initialization is excluded from steady update medians and must be budgeted for an actual lifecycle. The experiment does not claim the candidate wins when cold-start cost is included.

| Variables | Flags | Projection / same-JVM full capture | Diagnostic observe / clean full capture | Baseline projection, ms |
|---:|---|---:|---:|---:|
| 32 | ON | 0.69 | 1.47 | 4.9 |
| 32 | OFF | 0.69 | 1.42 | 3.6 |
| 32 | VERIFY | 0.65 | 1.28 | 4.9 |
| 128 | ON | 0.68 | 1.17 | 22.9 |
| 128 | OFF | 0.69 | 2.19 | 36.5 |
| 128 | VERIFY | 0.69 | 1.43 | 27.2 |
| 512 | ON | 0.74 | 2.06 | 147.8 |
| 512 | OFF | 0.75 | 1.89 | 138.1 |
| 512 | VERIFY | 0.53 | 1.77 | 133.5 |

Each displayed ratio is the median of three ratios of per-JVM phase medians. It need not equal a ratio of separately aggregated durations. Exact durations and per-repetition ranges are in `aggregated-medians.csv`; all per-JVM medians and raw 48-update samples are retained. Ratios below 1 mean less time, above 1 mean more time.

## Fixed native contour

One native root Mind, no storage, no children/settlement, no resets, no visibility-map edits, no callbacks during mutation, and one TValue per variable. Native Rule/TVariable/Term/TValue creation is used. Variables and canonical TValue identities remain stable; a single registered TValue alternates between two real canonical Terms through the original native `setValue` body. All native index warming through factory `forEach` happens before observation. No fixture fields or acceleration metadata are installed manually.

Sizes: 32, 128, 512 variables. Three fresh JVM repetitions per clean/shadow × ON/OFF/VERIFY cell: **54 timing JVMs**. Sixteen warmup updates precede 48 measured updates. Timings exclude before/after native fingerprints, dataset construction, index warming, warmup, filesystem writes, baseline, and session-end from update medians. Baseline time is reported separately. No parallel JVM workload ran during the timing matrix; focused regression started only after it completed.

The clean observer captures the full authority only. The shadow observer executes the existing journal with native setter hooks, dirty projection, full authority, comparison, and trace reporting. These are different workloads; their wall-time ratio describes diagnostic overhead, not a fair claim that an equivalent optimized production path is faster or slower. Same-JVM projection/full-capture phase comparison is the more direct evidence for the candidate calculation.

## Instrumentation and correctness

A docs-only journal copy adds opt-in `journal.cost.enabled` timers and a separate profile list. Original journal rows, decision branches, projection logic, oracle calls, error handling, and setter hooks remain unchanged. Removing the declared instrumentation substitutions recovers the original update-boundary journal source byte for byte. The native runtime and all storage/lookup/guard class families are unchanged and hash-verified.

The phase clock surrounds: incremental projection (including visibility and view copying), full authority capture, equality validation, then trace reporting. Dirty routing in the setter is outside projection timing; complete setter wall time is retained separately. Observe wall time additionally includes journal dispatch and profile string construction, which phase sums exclude. Timer overhead is neither calibrated away nor subtracted; no GC samples are removed. Java 17 runtime and Java 8 target, 512 MB heap, sequential fixed run order, and all environment details are retained. This is a diagnostic timing probe, not a fully stabilized JMH study or a cross-machine result.

All before/after fingerprints match for observer calls. Native final projections are byte-identical across clean/shadow, flags, and repetitions. Every shadow observation still compares incremental state against the independent full oracle. Each measured shadow session has 50 authority views, 48 changes, one seed, and 48 dirty-bucket reads: 27 new successful traces / **1,350 views**. Traces are byte-identical within each size and independently replayed.

Focused unchanged regression covers successful delete, flush, clear, close, upsert, and saved-link scope in all six mode/flag combinations: **36 JVMs**, including three unchanged successful journal traces / nine views. Native outcome rows and physical proofs match preceding evidence. Total: **90 JVMs, 30 successful traces, 1,359 authority views; no preliminary failures**. The preceding full matrices remain frozen and checksum-verified, not relabeled as freshly rerun.

## What to optimize next

Source inspection explains avoidable work. `ResidentTValueRead.bucket` constructs a complete Layer once for the target and again while collecting routes. Baseline capture calls that helper for every variable, so qualification/extraction repeats roughly twice per variable instead of once per observation. Each dirty update also copies the whole view and reporting scans/encodes all variables. Sparse dirty tracking alone does not make the implementation O(number of changes).

The next bounded improvement is to extract and qualify a read-only Layer snapshot once **within one observation**, then reuse it for that candidate's bucket enumeration. Do not retain it across mutations or generations. Keep the independent full oracle on a separate fresh scan, preserve owner purity and native-index behavior, rerun semantic equality checks, then repeat this exact timing contour. This targets the measured initialization/extraction overhead while retaining the qualification conditions.

Not qualified: production deployment, end-to-end inference speedup, persistent cold/resident performance, descendant/settlement performance, concurrent observers, arbitrary identity mutation, or full corpus equivalence. Earlier rejected corpus attempts remain unqualified.

Evidence: `tvalue-journal-cost/summary.json`, raw CSVs, full traces, independent analyzer, reversible instrumentation, environment, per-JVM commands/logs, build validation, and SHA-256 manifest.
