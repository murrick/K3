# Guarded current TValue lookup experiment

Base: live `develop/3.8.0` verified at `1828d7164159cb04df00dedd5c27e18d556af4ea`.
Branch: `experiment/3.8.0-guarded-current-lookup`.
Default OFF: `-Dkanger.experiment.guardedCurrentLookup=true` (restart JVM).

## Change and semantic boundary

`TValueFactory.get(TVariable)` currently calls `isEmpty` (HashMap.containsKey)
and then `HashMap.get` for present bindings. The experiment uses one get only
for the exact built-in factory and an exact built-in TVariable or a null key.
No cache, persistent state, key representation or context ownership is changed.
No optimization flags from other experimental branches are included.

The archived `directCurrentLookup` prototype protected factory subclasses only.
A custom TVariable.hashCode counter reproduced two calls OFF versus one ON.
That prototype was never integrated. This experiment also guards key subclasses,
preserving their original hash/equals callbacks, including exceptions and map
mutation on the second callback. Factory subclasses keep overridden isEmpty.

Built-in TVariable.hashCode/equals read private ID fields without virtual calls.
HashMap uses the probe key's equality, not the stored custom key's equality.
TVariable implements Comparable<Object>, not Comparable<TVariable>; tree-bin
lookups must not invoke stored custom getIndex. The boundary runner exercises
actual HashMap TreeNode entries with 80 identical-hash IDs and a stored custom
key whose getIndex throws. IDs remain mutable; no memoized hash/result is added.
The publicly exposed current map remains the same map, including null entries.
Existing unsupported unsynchronized concurrent mutation of that HashMap is not
turned into a new supported behavior.

## Validation

`GuardedCurrentLookupRunner`: 136 checks, OFF and ON on local Java 17.0.20.
Covers missing/present/mapped-null/null keys, swaps, public map mutation,
root/child separation, packing on child release, custom factory hiding/failure,
custom key hash/equals call counts, second-call exceptions and mutation,
collision tree bins, custom stored keys, changed IDs, signed/large IDs.

Local latent corpus and 20-operation transaction runner pass OFF/ON with the
six integrated optimization flags ON; serialized transaction states match.
Candidate concurrency passes in OFF and ON (three iterations each).
Benchmark results will be recorded after completion.

Dedicated CI covers Java 8/21/26 x six integrated flags OFF/ON. Each job runs new
flag OFF/ON, boundaries, corpus, cross-context projection, transactions/state
comparison, then candidate concurrency. CI results pending at code checkpoint.

## Measurement protocol

The CPU forensics checkpoint (`3ce9087`) is a lead, not a speedup estimate:
isEmpty owns roughly 13.4% of first-Kanger-frame attribution in sampled hypothesis
optimization, including the first cold sample; inclusive stack rows overlap.

Diagnostic SonProfileRunner compares full raw and final hypothesis text, result
status and solution/value counts. Four isolated JVMs use ON/OFF/OFF/ON, six
iterations each, discard first two and report medians of the remaining four.
All six integrated flags stay ON, other branch experiments remain absent.
Wall and main-thread CPU times are measured without profiler/verifier overhead.
Main-thread allocation telemetry is diagnostic, not retained heap measurement.
This is a single local workload and JDK, not a universal performance claim.

See `guarded-current-lookup-evidence/qualify.py`, `run-benchmark.py`, `analyze.py`.
No merge, release, default enable or deployment is authorized by this checkpoint.
