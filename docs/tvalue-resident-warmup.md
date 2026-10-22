# TValue resident inference: warmup and compilation study

2026-10-22. Parent `f21b2f3f1cdf98fd1769d8dd37685635bc2c8fae`;
branch `experiment/3.8.0-tvalue-resident-warmup`.
Native pin remains `2422f7f6d9e1af7608203b1566df64b5f29fe344`.
Only new qualification fixtures, scripts, reports and evidence are added.
No integration or production/default change is proposed.

## Finding

The previous three-cycle warmup did not reach a stable interval. In the archived
Java 17 series, median baseline query time for `on` fell from 41.89 ms at zero-based
iteration 3 to 20.46 ms at iteration 8. The new series continues to change even
near iteration 60. A longer fixed warmup alone has not established a plateau.

The prior `off` slowdown remains an observation requiring qualification. In the
new host/JDK series it is smaller in the last window with default compilation,
but changes direction with `-Xbatch`. This neither proves a persistent regression
nor clears the earlier observation. There is no established inference speedup.
The candidate stays separate.

## Frozen code and changed environment

The original ECJ 3.33 compiler hash is verified. Both diagnostic modules were
rebuilt from their exact historical sources (`73c5d6c2` baseline and parent
candidate); every module class hash matches its frozen manifest. All Kanger
runtime classes also match the clean/hooked frozen manifests. Of 712 runtime
classes, only bundled Rhino `org/mozilla/javascript/NativeArray.class` differs
when compiled against Java 21 instead of Java 17. The same rebuilt class is used
in clean and hooked runtimes. This difference is explicitly allowed and recorded;
we do not claim that the complete runtime is byte-identical to Java 17.

The machine is macOS aarch64, Temurin 21.0.6, shared, without CPU pinning or
exclusive access. Previous evidence came from Java 17.0.20. The two series are
not pooled, and cross-host absolute timings are not used to infer acceleration.
No competing benchmark was launched by this study.

## Design and correctness

72 unlogged JVMs: default/batch × on/off/verify × three repetitions ×
clean/disabled/baseline/candidate. Each JVM executes 60 cycles of the same seven
public Mind queries, with the same seed, expected answers and exact returned
substitutions as the parent fixture. Module order reverses in the middle repeat;
profile order also reverses. The input and assertion/session lifecycle are
unchanged. The timing fixture adds cycle start/end monotonic timestamps to CSV.
Cycle and query timing/allocation boundaries are inherited from the parent.
Query intervals contain Mind.query and attached callbacks; explicit observes,
assertions and finish remain outside query intervals.

Four extra `off` JVMs (default/batch × baseline/candidate) collect LogCompilation.
A diagnostic-only fixture records a JVM uptime/monotonic-clock correlation before
seeding. Correlation uncertainty is the sampling interval plus one millisecond.
These four JVMs are excluded from timing comparisons. Four preceding logging
runs without clock correlation are preserved under `pre-alignment/`, also
excluded. **80 JVMs physically executed, 76 final runs plus four archived runs**;
33,600 total queries, including 31,920 in the final dataset. No failed JVM run.

All native rows match the corresponding clean control, including exact variable
values and transaction/root authority checks. Final dataset: 2,400 trace replays,
1,200 byte-identical baseline/candidate trace pairs, and equal callback counts.
The four archived runs add 240 replayed traces and 120 identical pairs.
All cycle traces, including early cycles, are checked. No sessions retain observer
or context references after close. The final dataset checks 9,120 variable rows;
including archived runs, 9,600. SMART, full inference corpus and concurrent native
sessions are not qualified. Existing diagnostic failure/wiring tests were not
repeated because runtime/module Kanger bytecode is unchanged.

## Timing by window

Windows are zero-based half-open ranges. Within each JVM take a window median,
then take the median of three paired candidate/baseline ratios. The table shows
query intervals. Logging runs never enter these ratios. The final window is
called **late**, not steady-state.

