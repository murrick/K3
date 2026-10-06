# Linker frontier: work census and dependency boundary

Later saturation passes dominate the measured SON hypothesis workflow, so incremental scheduling is a substantial algorithmic direction rather than another allocation tweak. However, a frontier based only on changed Rule/TValue objects is not a sound boundary. Native witnesses show compatibility and branch production changing while those inputs stay the same. A second boundary matters too: the current rotator collection controls both execution and donor discovery; filtering it removes valid donors.

This branch records a diagnostic and **31 native checks on each build**, not an incremental scheduler or a qualified speedup. Production, qualification sources and workflows remain identical to `develop/3.8.0` at `1c943d02d39bd33ebd91fabb7c4190112ab49459`.

## Work measured

One clean JVM and two independent diagnostic JVMs each compile `natives.k`, run the ordinary logged `?$x son(John,x);` query and separately optimize hypotheses for three samples. All **nine complete snapshots** match the frozen 18 RAW / six optimized text lists, unknown query answer, zero solutions and values, and last-Linker statistics `passes=6 rules=1292 rotations=6824 pairs=13281 unifications=3080`.

Only optimization is observed. Its **133 Linker invocations** comprise 47 candidate validation links, 44 CHECKFALSE links and 42 CHECKTRUE links. Those query-pass counts include expanded reconstruction. They match the preceding phase census. The cumulative figures below cover every observed invocation, not just the final link returned by `Mind.getLinkerStatistics()`.

| Work per warm optimization | Count |
| --- | ---: |
| Rule visits, both directions | 156,374 |
| Terminal rotations | 858,101 |
| Domain pairs | 1,599,204 |
| Unification attempts | 367,807 |
| New-TValue attempt increments | 20,007 |
| Canonical TSolve version increments | 29,179 |
| Visits with no new-TValue attempt but another counted state change | 24,010 (15.35%) |
| Visits with no change in the observed counters | 126,557 (80.93%) |

The observer reads native map/set/list cardinalities, existing statistics and action flags. “No counted change” is **not** semantic equivalence: it omits current binding changes, same-cardinality content changes, visibility/restoration, function state and other unobserved effects. New-TValue attempts are the existing Linker counter, which is not a journal of surviving canonical insertions. Action transitions are changes in saturated Boolean flags, not counts of every effect.

The two first samples of each diagnostic JVM are excluded from timing summaries. Each JVM contributes one warm observation, sample 2. All nontiming group counters match between the two diagnostic JVMs at every sample index and between samples 1 and 2 within each JVM.

| Pass number, aggregated across links | Rule visits | Unification attempts | Observed rule CPU, seconds (two JVMs) |
| --- | ---: | ---: | ---: |
| 1 | 2,998 | 660 | 0.056–0.059 |
| 2 | 13,656 | 23,882 | 0.357–0.365 |
| 3 | 32,576 | 81,471 | 0.887–0.921 |
| 4 | 37,878 | 91,710 | 1.077–1.119 |
| 5 | 32,268 | 78,496 | 0.943–1.007 |
| 6 | 20,554 | 50,506 | 0.638–0.657 |
| 7 | 15,008 | 36,644 | 0.449–0.482 |
| 8 | 476 | 1,474 | 0.022–0.022 |
| 9 | 480 | 1,482 | 0.022–0.023 |
| 10 | 480 | 1,482 | 0.022–0.023 |

Passes after the first account for **98.74% of observed rule-visit CPU**, 98.08% of visits, 98.52% of rotations and 99.82% of unifications. This partly reflects expansion of the active rule set: an invocation starts from a seed and includes used/opposite/generated rules in subsequent passes. Later visits are not all repeats of an already processed immutable rule.

CPU intervals cover the original per-rule body inside `rotator`, using current-thread `ThreadMXBean` time. They exclude domain-index construction, active-set expansion, observer snapshots and outer optimizer work. Observer overhead outside those intervals can still affect execution indirectly. These are descriptive instrumented fractions, not complete optimizer CPU shares, removable cost estimates, or an OFF/ON benchmark. No end-user latency claim follows from them.

## Both traversal directions contribute

Each pass retains the original descending-long-ID traversal followed by ascending-long-ID traversal. Every archived pass has the same number of rules in both directions.

| Direction | Rule visits | TSolve version increments | Excluded tuple-count increase | Cause-count increase |
| --- | ---: | ---: | ---: | ---: |
| Descending | 78,187 | 28,160 | 36,472 | 34,226 |
| Ascending | 78,187 | 1,019 | 180 | 183 |

Ascending also records 9,354 new-TValue attempts, 6,052 used tuple-count increases and 663 hypothesis-count increases. It consumes 48.45–48.84% of the timed rule body CPU. A lower effect count does not establish that this traversal can be removed: its distinct effects still participate in the native fixed point and current control flow.

## Native dependency witnesses

The witnesses use actual Mind, Rule, TVariable, TValue, TSolve and Domain objects. Reflection calls existing private kernels to isolate a dependency; it does not replace their implementation or prove a complete scheduler equivalent.

