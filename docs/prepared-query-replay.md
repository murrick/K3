# Prepared query replay study

Preparing FALSE and TRUE query contexts once does not provide a qualified optimization of hypothesis replay. Seeded candidate linking changes native SON answers; replacing it with a full Linker pass restores the measured answers but has mixed timing and increases allocation. Keep this mechanism as a shadow study. No production flag, optimizer change, workflow change, or merge is included.

Base: `develop/3.8.0` at `1c943d02d39bd33ebd91fabb7c4190112ab49459`. The eight integrated experimental defaults are enabled. All **651 shared baseline classes are byte-identical**. The runner, build scripts, raw logs, per-source oracle, class hashes and machine-readable analysis are in [prepared-query-replay](prepared-query-replay/).

## Algorithms under comparison

Every candidate retains the current consistency gate: create a fresh child of the original base, render the actual hypothesis in that child, compile it, link from its rule and analyze. Inconsistent candidates are rejected before relevance testing. The exact path calls the ordinary `Mind.query` in that validation child, preserving the current order of hypothesis first, query second.

The alternative prepares separate CHECKFALSE and CHECKTRUE native Mind children of the base once, using the captured query source, canonical external arguments and native inversion. For each candidate that passed validation, it creates fresh hypothesis children of those query contexts, then releases them. A FALSE collision gives FALSE; otherwise a TRUE collision gives TRUE; otherwise the answer remains unknown. The native transaction reservations remain open only for the two query templates and are released at the end.

| Mode | Query template | Candidate fork linking |
| --- | --- | --- |
| `exact` | Ordinary per-candidate replay | Current production order |
| `prepared` | Compile, analyze, link query rule, analyze | Seed from hypothesis rule |
| `compiled` | Compile and analyze; no initial query closure | Seed from hypothesis rule |
| `full` | Same closed templates as `prepared` | `link(null, false)` over all visible rules |

These are native query contexts containing rules, variables and derived state, rather than immutable compiler output. The experiment deliberately checks whether changing creation and inference order preserves observed answers. It does not assume commutativity.

## Exact decisions and counterexample

The SON corpus contains 18 original RAW hypotheses and a merged pool of 47 unique candidates. The exact replay matches all 47 frozen per-source outcomes from the preceding phase study: four validation collisions; among the other 43, four TRUE, two FALSE and 37 unknown. Six hypotheses are accepted.

| SON mode | Per-source mismatches | Accepted hypotheses |
| --- | ---: | ---: |
| `exact` | 0 | 6 |
| `prepared` | 1 | 7 |
| `compiled` | 2 | 6, with a different set |
| `full` | 0 | 6 |

The prepared variant incorrectly accepts `!$y son(John,y);`: exact replay returns unknown, but its TRUE fork reports a collision and returns TRUE. A focus run selects this actual hypothesis from the native expanded pool, without running other candidates first, and reproduces the same difference. Full linking returns unknown for that isolated candidate.

The compiled-only control has the same false positive and also changes `?$y son(John,y);` from FALSE to unknown. Its accepted count remains six, demonstrating why equal cardinality is insufficient. Comparison includes compiled status, validation collision, the exact TRUE/FALSE/unknown answer and acceptance for every source.

Full linking matches all 47 decisions in normal and reversed candidate order, and throughout all 12 measured full-mode samples. The small inference case and external argument replacement on the same root (`item` followed by `other`) match exact replay in both prepared and full modes: `[TRUE, FALSE, unknown]`, then all unknown for `other`.

The six accepted SON sources are:

- `!$x father(x,John);`
- `!$x mother(x,John);`
- `!$x parent(x,John);`
- `!$y child(John,y);`
- `?$y child(John,y);`
- `?$y son(John,y);`

All **16 archived JVM logs / 42 cases** complete their lifecycle assertions: candidate children settle, both templates release, base reservations finish at zero, the base query remains unknown, and the sorted base RAW texts remain unchanged. `PREPARED_QUERY_STUDY_OK` marks harness completion, not algorithm equivalence; the separate comparator records the deliberate failures above.

Full linking is the measured control that restores this corpus. This is not a proof of general semantic equivalence, callback behavior, variable binding confluence or the precise internal cause of the seeded mismatch. Additional corpus coverage alone would not establish those properties.

## Workflow timing

Four fresh JVMs run serially in order **exact e1 → full f1 → full f2 → exact e2**. Each runs six samples on its own base; the first two samples are excluded in each JVM. Every sample's per-source decisions are checked, including the excluded samples. JVM: OpenJDK 17.0.20, `-Xmx512m`; ECJ target Java 8. See [environment.json](prepared-query-replay/environment.json).

The timed interval includes template preparation and release, candidate validation and relevance forks, and lightweight diagnostic buffering/assertions. It excludes the initial logged query, expanded pool generation, final base verification query and diagnostic output. CPU and allocated bytes cover the current Java thread. These figures compare the shadow candidate workflow, not complete `HypothesisStore.optimize` latency or end-user query time. The exact path also includes the native hypothesis effects of ordinary replay; the shadow path records decisions without publishing an optimized hypothesis list.

| JVM | Warm CPU mean, seconds | Warm wall mean, seconds | Warm allocation mean, GB (decimal) |
| --- | ---: | ---: | ---: |
| exact e1 | 5.346 | 5.460 | 4.728 |
| full f1 | 5.166 | 5.371 | 4.991 |
| full f2 | 5.497 | 5.728 | 5.123 |
| exact e2 | 5.143 | 5.247 | 4.688 |

| Pair | Full CPU change | Full wall change | Full allocation change |
| --- | ---: | ---: | ---: |
| e1 / f1 | −3.37% | −1.63% | +5.56% |
| e2 / f2 (opposite launch order) | +6.89% | +9.17% | +9.28% |

All individual samples and warm ranges are retained in [analysis.json](prepared-query-replay/analysis.json). There is no stable timing benefit in this screen, and allocation worsens in both pairs. The safe-on-this-corpus full pass does not justify integrating the proposed optimization. The cheaper seeded variants already fail the native oracle.

## Reproduction and evidence

Run from the repository root with Java, ECJ at `../tooling/ecj.jar` and the existing jline dependency:

```sh
python docs/prepared-query-replay/build.py
python docs/prepared-query-replay/run.py small exact prepared
python docs/prepared-query-replay/run.py son exact prepared compiled full
python docs/prepared-query-replay/run_matrix.py
python docs/prepared-query-replay/analyze.py
```

`run_matrix.py` runs the focused candidate, reverse-order and small full-mode controls, then the four timing JVMs. Every mode uses a separate JVM; timing JVMs are never concurrent. Logs are compressed only after successful process exit; stderr must be empty. Runtime classes and temporary user homes are outside the committed evidence folder. The source list comes from the existing resident-base comparison evidence and production sources remain untouched.

Initial small/SON controls used runner v1; subsequent focus, ordering and timing controls used v2. The archived v1 source rebuilds all four runner classes with exactly their original hashes. V2 adds optional controls and buffers row output outside the timed interval. Shared production classes remain identical in both builds. [runner-revisions.md](prepared-query-replay/runner-revisions.md) records this boundary.

`manifest.json` hashes every committed evidence file and this report, excluding the manifest itself. The branch records a finite negative optimization result and a reproducible native counterexample. Any future query preparation work needs a different representation or an explicit semantic boundary before integration; this study does not leave another micro-variant queue open.
