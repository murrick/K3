# Hypothesis validation and replay: final bounded investigation

Checkpoint: 2026-10-06. Production, qualification sources and workflows remain identical to develop/3.8.0 at `1c943d02d39bd33ebd91fabb7c4190112ab49459`. Eight integrated flags are enabled. This branch records a diagnostic of the current algorithm; it adds no production optimization or flag.

**Outcome:** the remaining cost is repeated inference under different candidate assumptions. In the measured SON optimization, RAW expansion produces 47 unique candidates; consistency rejects four and 43 exact replays retain six. Replay accounts for 65.02–67.25% of coarse main-thread CPU, and candidate linking for 29.65–30.67%. Native witnesses reject unchanged-child reuse, query-text-only answer caching and consistency-only acceptance. This closes the last direction of the bounded micro-optimization round. A substantial further reduction needs an execution-model change with a proof of candidate isolation and three-valued query semantics; no such change is qualified here.

## Protocol and checks

One clean JVM and two independent diagnostic JVMs each compile `natives.k`, execute the ordinary logged `?$x son(John,x);` query, render RAW texts, and separately optimize hypotheses for six samples. The first two samples per JVM are excluded from warm summaries. All **18 semantic snapshots** match full RAW/optimized texts, unknown initial query Boolean, 18 RAW / six final hypotheses, zero solutions and values, and final-Linker statistics `passes=6 rules=1292 rotations=6824 pairs=13281 unifications=3080`.

The unchanged `KangerCompletedHypothesisContractRunner` passes on both builds: historical male list has 14 entries, historical conjunction eight, the Console premise probe one, and external replay replaces `item` with `other` without retaining the stale premise. Its outputs and compiled class hashes match between builds. Native protocol witnesses pass **30 checks** on each build, also with identical output and class hashes.

Counters, phase invocation counts, core method calls, pass answers and source-to-candidate outcomes match between JVMs at every sample index and across all eight warm diagnostic samples. Source order is not an oracle; analysis compares the complete outcome mapping by source.

## Candidate census

| Event per optimization | Count |
|---|---:|
| Original RAW hypotheses | 18 |
| Expanded RAW source strings rendered | 29 |
| Newly admitted expanded sources | 29 |
| Expanded sources already present by source text | 0 |
| Candidates after merge | 47 |
| Distinct source strings compiled | 47 |
| Repeated candidate source strings | 0 |
| Successful candidate compilations | 47 |
| Candidate validation collisions | 4 |
| Noncolliding candidates replayed | 43 |
| Replay answers TRUE / FALSE / unknown | 4 / 2 / 37 |
| Accepted hypotheses | 6 |

All counted candidates are exactly native `Hypothesis` instances. The expanded query itself returns unknown. Every expanded source in this workload is new to the original store; there is no duplicate-source validation loop to eliminate.

The four colliding candidates already skip replay in production. The 37 unknown replay answers are necessary negative relevance results: those candidates are consistent but do not make the original query decidable. FALSE is a valid decisive answer and retains two candidates; treating relevance as TRUE-only would change the frozen six-entry result.

| Accepted candidate source | Exact replay answer |
|---|---|
| `!$x father(x,John);` | TRUE |
| `!$x mother(x,John);` | TRUE |
| `!$x parent(x,John);` | TRUE |
| `!$y child(John,y);` | TRUE |
| `?$y child(John,y);` | FALSE |
| `?$y son(John,y);` | FALSE |

The complete source/outcome mapping, including all rejected hypotheses, is in [summary.json](hypothesis-phase-forensics/summary.json).

## Fresh coarse phase distribution

The following ranges are the **two per-JVM warm aggregate shares**, each aggregating samples 2–5. CPU is current-main-thread CPU, measured with ThreadMXBean. Allocation is current-main-thread allocated bytes. Primary phase intervals are disjoint; the FALSE/TRUE rows below are nested subdivisions and must not be added again. These are descriptive timings of the instrumented base algorithm, not an OFF/ON experiment or an estimate of removable cost.

| Primary phase | Calls | Main-thread CPU share | Main-thread allocation share |
|---|---:|---:|---:|
| Candidate query replay | 43 | 65.02–67.25% | 64.89–64.90% |
| Candidate Linker before validation | 47 | 29.65–30.67% | 31.01–31.04% |
| Expanded RAW query | 1 | 1.53–2.88% | 1.51% |
| Candidate release | 47 | 0.58–0.65% | 1.79–1.82% |
| Candidate collision analysis | 47 | 0.44–0.51% | 0.41–0.42% |
| Candidate rendering + compilation | 47 | 0.29–0.32% | 0.30–0.31% |
| Candidate child construction | 47 | 0.04% | 0.02% |

Expanded child creation, source merge and release together each occupy small residual shares. Unattributed bookkeeping is 0.037–0.061% of main-thread CPU. Creation plus rendering/compilation of the candidates is below 0.36% in each JVM. That figure covers the candidate setup phases, **not** compilation occurring inside replay passes; the latter is included in replay and was counted but not timed separately.

| Nested replay pass | Calls | TRUE / FALSE / unknown | Share of whole optimization CPU |
|---|---:|---|---:|
| Candidate FALSE check | 43 | 0 / 2 / 41 | 44.52–45.99% |
| Candidate TRUE check | 41 | 4 / 0 / 37 | 20.48–21.23% |
| Expanded FALSE check | 1 | 0 / 0 / 1 | 0.96–2.08% |
| Expanded TRUE check | 1 | 0 / 0 / 1 | 0.57–0.80% |