| Boundary | Observed native result |
| --- | --- |
| Same current values; add a canonical TSolve | `isValidFor` changes allowed → rejected → allowed as compatible tuples become available. All five factory/store action flags stay false. |
| Same current values and TSolve version; append through an exposed native solve-list alias | Compatibility changes rejected → allowed. The existing exposed-map fallback observes the appended tail slot. |
| Same clause, values and TSolve version; mark one Domain excluded | `linkDatabase` changes from producing no Domain to producing the remaining Domain. Factory/store actions remain false at this classification stage; Rule materialization happens later. |
| Same single-domain receiver; index only its scheduled subset rather than all available donors | No `item` TValue is created with the subset index. Keeping the base donor in the index creates that binding. Both isolated children are released. |

The donor fixture uses a native `seed(item)` base fact and the native receiver `!@x ~seed(x);`. It directly exercises `buildDomainIndex`, `selectDomainCandidates` and `linkDomains`. It proves donor loss and binding loss in this fixture, not equivalence of a proposed work queue or a complete query Boolean difference.

The existing pass continuation flags answer whether another global pass is needed. They neither identify every changed semantic owner nor encode which rules read that owner. The TSolve version helps with canonical append detection, but exposed aliases can change tuple state without advancing it. Even adding that version to a Rule/TValue signature misses the Domain exclusion witness.

## What a future frontier must separate

The current `rotator(ruleList, ...)` builds its donor index from the very collection it executes. A future design must retain the complete native active donor universe while selecting a separate execution subset. Candidate filtering, stored/generated visibility and the original ascending/descending order remain semantic boundaries.

| Dependency family | Required scheduling boundary |
| --- | --- |
| Rule population and generated/primary/deleted visibility | Update the active universe and donor membership, including promotion/restoration rather than only new IDs. |
| TValue availability and current binding/visibility | Invalidate actual readers of changed bindings; insertion counts alone are insufficient. |
| TSolve compatibility | Invalidate readers of affected tuple groups; retain conservative treatment of exposed aliases. |
| used/excluded/calculated stamps and Cause state | Track semantic changes with their context and argument stamp; object identity and cardinality do not define equality. |
| Produced Domains and delayed Rule creation | Distinguish classification from publication, preserving the current boundary after branch traversal. |
| Function/system/UDF and other untracked reads | Require a justified conservative path rather than assuming logical purity. |
| Transaction checkpoints | Publish only surviving effects; rollback must restore scheduling state as well as semantic state. |

This table is a set of design obligations, **not** an implemented or proven event journal. The census identifies a large work area and rejects incomplete scheduling signatures. It does not reject every incremental algorithm, justify skipping the 80.93% group, or establish a savings estimate. There is no new default-OFF production prototype to merge. The next meaningful implementation would be a distinct scheduling project with explicit dependency ownership and equivalence checks, not another local flag variant.

## Verification and evidence

Both builds pass the unchanged completed-hypothesis contract: historical male list 14, historical conjunction eight, Console premise probe one and external `item` → `other` replacement. Exact candidate replay compares all **47 compiled/collision/TRUE/FALSE/unknown/accepted outcomes** against the frozen source map on both builds. The final profile candidate runner activates observation while collecting that map. Small and external cases also preserve `[TRUE, FALSE, unknown]` and then all unknown for `other`, unchanged RAW texts and zero reservations.

The clean build matches all 651 baseline class hashes. Only the temporary Linker source is instrumented; five shared class hashes differ (Linker and its four anonymous classes). Anonymous-class differences were not separately decoded. Helpers and drivers are diagnostic-only. Production, qualification and workflow diffs are empty. The final added donor witness changes only the witness class relative to the archived census manifests; Linker/helper/full-optimization driver bytecode remains identical. The final active candidate driver is added after the census. Earlier harness corrections and initial failing launches are archived and excluded from passing evidence; see the two initial-launch notes in the evidence folder.

From the repository root, with ECJ at `../tooling/ecj.jar` and the existing jline dependency:

```sh
python docs/linker-frontier-census/build.py
python docs/linker-frontier-census/witness.py
python docs/linker-frontier-census/run.py
python docs/linker-frontier-census/analyze.py
```

JVMs run serially with the eight integrated flags ON and fresh temporary user homes. Full logs are compressed only after JVM completion. The observer is intended for this single-thread census; it is not a concurrent runtime service. Source-level calls, original matching order, canonical factories and commits/releases are retained. Qualification source and production compatibility CI are unchanged; this documentation-only branch does not claim a new compatibility CI run.

[Compact summary](linker-frontier-census/summary.json) contains aggregates and timing scope; [detailed compressed JSON](linker-frontier-census/details.json.gz) and raw compressed logs preserve every observed link/pass/order group and selected witnesses. [Instrumentation patch](linker-frontier-census/instrumentation.patch), class hashes, runners, frozen source oracles, environment and reproduction scripts are alongside them. `manifest.json` hashes every evidence file and this report, excluding itself.
