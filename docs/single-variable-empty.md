# Guarded single variable-emptiness lookup

Base `develop/3.8.0 @ 1c943d02d39bd33ebd91fabb7c4190112ab49459`, verified live 2026-10-06. Branch `experiment/3.8.0-single-variable-empty`. Default OFF: `-Dkanger.experiment.singleVariableEmpty=true` (restart JVM). No merge or default enablement.

## Change and scope

`TVariable.isEmpty()` selects its active Mind once. With the new flag ON and exact TVariable, Mind and TValueFactory classes, it reads the same public current map with one fresh get and tests for a null value. A present native binding previously used two containsKey probes plus get; an absent binding still uses one map probe. No result/hash/ID/context/binding is cached. Factory.get and factory.isEmpty are unchanged.

This is the smaller variable-emptiness route quantified by [current-empty forensics](https://github.com/murrick/K3/blob/experiment/3.8.0-current-empty-forensics/docs/current-empty-forensics.md), not a repeat of the rejected guarded factory.get experiment. That census found about 7.3% of containsKey calls attributable to the duplicated present-binding probe in this scope. Call counts do not predict CPU gain.

Mapped-null public entries remain empty for TVariable while factory.isEmpty continues to mean absent key. The public map stays the same HashMap; no representation, persistent data or mutation lifecycle is changed. Exact built-in hashCode/equals read private ID fields without callbacks. Subclasses retain the old short-circuit expression and its callback order; a native Mind with a custom factory retains that path too. A null factory retains the old NPE boundary. This does not introduce support for unsynchronized concurrent mutation of the same HashMap or key ID; the sibling-context fixture uses separate maps.

## Qualification

`SingleVariableEmptyRunner`: 24 independently recreated callback scenarios, 163 checks OFF/ON on Java 17.0.20. The oracle copies the original method body and uses its private activeMind selection. It compares outcomes, original sentinel exception identity and full ordered traces. Scenarios cover custom key hash/equals failures on later calls, second/third callback mutation, custom Mind getter failures/null/replacement, custom factory hiding/failure/direct-get semantics, and a custom public-map getter that must not be invoked.

Native boundaries cover no context, missing/present/mapped-null bindings, public map mutation, value/variable deletion, mutable IDs, signed/full-range IDs, root/child switching, actual HashMap tree bins with a custom stored key, null map keys and null factories. Two simultaneous sibling contexts share one variable and make 100 reads each, with subsequent context switches on the main thread.

Local OFF/ON compact-find, resident-base, single-lookup, storage-reopen, latent corpus, 20-operation transactions and three candidate-concurrency iterations pass. Transaction states are byte-identical; stderr files are empty. The focused 163-check runner also passes with all eight prior flags OFF. The dedicated workflow covers Java 8/21/26 × eight integrated flags OFF/ON, testing the new flag OFF/ON in each job, plus cross-context projection, corpus, reopen, transactions and concurrency.

## Measurement protocol

Fresh clean reference compiled from the exact base, with byte-identical SonProfileRunner. Six sequential fresh JVMs: clean / OFF / ON / ON / OFF / clean. Java 17.0.20, ECJ -1.8, 512 MB heap, all eight integrated flags explicitly ON. Six ordinary logged natives.k / son(John,x) samples per JVM; median of the last four after discarding two. Compilation/query/raw rendering are outside separately measured optimizeHypothesis. Wall/main CPU/main allocated bytes are reported without counters or profiling. Full sorted raw/final text and last-Linker statistics must match for every sample.

Code CI checkpoint: `3f74a6c1b659c871a99bbd9f1bde7ef8989e8696`. Documentation-only evidence retains exactly that production, qualification and workflow code. Class comparison finds only TVariable.class different among 651 shared reference/experiment classes; SonProfileRunner bytecode is identical. Class hashes and the complete text oracle are recorded alongside this report.

All five workflows / 20 jobs passed at that code checkpoint: general KANGER CI, qualification isolation, distribution bundle, server, and [six-job dedicated emptiness qualification](https://github.com/murrick/K3/actions/runs/37444146540). Complete statuses and job/step records are in `ci-status.json`.

## Result: no stable timing gain

| Run | Wall median s | Main CPU median s | Main allocated MB |
|---|---:|---:|---:|
| Clean 1 | 4.655 | 4.569 | 4848.8 |
| OFF 2 | 4.657 | 4.561 | 4805.8 |
| ON 3 | 4.555 | 4.460 | 4804.0 |
| ON 4 | 4.861 | 4.759 | 4715.2 |
| OFF 5 | 4.666 | 4.572 | 4804.9 |
| Clean 6 | 4.674 | 4.584 | 4842.1 |

ON is 2.19% faster than OFF forward, but 4.17% slower in reverse; main CPU is 2.21% lower / 4.09% higher. Against clean, wall is 2.16% faster / 4.00% slower. Main allocated bytes are 0.92% / 2.62% lower than clean, but only 0.04% / 1.87% lower than same-build OFF. These are local directional comparisons, not confidence intervals, a universal speedup, or a proven permanent regression. Allocation telemetry measures bytes allocated by the main optimization thread, not retained memory.

All 36 measured samples match the full raw/optimized text oracle: 18 raw, six optimized hypotheses, unknown query result, zero solutions/values. All last-Linker statistics match: passes=6 rules=1292 rotations=6824 pairs=13281 unifications=3080. These are last-Linker records, not complete global work counts.

## Diagnostic path check

Two additional fresh diagnostic JVMs, OFF then ON, use six samples each and main-thread counters only within optimizeHypothesis. Instrumentation consumes the actual map results, preserves callback/short-circuit/context selection order, and does not issue extra semantic probes. Temporary source copies and their generated patch are under `probe-census`; production/qualification/workflow code is unchanged. These instrumented times are excluded from timing evidence.

All twelve diagnostic samples match the full text/statistics oracle. The counts outside TVariable.isEmpty match at every sample index across OFF/ON. Inside the variable-emptiness scope:

| Probes per optimization | OFF | ON |
|---|---:|---:|
| Initial containsKey | 4,911,387 | 0 |
| Factory.get containsKey | 4,910,080 | 0 |
| map.get | 4,910,080 | 4,911,387 |
| map.get returning null | 0 | 1,307 |

Variable emptiness outcomes are unchanged: 1,307 true and 4,910,080 false, with no null active contexts. The new path removes exactly **9,820,160 map probes per optimization** in all six paired diagnostic comparisons. Thus the path is exercised and the expected work reduction occurs; that reduction still does not produce a stable measured CPU/wall improvement. Counting map calls does not measure their cost or JIT-generated machine-code work. Forty-eight total scenario snapshots (36 measured + 12 diagnostic) are verified, with no instrumented times included in the timing table.

## Decision and reproduction

Keep the prototype default OFF and reject integration on this evidence. The targeted path preserves the tested semantics, but the timing effect changes sign; do not infer CPU savings from the earlier call census. No clean/ON confirmation sequence was run because the initial opposite-order comparison already fails to demonstrate a stable gain.

The two confirmation-script templates are retained but unused; there are no confirmation measurements in this checkpoint.

From repository root: `bash docs/single-variable-empty/build-local.sh`, `python docs/single-variable-empty/qualify.py`, `python docs/single-variable-empty/build-reference.py`, `python docs/single-variable-empty/run-benchmark.py`, `python docs/single-variable-empty/analyze.py`. ECJ is external at `../tooling/ecj.jar`; all completed logs and scripts are retained beside this report.

Optional diagnostic reproduction: `python docs/single-variable-empty/probe-census/build.py`, then `run.py` and `analyze.py` from the same directory (invoked from the repository root). Counter classes contain no retained semantic objects or cached bindings; compiled diagnostic hashes and class differences are recorded too.
