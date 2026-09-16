# Independent group-count / tuple-count experiment

Parent `8fad0d9`. New qualification runner only; inference code and defaults
are unchanged. `LatentSolveSyncShapeRunner output.csv` isolates synchronization
with 4, 32 or 128 groups and 1 or 16 initial tuples per group. Each group has
two distinct TVariables; each tuple has a different TValue for each variable.

## Procedure and correctness

For each fresh Mind/Linker, publish the initial tuples, call synchronization
500 times, publish one more tuple in every existing group, then synchronize
500 more times. Publication and checks are outside the timed sections. The
timed sections include first-time indexing in each phase, reference group
scanning or version checks, reflective invocation overhead and any JVM pauses.
This is not the entire inference loop and does not benchmark transaction work.

After both phases, every tuple must be returned by the candidate index for each
of its two values, as the exact original object with no extra candidates. This
fixture has one candidate per value; it does not replace corpus coverage of
multi-candidate ordering. No public map alias is exposed.

Mode order alternates within each shape/sample. Warmups default to 3, measured
samples to 7, with benchWarmups/benchSamples overrides. benchReverseShapes
reverses both shape axes. CSV is written directly once after successful runs.

## Work established by the verifying run

factory-verify, profileSolveSync=true, zero warmups and one sample per shape/mode
passed all 12 cases. Each proposed skip was checked by the existing reference.

| Groups | Reference group visits | Versioned group visits | New slots, 1 initial tuple/group | New slots, 16 initial tuples/group |
|---|---:|---:|---:|---:|
| 4 | 4000 | 8 | 8 | 68 |
| 32 | 32000 | 64 | 64 | 544 |
| 128 | 128000 | 256 | 256 | 2176 |

Each case has exactly 1000 requests; reference performs 1000 scans, versioned
performs 2 scans and 998 skips. New-slot counts are equal in both modes. The
runner asserts these counts and checks tuple identity after every phase.

## Isolated timing observations

Three separate JVMs in forward shape order and three in reverse order,
`-Xms256m -Xmx512m`, default 3 warmups + 7 measured samples per shape/mode.
No factory-verify, phase timers, work observer or invocation tracing during
timing. All six files have 84 complete measured rows: 504 cases in total, each
with the same correctness checks. Work-counter columns are zero when their
observer is disabled; they do not imply zero actual work.

Median of the three per-JVM medians, milliseconds for both 500-call sections:

| Groups | Initial tuples/group | Forward reference | Forward versioned | Reverse reference | Reverse versioned |
|---|---:|---:|---:|---:|---:|
| 4 | 1 | 0.7812 | 0.2391 | 1.3364 | 0.0209 |
| 4 | 16 | 0.4727 | 0.1943 | 1.4217 | 0.0413 |
| 32 | 1 | 10.1726 | 0.1689 | 1.7614 | 0.0375 |
| 32 | 16 | 2.2620 | 0.3512 | 2.0450 | 0.1393 |
| 128 | 1 | 8.3870 | 0.2750 | 8.1092 | 0.1239 |
| 128 | 16 | 9.3440 | 0.8875 | 10.2694 | 1.4284 |

Versioned synchronization is faster in these isolated samples, but the strong
shape-order sensitivity prevents interpreting these ratios as stable costs or
query speedups. In particular, the reference 32x1 case changes from 10.17 to
1.76 ms when order reverses. The exact cause was not profiled; JIT, GC, code
layout, allocation and runtime state are not isolated by this simple harness.
Three warmups do not establish steady state. Both orders are retained, not
pooled into a flattering ratio. End-to-end evidence remains the earlier native
benefit and lack of benefit on facts/symmetric examples.

## Decision

The group-scan complexity hypothesis is supported directly by operation counts,
independently of tuples per group. Correctness survives new publication in
every group between stable periods. No new optimization is justified by the
microtimings alone. Keep the flag off by default. Next useful work is a
transaction-level scenario exercising new tuple publication and rollback while
comparing the complete logical projection with the reference path.

Evidence: `latent-substitution-evidence/solve-shapes/`. OpenJDK 17.0.20 / ECJ 3.33
Java 8 target. The existing corpus was not rerun because production code did
not change; the new shape qualification and identity checks are described above.
Full Maven / canonical Java 8 and 21 qualification is still outstanding.
Live develop remains `3ad50f1e5253304f6b530de11c078f332ea4db89`.
No merge, release, tag or deploy.
