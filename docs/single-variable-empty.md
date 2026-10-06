# Guarded single variable-emptiness lookup

Base `develop/3.8.0 @ 1c943d02d39bd33ebd91fabb7c4190112ab49459`, verified live 2026-10-06. Branch `experiment/3.8.0-single-variable-empty`. Default OFF: `-Dkanger.experiment.singleVariableEmpty=true` (restart JVM). No merge or default enablement.

## Change and scope

`TVariable.isEmpty()` selects its active Mind once. With the new flag ON and exact TVariable, Mind and TValueFactory classes, it reads the same public current map with one fresh get and tests for a null value. A present native binding previously used two containsKey probes plus get; an absent binding still uses one map probe. No result/hash/ID/context/binding is cached. Factory.get and factory.isEmpty are unchanged.

This is the smaller variable-emptiness route quantified by [current-empty forensics](https://github.com/murrick/K3/blob/experiment/3.8.0-current-empty-forensics/docs/current-empty-forensics.md), not a repeat of the rejected guarded factory.get experiment. That census found about 7.3% of containsKey calls attributable to the duplicated present-binding probe in this scope. Call counts do not predict CPU gain.

Mapped-null public entries remain empty for TVariable while factory.isEmpty continues to mean absent key. The public map stays the same HashMap; no representation, persistent data or mutation lifecycle is changed. Exact built-in hashCode/equals read private ID fields without callbacks. Subclasses retain the old short-circuit expression and its callback order; a native Mind with a custom factory retains that path too. A null factory retains the old NPE boundary.

## Qualification

`SingleVariableEmptyRunner`: 24 independently recreated callback scenarios, 163 checks OFF/ON on Java 17.0.20. The oracle copies the original method body and uses its private activeMind selection. It compares outcomes, original sentinel exception identity and full ordered traces. Scenarios cover custom key hash/equals failures on later calls, second/third callback mutation, custom Mind getter failures/null/replacement, custom factory hiding/failure/direct-get semantics, and a custom public-map getter that must not be invoked.

Native boundaries cover no context, missing/present/mapped-null bindings, public map mutation, value/variable deletion, mutable IDs, signed/full-range IDs, root/child switching, actual HashMap tree bins with a custom stored key, null map keys and null factories. Two simultaneous sibling contexts share one variable and make 100 reads each, with subsequent context switches on the main thread.

Local OFF/ON compact-find, resident-base, single-lookup, storage-reopen, latent corpus, 20-operation transactions and three candidate-concurrency iterations pass. Transaction states are byte-identical; stderr files are empty. The dedicated workflow covers Java 8/21/26 × eight integrated flags OFF/ON, testing the new flag OFF/ON in each job, plus cross-context projection, corpus, reopen, transactions and concurrency.

## Measurement protocol

Fresh clean reference compiled from the exact base, with byte-identical SonProfileRunner. Six sequential fresh JVMs: clean / OFF / ON / ON / OFF / clean. Java 17.0.20, ECJ -1.8, 512 MB heap, all eight integrated flags explicitly ON. Six ordinary logged natives.k / son(John,x) samples per JVM; median of the last four after discarding two. Compilation/query/raw rendering are outside separately measured optimizeHypothesis. Wall/main CPU/main allocated bytes are reported without counters or profiling. Full sorted raw/final text and last-Linker statistics must match for every sample.

Timing and remote CI are pending at this code checkpoint. Scripts, raw logs, class/source verification and final decision will be added as documentation-only evidence. Single-workload local measurements will not establish a universal speedup.
