# Streaming full authority on dense native buckets

The preceding authority-stream experiment had one TValue per variable. This stage qualifies the same accepted implementation with 1, 8 and 32 actual native TValues per variable, keeping 32 variables and comparing unchanged observations with eight dirty buckets. Each changed bucket rewrites one real TValue. The full journals, full oracle, projection, reporters and native runtime are unchanged.

The accepted streaming authority retains its benefit on both dense fixture families. Across the 12 dense width/dirty/flag cells, full-observation median ratios are 0.831–0.939, and allocation ratios are 0.778–0.884. Across individual dense JVM medians, wall ratios are 0.782–0.956. Keep docs/tvalue-authority-stream/StreamAuthorityJournal.java with its shared-primitive StreamAuthorityRead as the diagnostic baseline. This confirms the previous optimization; it is not an additional cumulative speedup or an inference/application throughput claim.

## Paired results

Ratios streaming journal / pre-streaming idle journal. These are the same two unchanged implementations used in the preceding authority-stream comparison. Each displayed value is the median of three flag-cell medians; each cell has three fresh JVMs with 64 alternately ordered measured pairs after 48 warmup pairs. The complete observe call includes fresh full authority capture, ordinary Map.equals validation and complete reporting. Metadata bridges and native setters are outside timing.

| Values per variable | Native TValues | Dirty buckets | Whole observation | Authority phase | Allocated bytes |
|---:|---:|---:|---:|---:|---:|
| 1 | 32 | 0 | 0.791 | 0.729 | 0.718 |
| 1 | 32 | 8 | 0.872 | 0.725 | 0.835 |
| 8 | 256 | 0 | 0.860 | 0.820 | 0.778 |
| 8 | 256 | 8 | 0.912 | 0.815 | 0.872 |
| 32 | 1024 | 0 | 0.831 | 0.798 | 0.798 |
| 32 | 1024 | 8 | 0.909 | 0.863 | 0.884 |

All per-JVM, flag-cell, observer-order and half-run metrics and raw phase/wall/allocation samples are retained. Density and total native value count increase together here; this is coverage of different dense fixtures, not an isolated causal estimate of density alone. Thread-allocated bytes are not RSS. This remains a diagnostic benchmark, not JMH.

## Native fixture and trace controls

DensityCostRunner is an exact renamed copy of the accepted AuthorityCostRunner after reversing four declared fixture/control/output edits. The paired observation protocol, setters, metadata bridges, warmup, phase accounting, purity fingerprints, trace equality and finish checks are unchanged. Extra terms are actual native Terms, and TValueFactory.add creates actual native values; setup is outside timing. Every variable's native forEach enumeration is checked against the qualified reader for width, exact object reference identity and order. Qualified control reads must preserve the native fingerprint. Setup also checks the full native value count and complete 32-variable authority.

57 fresh JVMs passed: 54 timing and three reused, hash-verified scope controls. There were no preliminary fixture failures or overlapping timing runs. The matrix is width 1/8/32, dirty 0/8, three repetitions and on/off/verify flags. It covers 32, 256 and 1,024 native values. 1,728 per-variable native enumeration controls verify 23,616 canonical value references during fixture construction.

3,456 measured pairs produce 114 successful complete traces and 7,188 independently replayed authority views. Every paired trace is byte identical; all width-one timing traces also match the preceding accepted stage exactly. All 7,128 timing authority views have exactly 32 variables, the configured number of values per variable and no deleted values, covering 3,117,312 serialized value entries. Replay verifies complete before/after buckets and authority equality, preserves canonical ID order, and finds exactly 64 times dirty-count term changes with no insertion, removal, delete/restore or reorder transition. Final native views agree across flag/repetition/dirty settings within each width.

The three unchanged scope JVMs retain 1,028 route-encoding cases, 1,028 reporting map cases, ten full native authority comparisons including parent/child, visibility and clear, positive insert/change/delete/restore/clear/reset traces, and three unnotified setter/add/clear refusals each. All nine negative cases refuse identically and finish rejects failed sessions. These separate scope views are included in the 7,188 replayed views; the ten direct authority comparisons per scope JVM are additional assertions.

## Boundaries and reproduce

All 662 inherited native class hashes and all 13 accepted diagnostic class hashes are verified before the build and after execution, along with accepted source hashes. Only the new fixture runner is compiled in this stage. Both journals use independent sessions on one native memory state. The full oracle stays fresh on every observation and never seeds or repairs the projection. Actual engine hook integration, persistent performance and end-to-end inference were not rerun. Production sources/defaults are unchanged; no develop merge. The earlier frozen complete corpus remains unqualified.

From repository root, prepare the clean native runtime described in docs/tvalue-layer-cost, then build the accepted docs/tvalue-authority-stream overlay with Java 17 and ECJ 3.33 target Java 8. Run this stage's build.py, scope.py, run.py, analyze.py and make_report.py sequentially. build-validation.json records fixture source recovery and immutable runtime hashes; summary.json and CSVs contain results; evidence-sha256.json covers all stage files and this report, excluding itself.
