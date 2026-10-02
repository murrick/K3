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
All 24 benchmark iterations have identical raw/final hypothesis texts, unknown
logical result and zero solution/value counts. Six additional profiled iterations
also match those texts.

Dedicated CI covers Java 8/21/26 x six integrated flags OFF/ON. Each job runs new
flag OFF/ON, boundaries, corpus, cross-context projection, transactions/state
comparison, then candidate concurrency. Code checkpoint: `ba2d9c72e0cd07b5ae7a93bed27aa1991f69e849`.
CI run: https://github.com/murrick/K3/actions/runs/36981352576.
All six dedicated Java 8/21/26 x prior OFF/ON jobs passed.
General server, distribution and qualification-isolation workflows passed.
General KANGER CI regression matrix also completed successfully. All five
triggered workflows passed at the code checkpoint (see ci-status.json).

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

## Result: no demonstrated speedup

| JVM/order | Flag | Wall median s | Main CPU median s | Allocated MB |
| --- | --- | ---: | ---: | ---: |
| 1 | ON | 5.216 | 5.107 | 5392.2 |
| 2 | OFF | 4.984 | 4.923 | 5345.0 |
| 3 | OFF | 5.104 | 4.974 | 5393.3 |
| 4 | ON | 5.044 | 4.953 | 5384.5 |

ON/OFF pair: wall -4.66%, CPU -3.74%, allocation -0.88% reduction.
OFF/ON pair: wall +1.17%, CPU +0.43%, allocation +0.16% reduction.
These are directional pair differences, not confidence bounds. The effect
changes sign and does not demonstrate a useful elapsed/CPU/allocation gain.
No merge/default enable recommendation for this prototype.

### Profile attribution check

ON uses the same six integrated flags, six iterations, Java 17.0.20, 512 MB heap
and execution-sample JFR settings as the prior OFF forensics checkpoint.
Both recordings include cold and warmer optimization iterations; profile timing
is excluded from the benchmark comparison. Compare stack samples, not call counts.

| Execution samples within optimizeHypothesis | OFF | ON |
| --- | ---: | ---: |
| Total | 2654 | 2658 |
| Includes TValueFactory.get(TVariable) | 368 | 349 |
| Includes TValueFactory.isEmpty | 355 | 18 |
| Includes both get and isEmpty | 322 | 0 |

The caller reader distinguishes method descriptors: get(long) is excluded from
the get(TVariable) row (method-name-only totals were 398/390).
In ON the remaining sampled isEmpty calls are from TVariable.isEmpty. The
expected redundant path disappears from recorded stacks, but inclusive get
attribution stays similar. Thus the prior 13.4% isEmpty attribution is not an
estimate of removable CPU time. JIT/inlining attribution and sampled stack
placement can change; this observation does not explain the timing result by
itself. No claim that samples measure invocation counts or exact CPU percentages.

Readers and profile logs are persisted here; raw JFR recordings are scratch
artifacts reproducible using run-profile.sh (ON) and the CPU forensics branch
(OFF). ReadLookupCallers runs against both files. No production instrumentation
or retained-heap estimate is introduced.
