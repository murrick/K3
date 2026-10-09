# Fresh full-authority streaming serialization

The accepted idle-projection journal keeps an independent fresh full authority on every observation. Its authority serializer allocates an intermediate TValue list, a list of per-value strings, and an eagerly constructed default LinkedHashSet for every variable. The final candidate streams the same ordered resident intersection into a StringBuilder and omits keys with no resident routed values. No baseline, dirty bucket, visibility, reporting, ordinary Map.equals validation or missing-event policy is weakened.

Select **StreamAuthorityJournal** with **StreamAuthorityRead** as the next diagnostic baseline. All 18 final size/dirty/flag cells have lower median full-observation wall time, with 16–29% fewer allocated bytes. This is an observation benchmark result, not an inference or application-throughput claim. Individual JVMs still show noise and occasional regression.

## Final implementation

BeforeAuthorityJournal is an exact class rename of docs/tvalue-idle-projection/IdleProjectionJournal.java. StreamAuthorityJournal differs only in its full-authority call. StreamAuthorityRead copies the existing full-authority method, preserving Mind validation, fresh layer extraction, cycle refusal, base-to-leaf route merging, sorted variable discovery and target-resident intersection. Its only algorithm edit is the declared serialization loop plus encodeRoutes helper. It imports the unchanged ResidentTValueRead raw primitives; the 662-class clean native runtime is verified before build and after the runs.

Sharing raw validation primitives is already part of the old independent oracle design. Both authority implementations call layer afresh for every factory every time. Neither receives an ObservationLayers frame, dirty set, cached authority or incremental projection. The oracle never seeds or repairs a candidate, and is never disabled. Native guards remain the original byte-verified implementation. Calls run under the existing quiescent diagnostic precondition.

## Final paired result

Ratios candidate / accepted idle baseline. Each displayed value is the median of three flag-cell medians, each cell based on three fresh JVMs and 64 alternately ordered pairs. Full wall time and thread-allocated bytes include the complete observe call; the authority column covers fresh full capture and serialization.

| Variables | Dirty buckets | Whole observation | Full authority phase | Allocated bytes |
|---:|---:|---:|---:|---:|
| 32 | 0 | 0.825 | 0.763 | 0.718 |
| 32 | 8 | 0.884 | 0.752 | 0.835 |
| 128 | 0 | 0.871 | 0.809 | 0.715 |
| 128 | 8 | 0.914 | 0.800 | 0.806 |
| 512 | 0 | 0.863 | 0.816 | 0.760 |
| 512 | 8 | 0.884 | 0.746 | 0.830 |

Across the 18 flag cells, full-observation ratios are 0.820–0.947 and authority-phase ratios 0.734–0.864. Across individual JVM medians the wall ratios are 0.748–1.140; do not describe every JVM as faster. Unchanged projection/reporting phases vary with the paired runtime environment. Full per-JVM, flag-cell, first-observer and half-run metrics are retained in CSV evidence.

## Correctness evidence

57 retained final JVMs: 54 timing and three scope controls, on/off/verify flags. 3,456 measured pairs, 114 complete successful traces and 7,188 independently replayed authority views. Paired traces are byte identical; all timing traces also match the accepted idle stage byte for byte. Real native setters execute before explicit metadata bridges, outside timing. Fingerprints verify both observers preserve native state. Native final views agree.

Each scope JVM checks 1,028 randomized/fixed route-encoding cases, including empty sets, missing resident IDs, shuffled order, multi-value output and unchanged input maps/sets. It also compares both complete authorities in ten real memory states: empty, multiple values before/after native indexing, inherited child before/after indexing, parent-plus-child routes, inherited delete/restore, local term rewrite and clear. These 30 native authority pairs are additional assertions, separate from the 7,188 replayed trace views. Existing scope checks retain 1,028 reporting map pairs, insert/change/delete/restore/clear/reset transitions, and three unnotified native mutation refusals per JVM. All nine missing setter/add/clear cases produce identical mismatch refusals, and finish refuses the failed sessions.

No correctness, fixture or native boundary failure occurred in the three series.

## Two intermediate comparisons

171 JVMs were executed in total, including two separately preserved 57-JVM intermediate series. Their results never enter the final metrics. Both passed the same functional checks.

The initial prototype cloned the entire validated reader for the candidate while leaving the control on ResidentTValueRead. Fingerprints and both projections additionally used that original class. The copies therefore had asymmetric call frequencies and class-specific warmup/JIT profiles. Despite lower allocation, the original setup mostly regressed in full wall time. This is a measurement-design concern, not proof that class warmup alone caused every regression.

The intermediate comparison gave each authority its own complete reader copy with matching call frequency. It showed a serialization benefit in that model. The final implementation avoids copying read validation entirely: both sides use the same unchanged qualified primitives, and the control journal is again exactly the accepted baseline. Every final timing and scope JVM was rerun sequentially after the previous series completed.

The table below is historical evidence only, showing median full-wall ratios across flags in the two superseded models:

| Variables | Dirty buckets | Initial asymmetric copies | Intermediate symmetric copies |
|---:|---:|---:|---:|
| 32 | 0 | 1.163 | 0.885 |
| 32 | 8 | 1.067 | 0.904 |
| 128 | 0 | 1.078 | 0.856 |
| 128 | 8 | 1.037 | 0.913 |
| 512 | 0 | 1.077 | 0.930 |
| 512 | 8 | 1.107 | 0.903 |

Each superseded series has a complete .tar.gz archive containing original sources, scripts, build hashes, raw results, traces, CSVs and summaries. Archives were checked byte for byte against every expanded file. The small directories retain directly readable summaries, exclusion reasons and per-file archive-content hashes. They are not alternative selected baselines.

## Limits and reproduction

This is diagnostics-only native memory qualification with explicit post-setter metadata bridges. Native hook integration, persistent storage and end-to-end inference performance were not rerun. No production sources, defaults or CI configuration changed; no develop merge. The earlier frozen complete corpus remains unqualified. Allocated bytes are not RSS; this is not JMH or an application-load benchmark.

From the repository root, reconstruct the hash-verified clean native runtime described in docs/tvalue-layer-cost; use Java 17 and ECJ 3.33 target Java 8 at ../tooling/ecj.jar. Run build.py, scope.py, run.py, analyze.py and make_report.py in this stage directory, sequentially. Source recovery checks verify the exact accepted journal, unchanged qualified reader, copied full-authority method and declared extra scope controls. build-validation.json, summary.json and evidence-sha256.json record source/runtime checks, all metrics and evidence hashes. evidence-sha256.json excludes itself.