| Profile | Flags | Cycles 3–8 | Cycles 12–23 | Cycles 36–47 | Cycles 48–59 | Late pairs slower |
|---|---|---:|---:|---:|---:|---:|
| default | on | 1.092 | 1.082 | 1.048 | 0.977 | 1/3 |
| default | off | 0.974 | 0.977 | 1.035 | 1.022 | 3/3 |
| default | verify | 0.995 | 1.099 | 0.960 | 0.982 | 0/3 |
| batch | on | 0.962 | 0.980 | 1.015 | 1.026 | 2/3 |
| batch | off | 0.866 | 0.971 | 1.031 | 0.973 | 1/3 |
| batch | verify | 1.006 | 0.983 | 0.969 | 0.995 | 1/3 |

Some batch pairs are particularly dispersed: late `on` ratios are
1.027/0.675/1.026, and late `off` ratios are 0.973/1.084/0.795. A three-pair median
cannot establish equivalence or statistical significance.

Across the nine baseline JVMs per profile, median late/preceding-window query
ratio is 0.861 with default JIT and 0.719 with batch; candidate is 0.877/0.713.
All those ratios are below one (individual baseline ranges 0.838–0.944 and
0.650–0.952; candidate 0.825–0.916 and 0.625–0.913). Thus even the last two
windows still move substantially. The data do not justify assigning a universal
warmup cutoff or treating the late ratios as final throughput measurements.

Late paired allocations still favor the candidate: total cycle savings are
68,992 B for every default flag cell, and 56,464–64,144 B across batch cells.
This is the whole diagnostic cycle and cannot be attributed solely to TOUCH
formatting or promoted into an inference performance claim.

## Compilation evidence

For the separately logged runs, counts below are completed Kanger `nmethod`
installations whose timestamps fall within cycle windows (including gaps between
cycles in the same window). These are not counts of unique methods, and include
multiple compilation levels/OSR where present.

| Profile/module | Cycles 36–47 | Cycles 48–59 |
|---|---:|---:|
| default baseline | 177 | 53 |
| default candidate | 120 | 62 |
| batch baseline | 70 | 18 |
| batch candidate | 70 | 21 |

The late batch compilations include RuleFactory.collectTermIds and
hasActiveRuleWithTerm at level 4; the default runs still compile pack/update and
Mind.compileLine paths. Full method/level/timestamp and invalidation records are
in compilation-summary.json. These observations establish that compilation still
occurs late in the diagnostic runs. They do not prove that JIT alone causes each
unlogged timing difference; logging alters execution, and GC/host activity was
not isolated. No inference is made about identical compiled machine code.

## Reproduction and provenance

From repository root, provide the exact native source tree at
`../K3-observer-native`, ECJ 3.33 at `../tooling/ecj.jar`, and bundled jline.
Native sources can be obtained with `git archive` from the pinned commit; no
native worktree changes are required. Use Java 21.0.6 for this specific rebuild.

```sh
python3 docs/tvalue-resident-warmup/build.py
python3 docs/tvalue-resident-warmup/run.py
python3 docs/tvalue-resident-warmup/diagnostic.py
python3 docs/tvalue-resident-warmup/analyze.py
python3 docs/tvalue-resident-warmup/compilation.py
python3 docs/tvalue-resident-warmup/manifest.py
```

`diagnostic.py` preserves the initial four logged runs and replaces their final
slots with clock-correlated runs. It does not replace the 72 timing JVMs.
The build validates frozen runtime/module classes; manifest verifies the rebuilt
classes, hashes artifacts and native input sources, and rejects any changed
parent file outside this new documentation/fixture scope. Earlier manifests and
evidence remain intact. Paths in execution records identify the actual checkout.

## Decision and next step

Do not integrate the candidate on these timings. The concrete finding is that the
previous sampling window was dominated by ongoing warmup, and even 60 cycles are
insufficient to declare stability for this fixture/JDK. Next timing work should
use bounded longer resident runs with predeclared consecutive-window stability
criteria, a separate compilation diagnostic, and an explicit unstable outcome
when the cap is reached. Individual query intervals should be recorded outside
their timers to distinguish ground and substitution paths. Retain clean/native
and both-journal controls; do not change production defaults to improve a score.
