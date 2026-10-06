# Current-binding emptiness call census

Base: `develop/3.8.0 @ 1c943d02d39bd33ebd91fabb7c4190112ab49459`, verified live 2026-10-06. Branch: `experiment/3.8.0-current-empty-forensics`. Evidence only: production sources, qualification sources and workflows are unchanged. No runtime flag, integration or default enablement is introduced.

## Finding

The warm JFR lead attributes 3.80–4.63% of first-Kanger-frame samples to `TValueFactory.isEmpty:337`. This is `isEmpty(TVariable)`, the current-binding `HashMap.containsKey` overload, rather than the no-argument cache-emptiness method. Stack attribution is not a removable CPU-time estimate.

The old [guarded factory-get experiment](https://github.com/murrick/K3/blob/experiment/3.8.0-guarded-current-lookup/docs/guarded-current-lookup.md) already removed the containsKey-before-get probe for built-in keys/factories. Its paired wall results changed sign; its profile lost the expected isEmpty frames without demonstrating a useful speedup. Do not repeat that mechanism solely because the newer JFR still names isEmpty.

A separate route is `TVariable.isEmpty()`: after selecting activeMind once, it checks factory.isEmpty and, when present, calls factory.get, which checks factory.isEmpty again and then reads the value. This census distinguishes those two checks from factory.get calls outside TVariable.isEmpty.

Representative post-warmup sample, identical in both diagnostic JVMs:

| Route | containsKey calls | Absent | Present | HashMap.get calls |
|---|---:|---:|---:|---:|
| Factory.get outside TVariable.isEmpty | 57,603,349 | 220,977 | 57,382,372 | 57,382,372 |
| TVariable.isEmpty initial check | 4,911,387 | 1,307 | 4,910,080 | 0 |
| Factory.get inside TVariable.isEmpty | 4,910,080 | 0 | 4,910,080 | 4,910,080 |
| Other direct factory.isEmpty calls | 0 | 0 | 0 | 0 |

The eight post-first-two diagnostic samples contain 67,424,816–67,574,106 containsKey calls and 62,292,452–62,441,742 map.get calls per optimization. The counts outside TVariable.isEmpty vary with sample index; **every corresponding index matches exactly between the two fresh JVMs**. TVariable.isEmpty counts are identical across all twelve samples. No assumption of a stationary warm total is made.

The additional present-binding containsKey inside TVariable.isEmpty is 4,910,080 calls, or 7.266–7.282% of containsKey calls. A hypothetical fresh single-map-probe variable-emptiness path could remove two probes per present call: 9,820,160, or 7.553–7.570% of the observed combined containsKey/get calls. These are call-count arithmetic, **not CPU shares, speedup predictions, timing bounds, or evidence that a candidate is safe**. Removing a single probe in the old factory-get experiment did not establish a speedup.

All observed keys/factories are exact built-in classes; variable-emptiness scopes also use exact built-in Mind and TVariable. All active contexts are non-null. No observed map.get returns null, though 1,307 variable calls short-circuit on a missing binding. These workload observations do not narrow the public contract.

## Semantic boundary for any future candidate

`TValueFactory.isEmpty(tv)` tests key presence, while `TVariable.isEmpty()` tests usable current-value presence. `TValueFactory.getCurrent()` exposes the mutable map; a mapped-null entry is legal at that boundary. Keep those meanings distinct. Preserve fresh context selection and mutable IDs; no hash, result or binding may be memoized across calls.

Overridden Mind.getTValues, factory.isEmpty/get and TVariable hashCode/equals can change state or throw on later callbacks. A future path must retain the original callback sequence for subclasses. The prior guarded-current runner supplies mapped-null, custom callbacks/exception identity, factory hiding, mutable IDs, actual tree-bin collisions and custom stored-key witnesses; new scope would still need its own qualification against the current eight-default base. Exact classes in this one corpus are not a replacement for such checks.

## Method and verification

Java 17.0.20, ECJ `-1.8`, 512 MB heap; all eight integrated flags explicitly ON. One fresh clean JVM and two fresh diagnostic JVMs, six ordinary logged natives.k / son(John,x) samples each. Counters are enabled on the optimization thread only between entry/exit of optimizeHypothesis. Query, compilation and raw/final text rendering are outside the counter window. Instrumented timings printed by the existing driver are excluded from performance evidence.

`build.py` generates temporary instrumented copies of TValueFactory and TVariable, plus a diagnostic driver. It preserves the original lookup outcomes, short circuits, activeMind reads, virtual getTValues calls and get/isEmpty order. The counters consume actual results rather than issue extra map probes; getClass checks do not invoke user callbacks. Thread ownership is restricted to the active window, depth scopes restore in finally, and no semantic objects are retained or cached. This deliberately adds counting/branch overhead and is not a production prototype.

Clean and diagnostic class comparison finds only TValueFactory.class and TVariable.class different among shared classes. Generated sources/patch, compiled diagnostic hashes, explicit source list, scripts and compressed completed logs are retained beside this report. Logs are compressed after JVM exit.

All 18 samples match the full independently retained expected raw and optimized text sets: 18 raw hypotheses, six optimized, unknown query result, zero solutions and values. All last-Linker statistics match: passes=6, rules=1292, rotations=6824, pairs=13281, unifications=3080. These are last-Linker statistics, not global work counts. Twelve diagnostic samples satisfy scope/row arithmetic invariants; both JVMs match at every sample index. There is no production change requiring a new compatibility CI matrix.

## Decision and reproduction

Do not infer a speedup or revive guarded factory.get from this profile lead. The smaller variable-emptiness route is now quantified; a new candidate would have to isolate that scope and demonstrate benefit against a clean reference before integration can be considered. This checkpoint changes no production behavior.

From the repository root: `python docs/current-empty-forensics/build.py`, `python docs/current-empty-forensics/run.py`, `python docs/current-empty-forensics/analyze.py`. ECJ is external at `../tooling/ecj.jar`. Full count observations and their scope are in `summary.json`; text oracles are in `expected.json`.
