# Linker dependency and lifecycle boundary

An execution frontier needs more than the five continuation flags, a TValue-attempt counter, a TSolve version or a method's Boolean return. Native witnesses now show independent changes in current argument stamps, exposed domain maps, speculative canonical state and externally observable operation callbacks. The preceding donor separation remains the production baseline; this branch adds diagnostic evidence only.

Base: `refactor/3.8.0-linker-donor-universe` at `e84d1748fd7d5b963cc8f5acc89ecfc4a23c0f2d`. Production, qualification-module sources and CI workflow are unchanged. The diagnostic rebuild has 654 shared classes byte-for-byte identical to the preceding split build. This does not introduce a journal or skip any rule visits, and it makes no performance claim.

The baseline's complete CI passed: all ten Java 8/21 matrix jobs, including the new donor gate in each job, and the Server, distribution bundle and qualification-isolation workflows. Exact run/job identities are recorded in `prior-ci.json`.

## Native witnesses

| Boundary | Observed result | Consequence for a future scheduler |
| --- | --- | --- |
| Nested TValue checkpoint | Inner commit followed by outer release restores the canonical enumeration to `[base]` and clears action; explicitly selected transient current remains `outer` | Canonical visibility and current projection have different lifecycles; a checkpoint event cannot be treated as a durable publication |
| Nested FValue checkpoint | Both speculative results disappear after outer release; action clears; transient result slot still contains `4.0` | Result-slot writes and surviving FValue creation are different channels |
| Current argument stamp | Same canonical values and same single domain exclusion mark yield exclusion `[true,false,true]` as current changes `a → b → a`; a public current-map alias performs the last switch | Domain identity or canonical value-bucket equality alone does not describe a branch read |
| Exposed exclusion map | Clearing the native exclusion stamp changes the same clause's deferred production from true to false without setting continuation flags or changing TSolve version | Setter-only instrumentation misses supported raw-map observations |
| Boolean return versus effects | The second `linkDatabase` call returns false with no produced domain but adds two provisional hypotheses and raises their action | False is not evidence of a semantic no-op |
| Unresolved dynamic function | Two `calcFunctions` calls invoke the same native operation twice, while no FValue appears and all five action flags/TSolve version remain stable | An empty-result callback may have externally observable effects; generic operation execution cannot be omitted based on inference counters |
| Mutable native Operation | Changing its callback through `setProc` changes the next dispatch with no new canonical result or continuation signal | Callback identity, binding resolution and external state are outside a simple rule/value fingerprint |

These are existing contracts, not newly introduced behavior. In particular, the current/result projection surviving a local canonical checkpoint is not a claim that it survives every composite transaction or query boundary. The native function witness installs a real Library `Operation` with a diagnostic callback and executes it through `Linker.calcFunctions → Calculator`; it does not replace the Linker, factories or semantic matching with mocks. Its counter is a deliberately observable callback effect, not a built-in logical mutation.

All five witness cases contain 49 assertions. Six fresh witness JVMs produce identical complete rows across ON/OFF/VERIFY and repeats. Three modes also pass the existing 31 dependency assertions, 88 donor-scope assertions, native waiter rollback gate and 20-operation nested transaction corpus. All 18 accepted JVMs have empty stderr; complete transaction states agree across modes, and ON/VERIFY work vectors agree. OFF work counters remain diagnostic rather than an equality oracle for ON.

The first probe incorrectly asserted that the entire post-classification state would remain quiet after exclusion removal. Its failure revealed the provisional-hypothesis effect. Original source/stdout/stderr are retained under `failed-attempts`; the corrected witness checks quietness at the alias-mutation boundary and separately asserts the classifier's actual effects. It changes no production behavior.

## Dependency map from source

This is a review map, not a proven exhaustive read set for arbitrary extension code. Transitive hydration, custom Mind subclasses, custom storage and arbitrary operation callbacks require conservative handling.

