# Deletion caller attribution and exposure guard coverage

## Decision

The second deletion expression in variable enumeration accounts for **43.25% of completed deletion checks** in this fixture. However, an unconditional one-check collector is semantically different, and an exact-Argument/TVariable/Mind class guard alone is insufficient. A conservative guard requiring no observed map exposure anywhere in the visited chain has **zero covered calls** here.

Do not implement or time that guard as a presumed optimization. It would fall back on every observed call. The next distinct design question is whether trusted internal accesses can avoid publicly exposing mutable state, with authoritative mutation tracking and fallback after actual public exposure. That would need a separately qualified ownership and invalidation design; this checkpoint does not introduce one or claim a speedup.

Baseline: verified live `develop/3.8.0 @ 1c943d02d39bd33ebd91fabb7c4190112ab49459`. Eight integrated optimizations ON; other default-OFF experiments absent. Production, qualification and workflow files remain byte-identical to baseline.

## Method

Two fresh sequential Java 17 JVMs, 512 MB heap, six ordinary son-query samples per JVM; discard the first two samples. Deletion and argument counters cover only main-thread `optimizeHypothesis`, separately from the original query. Full logs are retained in gzip form. All instrumented timings are diagnostic overhead, not performance results.

Four baseline sources are restored into a temporary build directory and instrumented:

- `Domain.setMind`: scope around the original virtual `arguments.getTVariables` invocation, classified BINDING.
- `ArgumentsList.getStamp`: scope around its original virtual `getTVariables` invocation, classified STAMP. Other callers remain OTHER. Nested collection work inherits its enclosing tagged scope.
- `ArgumentsList.getTVariables`: scoped wrappers around each of the original first/second `Argument.isDeleted` expressions; both calls still execute with original short circuit and exceptions. OUTSIDE means no tagged deletion expression.
- `Argument.isDeleted`: observe the inherited method body's receiver class, held object's class, and initial Mind class, without extra semantic getters or hydration. Overrides that do not call the inherited body are outside these edge counts.
- `Mind.isUnitDeleted`: same original unit getter reads, monitor entries, map lookups, membership callbacks and ancestor traversal. Count outcomes, visits and depths by role/phase/type.

Map exposure tracking starts before User/Mind construction and compilation, and persists across query samples. Original `getDeleted`/`getRestored` getter invocations set a diagnostic boolean on that Mind. No extra getter is invoked, and no returned map/set is copied, wrapped or restricted. Tracking covers the main tracing thread only and counts internal getter use as exposure too; it does not prove that external user code actually retains a map.

The qualified observer keeps the boolean on each temporary Mind and retains **no Mind registry or owner references**. An earlier exposure preflight used a strong identity registry; its recordings were replaced because extending retired Mind lifetimes could distort weak-context behavior. Final findings use only the non-retaining observer. Getter-count and distinct-exposed-Mind totals are lifetime observations, not optimization-only counts.

## Results

All eight warm deletion groups, argument class counts and per-group exposure coverage agree exactly. Their totals reconcile with the preceding independent chain-depth diagnostic: **17,573,710 calls / 46,897,159 layer visits** per optimization. All requested-type sets are absent and all deletion results are false in the measured fixture.

| Attribution | Deletion calls | Layer visits | Share of all calls |
| --- | ---: | ---: | ---: |
| Binding, first expression | 3,875,237 | 10,359,542 | 22.05% |
| Binding, second expression | 3,875,237 | 10,359,542 | 22.05% |
| Stamp, first expression | 3,411,310 | 9,120,373 | 19.41% |
| Stamp, second expression | 3,411,310 | 9,120,373 | 19.41% |
| Other collection, first expression | 314,604 | 834,202 | 1.79% |
| Other collection, second expression | 314,604 | 834,202 | 1.79% |
| Outside tagged collection expressions | 2,371,408 | 6,268,925 | 13.49% |

Every FIRST/SECOND Mind check has type TVARIABLE. The second expression totals **7,601,151 calls / 20,314,117 layer visits**, 43.25% / 43.32% of the totals. This is a count budget, not exact CPU cost or safely removable work. No stack walking or inferred CPU attribution is used for this table.

All 15,202,302 FIRST/SECOND inherited Argument-body observations have exact built-in Argument, held TVariable, and initial Mind classes. That says nothing about ancestor classes, map/set aliases, callback purity or concurrent mutations. Another 23 inherited Argument-body observations occur outside the tagged expressions and hold other object types.

Tagged BINDING collector entries range from 4,888,581 to 4,893,872 per warm optimization. STAMP collector entries are 1,706,357. Zero OTHER entries means there is no wrapper counting those entries; it does **not** mean other collectors are absent. Binding entry counts vary because empty enumeration work can change without changing the measured deletion checks.

## Exposure guard

Every completed deletion call visits at least one Mind whose deletion/restoration getter had already been invoked on the tracing thread. Therefore the hypothetical **whole-chain never-exposed guard covers zero of 17,573,710 calls**, including zero FIRST/SECOND calls. Some individual layers have no observed exposure, but none of the complete traversals consist solely of those layers.

This is coverage for one conservative proposed restriction, not proof that all future guarded designs are impossible. Conversely, a layer with no observed getter exposure is not proven immutable: ordinary internal mutation, other-thread access, reflection and ancestry changes are outside that certificate. No result cache, lock elision, or version maintenance is implemented.

## Class guards are insufficient

`ExposedSetDeletionWitness` uses **exact** Argument, TVariable, ArgumentsList and Mind classes, then installs a custom HashSet through `getDeleted`. The first membership callback returns false; the second either returns true or throws the original exception. The reference collector excludes the variable or propagates that exception. A hypothetical one-check collector includes the variable and bypasses the second callback. Both scenarios pass **11 assertions**, including the three class checks and exception identity.

The earlier custom-TVariable witness is rerun: two scenarios / eight assertions. The authoritative public-map visibility witness is also rerun: 27 checks for direct mutation, ancestor deletion, nearer restoration, empty/null sets and deletion API operations. These are witnesses against shortcuts, not qualification of an implemented optimizer.

## Validation

All 12 complete RAW/OPTIMIZED hypothesis texts match the clean oracle: 18 raw / six final hypotheses, unknown logical result, zero solutions and values. Last Linker statistics match in every sample: passes 6, rule visits 1,292, rotations 6,824, domain pairs 13,281, unifications 3,080. All profile and witness stderr logs are empty.

The analyzer reconciles every type's caller groups with the previous depth histogram and outcomes, validates FIRST/SECOND argument-class counts, and verifies eight identical warm groups. The final build is checked against the profiled class files. No production flag, merge, new cross-JDK CI matrix, or timing improvement is claimed for this docs-only diagnostic branch.

## Reproduce

From the repository root, with Java and `../tooling/ecj.jar` available:

```bash
python docs/deletion-caller-forensics/build.py
python docs/deletion-caller-forensics/run.py
python docs/deletion-caller-forensics/analyze.py
git diff --exit-code 1c943d02d39bd33ebd91fabb7c4190112ab49459 -- kanger kanger-qualification .github
```

The evidence directory contains the observer and derived runner, three witnesses, instrumentation patch, source/build/run manifests, full logs, expected hypothesis oracle, preceding per-type totals, analyzer, and JSON/text summaries.
