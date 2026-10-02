# CPU forensics after six integrated optimizations

Fresh live develop/3.8.0 verified at 1828d7164159cb04df00dedd5c27e18d556af4ea. Separate experiment/3.8.0-cpu-forensics. No production changes. Java17 Xmx512m, six integrated flags ON, all newer experimental paths absent. Six son(John,x) hypothesis optimizations; snapshots agree across all six runs (18 raw/six final, zero solutions/values), stderr empty. Profiled times are not baseline benchmarks.

JFR profile settings, stack depth256. Parser selects jdk.ExecutionSample records on main whose stack contains Mind.optimizeHypothesis. Of 2715 execution samples, 2654 meet this filter. Startup/compilation and original query samples are excluded when that frame is absent. First cold optimization is included; do not label this warmed steady state. Execution sampling is statistical and safepoint/JIT sensitive, not exact accounting or a wall-time percentage. Inclusive rows overlap and must not be summed. Raw JFR stays in temporary ../build; parser, invocation, histogram and text results are preserved here.

| Attribution | Samples | Share of selected samples |
|---|---:|---:|
| JVM leaf HashMap.getNode | 685 | 25.8% |
| First KANGER TValueFactory.isEmpty:337 | 355 | 13.4% |
| First KANGER Predicate.getName:136 | 193 | 7.3% |
| First KANGER TVariable.activeMind:117 | 173 | 6.5% |
| First KANGER Escalera.find:427 | 119 | 4.5% |

The map leaf and first-KANGER rows describe different overlapping attribution schemes. TValueFactory.get appears inclusively in398 samples, isEmpty in355. Source confirms get invokes isEmpty (containsKey) before current.get. This motivates revisiting the archived directCurrentLookup experiment; its previous elapsed results were inconclusive under strong environment drift. Name/snapshot experiments already assess the corresponding allocation sites; do not duplicate them. activeMind uses a thread-confined weak context projection, so replacing it with a shared/default Mind is not safe.

## New semantic boundary in archived directCurrentLookup

The archived K3-binding38 prototype guarded only exact TValueFactory class. Probe compiled against its archived classes inserts a TVariable subclass overriding hashCode into the exposed current map. Both modes return the same TValue, but reference OFF invokes hashCode twice while ON invokes it once (callback-off/on.log). This is an observable callback difference, so the old prototype is not a semantic-equivalent path for custom keys. It was not integrated; develop is unaffected. Its prior22 boundary checks did not cover this key callback case.

Next candidate must guard exact factory AND built-in variable keys (with explicit null handling) and retain the original path for subclasses. Built-in TVariable hashCode/equals read ID fields directly; public setId means completed hash/binding results must not be cached. Additional validation must cover custom hash/equals callback counts and failures, collision/tree bins, current-map mutation, null/mapped-null, child contexts, transaction snapshots and concurrency. No optimizer code changed in this checkpoint and no merge/default enable performed.
