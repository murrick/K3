# Warm CPU and allocation profile after eight integrated optimizations

Base: live `develop/3.8.0 @ 1c943d02d39bd33ebd91fabb7c4190112ab49459`, verified on 2026-10-05. Branch `experiment/3.8.0-warm-profile`. All production sources, qualifications and workflows are identical to this base. All eight integrated flags are explicitly ON. The rejected lazy-binding prototype is absent.

## Phase boundary and validation

Two sequential fresh Java 17.0.20 JVMs, six `son(John,x)` query/optimization samples each, 512 MiB heap. Only samples 2–5 contribute to profile tables. The standalone diagnostic runner is copied from SonProfileRunner and adds a JFR duration event around each ordinary optimizeHypothesis invocation, with its sample number. Analysis selects main-thread events whose timestamps fall inside those four duration windows. Compilation, original query, printing and the first two optimizations are excluded. Sampling uses JFR profile settings and depth 256. No selected sampled stack is truncated.

Both recordings have complete six-sample output with empty stderr. All 12 full RAW/OPTIMIZED texts match one another and the clean baseline reference from the lazy-binding experiment: 18 raw / six final hypotheses, unknown result and no solutions/values. All 12 last Linker statistics match: passes=6, rules=1292, rotations=6824, pairs=13281, unifications=3080. Assertions are executable in analyze.py. A preliminary recording had incomplete stdout and was excluded rather than treated as a qualified observation; retained runs are numbered 2 and 3.

This is profiling, not a timing benchmark or a new correctness qualification of an optimizer. JFR and diagnostic events add overhead. Production and callback traversal are unmodified. No cross-JDK CI is requested for a docs-only diagnostic checkpoint.

## Repeated CPU attribution

The first KANGER frame in each selected ExecutionSample is attributed to its method and source line, then grouped by method. This is statistical stack attribution, not exact CPU accounting or a count of safely removable calls. JIT/inlining can move apparent cost among frames.

| First KANGER method | Run 2 samples / share | Run 3 samples / share |
|---|---:|---:|
| Mind.isUnitDeleted | 395 / 19.49% | 374 / 17.86% |
| TVariable.activeMind | 195 / 9.62% | 165 / 7.88% |
| Predicate.getName | 161 / 7.94% | 173 / 8.26% |
| TValueFactory.isEmpty | 77 / 3.80% | 97 / 4.63% |
| Total selected ExecutionSample events | 2,027 | 2,094 |

These methods were previously investigated. Their presence here does not overturn negative timing results of empty deletion layers, direct current lookup or direct predicate names, and does not justify bypassing binding/deletion effects. Inclusive tables and up to 50 leading KANGER call paths are retained separately; inclusive shares overlap and must not be added.

## Allocation attribution

ObjectAllocationSample weights are sampling estimates, not exact class allocation counts, retained heap or CPU shares. Results aggregate weights of all classes whose first KANGER frame is the named method. ThreadMXBean sample totals in logs separately count cumulative main-thread allocation over each whole optimization.

| First KANGER allocation site | Run 2 weighted MB / share | Run 3 weighted MB / share |
|---|---:|---:|
| Predicate.getName | 2,008.8 / 10.23% | 2,328.8 / 11.93% |
| ArgumentsList.getTVariables | 1,732.3 / 8.83% | 1,328.9 / 6.81% |
| TVariable.setMind | 751.1 / 3.83% | 568.1 / 2.91% |
| ArgumentsList.getStamp | 424.5 / 2.16% | 530.0 / 2.72% |
| Total weighted bytes across four warm windows | 19,627.7 MB | 19,515.3 MB |

ArrayList backing-array growth at getTVariables is visible in both recordings, separately from list objects. Source initializes its collector with the default ArrayList constructor, whose first insertion reserves more than one slot. This differs from the completed lazy-binding experiment, which avoided empty collector creation but left nonempty collector growth unchanged. The profiles do not count list-length frequencies or separate Domain-binding callers from all other callers of getTVariables.

## Decision and next bounded candidate

Do not repeat the already rejected shortcut variants solely because they remain prominent in the profile. Keep ordinary Rule hydration, all binding effects, argument traversal, duplicate/deletion checks and the original exception/callback order.

A distinct next hypothesis is reducing spare capacity in **nonempty internal binding collectors**. First measure their length distribution and the fraction attributable to Domain binding, then consider a small initial capacity while retaining complete enumeration before binding. Capacity must remain an internal implementation detail; public getTVariables and custom subclass paths must retain their reference contract. No result cache or early binding is implied. This checkpoint establishes the allocation lead, not a qualified implementation or a performance gain. The lazy-empty-list result remains negative.

## Reproduction

External ECJ at `../tooling/ecj.jar`, Java 17 runtime with JFR:

```sh
bash docs/warm-profile/build.sh
python docs/warm-profile/run.py
python docs/warm-profile/analyze.py
git diff --exit-code 1c943d02d39bd33ebd91fabb7c4190112ab49459 -- kanger kanger-udf kanger-qualification .github
```

Core/UDF sources are compiled at Java 8 target; only the diagnostic runner/reader target Java 17. No JFR dependency enters the reactor. Classes and raw recordings are reproducible scratch files under `../build`. Reports retain source manifest, runner, reader, scripts, full text snapshots and aggregated class/site tables; CPU_PATH output is limited to the leading 50 entries. No merge, production flag, default activation or storage-format change is introduced.
