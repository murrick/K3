# Variable enumeration caller forensics

Live base `develop/3.8.0 @ 1c943d02d39bd33ebd91fabb7c4190112ab49459`, checked 2026-10-04. Branch `experiment/3.8.0-variable-traversal-forensics`. Production remains unchanged; all eight integrated optimizations use ON defaults. The weak-Mind reuse experiment is absent.

## Scope

Follow-up to deletion membership forensics: attribute the 17,573,710 Mind.isUnitDeleted calls in a son(John,x) hypothesis optimization to variable enumeration callers. A diagnostic copy instruments all 18 getTVariables callsites in the production source list, plus argument visits and Mind.isUnitDeleted entries. Callsites are recorded with their base line numbers in callsites.json. Recursive function enumeration inherits the outer route; this workload made no recursive enumeration calls.

Counters select only the main thread, from immediately before optimizeHypothesis until its return. Query, compilation and text rendering are excluded. An IdentityHashMap tracks distinct ArgumentsList objects by reference, avoiding user equals/hashCode. The instrumented helper retains those lists until phase completion, so this build is neither a timing control nor a concurrency/GC equivalence test. Result size is inspected only for exact ArrayList results. The original getTVariables implementation and its deletion/getObject ordering remain unchanged; diagnostic routing still invokes it virtually. Domain stamp counters count method entry and comparison separately.

Two fresh sequential JVMs, six samples each; exclude the first two per JVM from warm summary. All 12 full raw/optimized texts match the earlier clean-base reference (18 raw, six optimized, no solutions/values). Stderr is empty. Every sample reconciles attributed + outside deletion calls exactly to 17,573,710. Warm deletion attribution and distinct list counts are stable; empty Domain.setMind enumeration counts vary slightly, so report ranges. No causal explanation for that variation is established.

## Warm result per optimization

| Caller | Enumeration calls | Distinct lists | Deletion checks | Share of all deletion checks |
|---|---:|---:|---:|---:|
| Domain.setMind | 4,888,581–4,893,872 | 2,257 | 7,750,474 | 44.10% |
| ArgumentsList.getStamp | 1,706,357 | 80 | 6,822,620 | 38.82% |
| Linker rotation collection (1333) | 73,239 | 18 | 292,300 | 1.66% |
| Linker solve collection (587) | 202,938 | 2,254 | 219,084 | 1.25% |
| Domain.isQuery | 47,958 | 2,140 | 69,950 | 0.40% |
| ArgumentsList.applyStamp | 11,955 | 63 | 47,604 | 0.27% |
| Outside getTVariables | — | — | 2,371,408 | 13.49% |

Three other observed factory routes account for 90 deletion checks each; full figures are in summary.txt. Across observed routes, returned variable lists contain at most two variables. Domain.setMind returns an empty variable list about 2.68 million times; getStamp enumeration is empty 642 times. Distinct ArgumentsList identity does not establish stable contents, stable selected Mind or stable values.

The two main routes account for 82.9256% of deletion calls, so they are useful priorities. Counts are not CPU shares and do not prove those percentages are removable.

## Stamp loop observation

| Method | Entries | equalsStamp comparisons | Comparisons / entry |
|---|---:|---:|---:|
| Domain.isCalculated | 2,220,870 | 1,665,824 | 0.750077 |
| Domain.isProduced | 13,659 | 25,378 | 1.857969 |

These aggregate counters do not establish the histogram or maximum comparisons in one invocation. isCalculated is called frequently; there is no evidence here for a uniformly large per-call comparison multiplier. getStamp is rebuilt for every equalsStamp comparison and for direct insertion into the produced/calculated records.

## Boundary and next candidate

Caching getTVariables or hoisting one current stamp across a candidate loop is not qualified by these counts. ArgumentsList is mutable, Argument.setObject/clear can change type/object, deletion maps are public mutable views, and parent or value/list callbacks may change later reads. The previously demonstrated duplicate-deletion witnesses still apply. No checks or callbacks may simply be omitted because this particular workload had empty deletion maps.

A smaller next experiment is a compact temporary current-stamp representation inside exact built-in equalsStamp, preserving full variable enumeration and all value reads before comparisons, callback order, exceptions and custom getStamp fallback. It targets the repeated temporary allocations without caching state across comparisons. It has not been implemented or timed here, and the counter result does not promise a speedup. Domain.setMind enumeration reduction remains an architectural question requiring a proved observation/invalidation boundary.

## Reproduce

From repository root, with external ECJ jar and Java 17:

```sh
python docs/variable-traversal-forensics/instrument.py
python docs/variable-traversal-forensics/run.py
python docs/variable-traversal-forensics/analyze.py
```

ECJ targets Java 8. Modified copies and classes are written only under ../build/variable-traversal-source and ../build/variable-traversal-classes. Full diagnostic patch, helper/runner, scripts, callsites, schema and logs are alongside the report. Instrumented elapsed times are excluded. No runtime flag or production change is introduced, so general regression CI is not requested for this diagnostic-only checkpoint.