The two decisive FALSE checks already bypass TRUE checking. The current query implementation therefore performs 84 candidate query passes rather than 86. Expanded reconstruction adds two passes. Removing FALSE checking would lose the two negative decisive results and alter the final candidate list.

Absolute timing varies materially: per-JVM warm mean main-thread CPU is 7.845 and 10.361 seconds; mean elapsed is 8.005 and 10.619 seconds. All eight samples remain included, including the second JVM's slower sample 2. The report uses the stable work census and descriptive phase proportions; it makes no speedup claim or reliable end-user latency prediction.

## Physical context and method counts

| Method entries in the optimization interval | Count |
|---|---:|
| `Mind(IMind)` child constructors | 267 |
| Public three-argument `Mind.compileLine` | 133 |
| `Mind.link` | 133 |
| `Mind.analyze` | 219 |
| `Mind.release` | 134 |

The 267 child contexts comprise 47 candidate contexts, 47 technical candidate-compilation contexts, 168 contexts for the 84 candidate query passes, and five expanded-reconstruction contexts. Each query pass has its query transaction plus a compilation transaction. The low direct constructor cost does not mean that the inference work performed in those contexts is cheap.

Core counts include operations inside the timed passes; they are not extra validation calls. Candidate rendering/compilation has 47 compile entries, replay has 84, and expanded reconstruction two. The same original query source is replayed under 43 distinct assumptions and returns all three truth states. A cache keyed only by query source would combine different semantic states.

## Native safety witnesses

[HypothesisPhaseWitness.java](hypothesis-phase-forensics/HypothesisPhaseWitness.java) exercises the existing compile/link/analyze/query/release protocol with native objects and a small `seed -> target` program. It does not replace production optimization or qualify a new reusable-context implementation.

| Boundary or control | Native observation |
|---|---|
| Reuse one child unchanged for successive candidates | Fresh children yield `[true, unknown]` for `seed(item)` then `unrelated(item)`; a shared child yields `[true, true]` because the first assumption remains present. |
| Cache answers by original query text only | The identical query has TRUE and unknown answers in the fresh-child sequence. Candidate state changes the answer. |
| Accept all consistent candidates without replay | Three consistent assumptions produce `[true, false, unknown]`. Consistency does not establish relevance. |
| Collision control for the existing gate | A positive `seed(item)` assumption against native `~seed(item)` is inconsistent and must be rejected. |

The base query remains unknown after the candidate contexts are released, checking that the isolated assumptions do not become base facts. The first three observations invalidate those generic reuse/acceptance boundaries without subclasses, custom callbacks or concurrency. The collision control verifies the existing validation signal. They do not rule out a correctly implemented rollback/reset protocol, complete state-aware memoization or a new inference algorithm. Such mechanisms would need separate proofs and qualification.

## Decision: this local round is complete

There is no qualified new optimization from this direction. The algorithm already suppresses replay after collisions, suppresses TRUE checks after decisive FALSE checks, deduplicates expanded sources, and operates on 47 distinct candidate texts in the measured workload. Replacing direct child construction addresses a very small measured phase while unchanged-child reuse breaks isolation.

The two remaining bounded directions are now closed with negative/safety results: [resolved candidate hydration](https://github.com/murrick/K3/blob/experiment/3.8.0-resolved-candidate-forensics/docs/resolved-candidate-forensics.md) and this validation/replay census. [Round checkpoint](remaining-optimization-directions.md) records that no further local variants are queued. Existing shipped optimizations remain the current base; unmerged prototype flags remain OFF.

A future performance project could investigate incremental replay against separately identified candidate state, with a static query plan and fresh runtime bindings. This report establishes neither a safe implementation nor a savings estimate for that design. Query-source equality, object identity and absence of collisions are insufficient cache boundaries. Acceptance still requires an exact positive or negative query answer; unknown must remain rejected.

## Reproduction and evidence

From this worktree, with Java 17 and ECJ targeting Java 8:

```sh
python docs/hypothesis-phase-forensics/build.py
python docs/hypothesis-phase-forensics/witness.py
python docs/hypothesis-phase-forensics/qualify.py
python docs/hypothesis-phase-forensics/run.py
python docs/hypothesis-phase-forensics/analyze.py
```

Only temporary copies of Mind and HypothesisStore are instrumented. Original calls, callback evaluation order, child lifecycle, commits/releases, query parameters and semantic decisions are retained. Source strings are recorded from original rendering expressions; extra private-store sizes, class identity, counters and timers add diagnostic overhead. Main-thread timers exclude other threads' CPU and allocation. Total timing is captured before diagnostic output. Logs are compressed only after a JVM exits.

Three shared class hashes differ: Mind, its anonymous class and HypothesisStore, confined to the two modified temporary sources. The anonymous-class difference was not separately decoded. Helpers and the phase runner are diagnostic-only. Qualification source and production/workflow diffs against the base are empty; there is no new production compatibility CI run. The initial contract launch could not find a runner absent from the baseline compilation manifest; qualify.py now explicitly compiles that unchanged runner. [Initial launch note](hypothesis-phase-forensics/initial-contract-launch.md) and error are retained and excluded from qualification.

[Summary](hypothesis-phase-forensics/summary.json), [source patch](hypothesis-phase-forensics/instrumentation.patch), [manifest](hypothesis-phase-forensics/manifest.json), class hashes, full compressed phase/candidate logs, contract and witness output, and reproducible scripts accompany this report.