| Channel and authority | Representative native readers | Existing mutation/lifecycle boundary | Required future observation |
| --- | --- | --- | --- |
| Active execution/donor view | `Linker.link`, `buildDomainIndex`, `selectDomainCandidates` | Query seed, Rule used state, generated additions, visibility; rebuilt each pass in both direction orders | Preserve complete direction-specific donors; admit generated rules at the existing next-pass boundary |
| Canonical TValue visibility/order | `rotateVariables`, unification | `TValueFactory.add`, local mark/commit/release, typed child promotion, deletion/resurrection, pack | Surviving per-variable visibility and ordered enumeration, scoped to Mind/generation |
| Current argument projection | `TVariable.getCurrent/getValue`, Domain stamps, function arguments | `TValueFactory.set/getCurrent` and argument/result setters; not the local canonical checkpoint journal | Track consumed argument stamps separately; never equate current-cursor movement with durable canonical insertion |
| TSolve compatibility | `isValidFor`, indexed tuple candidates | `Mind.addTSolve`, invocation reset, exposed public tuple map/list | Canonical tuple changes and reset boundaries; retain existing append-only alias fallback, without promising arbitrary destructive alias repair |
| Domain status by argument stamp | `linkDomains`, `linkDatabase`, `checkSystem` | Used/excluded/calculated stamps, invocation clears, public mutable Mind maps | Domain ID plus frozen typed argument stamp and status kind; include removals/clears |
| Rule used/visibility state | Active-set expansion, candidate hydration, database lookup | `Rule.setUsed`, `Mind.setUnitDeleted`, deletion/restoration maps, RuleFactory addition | Identity-aware used state and logical visibility transitions, including resurrection without identity change |
| Canonical FValue applicability | Calculator, Function, argument reads | `FValueFactory.add`, invalidation, local checkpoints, function/Library lifecycle | Function identity, binding and argument applicability; separate transient result-slot writes |
| Library/system operations | `Calculator.resolve/execute`, `checkSystem` | Library redefine, mutable Operation fields, system dispatch maps, external state | Full traversal for unqualified callbacks/system operations; deterministic purity must be an explicit contract before pruning |
| Waiter assumptions | `linkDatabase` | DomainFactory expansion, typed promotion, waiter checkpoints, exposed waiter set | Native waiter visibility and rollback; not just canonical Domain cache changes |
| Provenance/solve metadata | Cause weighting, Domain classification, `updateDatabase` | Invocation-local `causes`; per-rule Domain solves/causes clears; Rule cause/solve lists | Relevant donor/receiver argument identities and scratch-to-generated-Rule transfer |
| C-variable lineage | Unification and term comparison | Rule-scoped child creation, parent/child links, pack/clear | Parent Term plus receiver Rule scope; do not replace child hydration with metadata-only identity checks |
| Hypotheses/query/flood context | Classification, query-value and flood guards | Query pass/policy, temporary/final hypothesis sets, query values, flood accounting, invocation clear | Keep phase/policy and the existing continuation contract; temporary hypotheses count as effects even without produced records |

Source anchors: [Linker](../kanger/src/org/kanger/Linker.java), [Mind](../kanger/src/org/kanger/Mind.java), [Domain](../kanger/src/org/kanger/units/Domain.java), [TValueFactory](../kanger/src/org/kanger/factory/TValueFactory.java), [FValueFactory](../kanger/src/org/kanger/factory/FValueFactory.java), [DomainFactory](../kanger/src/org/kanger/factory/DomainFactory.java), [Calculator](../kanger/src/org/kanger/calculator/Calculator.java), [LibraryFactory](../kanger/src/org/kanger/factory/LibraryFactory.java). The native tests exercise the stated boundaries; they do not validate every proposed key or scheduling rule in this table.

## Concrete next implementation boundary

The next change should be an **observer-only journal for surviving canonical TValue changes**, scoped to one Mind and invocation/generation. Keep the complete scheduler in control. Before consumers exist, verify its emitted records against authoritative ordered enumeration, including nested local checkpoints and accepted/rejected child publication.

Provisional additions remain inside nested journal frames. Inner checkpoint commit merges into its outer frame; outer release discards both. A surviving outer commit may publish the canonical delta. Typed child publication must follow Mind's actual settlement outcome; parent observers must not see released child effects. Deletion, resurrection, physical removal and reset need explicit visibility records. Repeated add attempts are not automatically canonical deltas. Current bindings, transient function results and domain marks must not silently be restored or published by this canonical journal.

Visibility records must preserve the existing canonical lookup/resurrection and ordered-enumeration contracts. They must not introduce new filtering of logically deleted values. Consumers remain responsible for the existing semantic treatment of those identities.

Only after observer equivalence should rule read sets consume these records. They must retain all other channels above or use a conservative full traversal. A default-OFF shadow selector may compare its proposed frontier with the full traversal, but a visit with no counter delta cannot be labeled removable. Impure/unresolved operations and untracked mutable aliases remain reasons to retain the full traversal. New rules must retain their existing publication boundary, and donors must retain the complete pass view in each direction.

This intentionally limits the next implementation to one authoritative channel. A TValue-only journal is not a sufficient execution-frontier oracle.

## Reproduction

From the checkout root, with Java 17.0.20 and ECJ 3.33.0 at `../tooling/ecj.jar`:

```sh
python docs/linker-dependency-lifecycle/qualify.py
```

The source-list and previous class-hash oracle are committed with the donor-scope baseline; compilation uses temporary classes at `../build/dependency-lifecycle`. `summary.json`, hashes, complete compressed JVM logs and transaction state/work vectors are under `docs/linker-dependency-lifecycle/`. The previous production commit's Java 8/21 CI status is recorded separately in `prior-ci.json` once checked.
