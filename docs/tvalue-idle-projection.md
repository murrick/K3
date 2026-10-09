# Unchanged-observation projection reuse

The accepted reporting journal copies its TreeMap even when no buckets are dirty. This isolated candidate reuses the previous projection only after visibility processing has also left the dirty set empty. Any dirty bucket still starts from a new TreeMap. The candidate never modifies the reused map: ordinary Map.equals validation and reporters only read it. Baseline/reset still capture a new projection. A fresh full native authority capture remains mandatory on every observation and never supplies or repairs the candidate.

## Result

Select this one-line change as the next diagnostic baseline for its small, consistent allocation reduction on unchanged observations. Do not claim a stable whole-observation or inference speedup. Full wall ratios overlap parity. Dirty 1/8 behavior and allocation remain effectively unchanged.

Ratios candidate / accepted baseline; median of three flag-cell medians, each cell based on three fresh JVMs and 64 paired observations:

| Variables | Whole observation | Projection phase | Allocated bytes |
|---:|---:|---:|---:|
| 32 | 0.991 | 0.568 | 0.978 |
| 128 | 0.984 | 0.497 | 0.978 |
| 512 | 0.979 | 0.412 | 0.978 |

## Evidence and limits

84 retained JVMs: 81 timing (32/128/512 variables, dirty 0/1/8, three repetitions, on/off/verify flags) and three scope controls. 5,184 measured pairs, 168 successful complete traces, 10,752 independently replayed authority views. Paired traces are byte identical; dirty 1/8 traces also match the prior reporting stage. Scope includes 1,028 map pairs per JVM, native insert/change/delete/restore/clear/reset transitions, and three unnotified native mutation refusals per JVM. Missing setters, additions and clear are rejected even on the reused-map path; finish also refuses the failed session. Native fingerprints remain unchanged by observation.

96 JVMs were executed: the last 12 initial timing JVMs were conservatively excluded because scope controls were started before matrix completion was confirmed. Their complete results and reason are preserved in excluded-overlap-timings. They were rerun sequentially after all controls completed; only replacements enter summary/CSV metrics. No correctness or fixture failure occurred. Raw accepted evidence, per-JVM and flag-cell medians include oracle phase noise and both observation orders.

Sources are exact renamed copies of docs/tvalue-reporting/StreamReportJournal.java with the single declared projection replacement. Runner and scope controls are rename-only copies of that stage. Build verifies all 662 inherited native runtime class hashes. Experiments use native memory objects with explicit post-setter diagnostic metadata bridges; native hook integration and persistent storage were not rerun. No production sources/defaults changed, no merge, and the earlier frozen full corpus remains unqualified. Heap allocation counters are not RSS; this is a diagnostic benchmark, not JMH or end-to-end inference evidence.

## Reproduce

From repository root with Java 17 and ECJ 3.33 in ../tooling/ecj.jar, first reconstruct the hash-verified clean native runtime described in docs/tvalue-layer-cost. Then run build.py, run.py, scope.py and analyze.py in this directory sequentially. rerun.py records the exact conservative replacement subset used here. analyze.py performs source recovery, paired trace equivalence, independent trace replay, phase accounting, native hash checks and scope refusal checks. summary.json and evidence-sha256.json record results and evidence hashes.
