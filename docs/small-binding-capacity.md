# Small internal variable collector for Domain binding

Base: `develop/3.8.0 @ 1c943d02d39bd33ebd91fabb7c4190112ab49459`, live verified 2026-10-05. Branch `experiment/3.8.0-small-binding-capacity`. New flag `kanger.experiment.smallBindingCapacity` defaults **false**. No develop merge or default enable.

## Why a distinct experiment

The prior lazy-binding experiment removed empty collector creation but retained ordinary ArrayList growth for nonempty lists. It demonstrated no useful gain. The subsequent warm profile identified backing-array growth as an allocation lead, without measuring lengths attributable specifically to Domain binding.

A diagnostic wrapper around only Domain.setMind's original virtual getTVariables call now counts the returned length for exact built-in ArgumentsList/ArrayList objects. It leaves enumeration, binding, deletion checks and callbacks intact. Only the main thread during optimizeHypothesis is counted. Subclass/custom results are classified without calling their size. Exceptions retain identity. Counters reconcile calls = lengths + custom + failures + overflow.

Two fresh Java 17 JVMs, six samples each, discard first two for warm counters. All eight warm samples contain exactly 557,195 lists of length one and 1,653,302 lists of length two. Empty counts vary from 2,678,084 to 2,683,375; total calls 4,888,581–4,893,872. No longer list, custom result, enumeration failure or histogram overflow occurs. Thus every observed nonempty outer binding collector has one or two entries. These are workload-specific observations, not a general bound on permitted lists. All 12 complete RAW/OPTIMIZED texts and last Linker statistics match the clean baseline. Instrumented elapsed values are excluded from performance conclusions.

## Prototype and semantic boundary

Only ArgumentsList and Domain production sources change. Public getTVariables remains unchanged. Exact built-in ArgumentsList may use a separate internal owned ArrayList initialized with capacity zero; before the first insertion it ensures capacity two. Empty collectors still exist as list objects and have no allocated element array. Longer inputs use ordinary ArrayList growth. This prototype does **not** repeat the lazy/null-sentinel empty-list change.

All type/deletion/object reads, duplicate equality checks, nested public function enumeration and iterator callbacks retain reference order. The second getObject completes before capacity reservation and insertion. Domain still binds only after full enumeration. Null arguments and custom outer list subclasses use the original expression, including null-result failures; nested custom lists retain virtual enumeration. No cache across calls, skipped Rule hydration, early binding, changed map ownership or persistence format is introduced. Array capacity of this internal nonescaping collector differs; public snapshot ownership and implementation remain unchanged.

Assuming four-byte compressed references, a 16-byte array header and eight-byte alignment, a 10-slot backing array takes 56 bytes and a two-slot array 24 bytes. For the observed 2,210,497 nonempty collectors the nominal difference is 70,735,904 bytes per warm optimization. This is an object-layout estimate, not an exact JIT allocation saving; empty list objects and all other allocations remain. End-to-end measurements below decide whether it matters.

## Qualification

Local Java 17 compiles at Java 8 target. SmallBindingCapacityRunner compares fresh independent fixtures against the unchanged public-enumeration/original-binding oracle: 23 scenarios / 60 checks per OFF/ON mode. The inherited 22 callback/exception/ownership/context cases are extended with 32 distinct variables and a duplicate, exercising capacity growth beyond two while retaining ordering. Both full callback traces and outcome match, including original exception identity.

OFF/ON also pass compact snapshots (23 checks), resident comparisons (48, custom reads 3), single lookup (15), DB reopen, latent corpus, 20-operation transaction and candidate concurrency (three iterations). Serialized transaction states are byte-identical. The separately built clean baseline and prototype use byte-identical SonProfileRunner code. Dedicated CI covers Java 8/21/26 × prior eight flags OFF/ON, testing this flag OFF/ON, cross-context projections, transactions and concurrency.

## Measurement protocol and decision

Clean → OFF → ON → ON → OFF → clean; six samples per fresh sequential JVM, exclude first two from medians. Java 17.0.20, 512 MiB heap, all eight integrated flags explicitly ON. No profiler/counter/shadow paths in these runs. Compare wall time, main-thread CPU and cumulative allocated bytes; these are not retained heap or a universal speedup.

Warm medians of the final four samples:

| Run | Wall seconds | Main CPU seconds | Allocated MB |
|---|---:|---:|---:|
| Clean, forward | 5.069 | 4.966 | 4,803.6 |
| OFF, forward | 4.550 | 4.456 | 4,767.3 |
| ON, forward | 4.901 | 4.800 | 4,731.4 |
| ON, reverse | 4.797 | 4.708 | 4,736.4 |
| OFF, reverse | 4.755 | 4.639 | 4,806.9 |
| Clean, reverse | 4.933 | 4.833 | 4,815.0 |

ON reduces allocation versus clean by 1.50% and 1.63% (roughly 72–79 MB); versus OFF by 0.75% and 1.47%. This is a small consistent allocation reduction in these pairs. It does not measure retained heap or all workloads.

ON wall time is 3.31% and 2.76% faster than clean, but 7.71% and 0.89% **slower than OFF**; CPU comparisons show the same direction. OFF itself is 10.23% and 3.61% faster than clean. Thus the clean/ON difference cannot establish that the capacity change produced a useful timing benefit: control variation and code-shape effects remain unresolved. The measurements do not establish a specific JIT cause. All 36 complete RAW/OPTIMIZED texts, logical results and last Linker statistics match the clean oracle, with empty stderr.

**Keep as a qualified small allocation improvement with inconclusive timing; do not integrate or enable by default.** The measured allocation saving alone does not justify another production enumeration path at this checkpoint. No extra timing batch or manual laptop test is recommended without a stronger hypothesis. No further semantic callback/binding skips are authorized by this result.

All five workflows passed on the code commit: general KANGER CI, distribution bundle, server, qualification isolation and the dedicated Java 8/21/26 matrix with all prior eight flags OFF/ON and this flag OFF/ON. Final statuses and run URLs are retained in small-binding-capacity/ci-status.json. The final evidence commit changes only documentation/logs.

## Reproduce

With Java and external ECJ at `../tooling/ecj.jar`:

```sh
python docs/small-binding-capacity/build-count.py
python docs/small-binding-capacity/count.py
python docs/small-binding-capacity/analyze-sizes.py
bash docs/small-binding-capacity/build-local.sh
python docs/small-binding-capacity/qualify.py
python docs/small-binding-capacity/build-reference.py
python docs/small-binding-capacity/run-benchmark.py
python docs/small-binding-capacity/analyze.py
```

Counter builds restore both changed production files from the baseline before applying the diagnostic Domain wrapper. Temporary sources/classes are under `../build`; helper, exact source patch, scripts, complete logs, transaction states, histogram and manifest are retained here. Code CI executes commit `2308a5b861d6665274e0342397a7a6dac1ec96fc`; later evidence-only changes do not modify tested sources or workflows.
