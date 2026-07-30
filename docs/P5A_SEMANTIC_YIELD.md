# P5a — Semantic Yield Experiment

Target line: KANGER III 3.4.1

Baseline: `snapshot/3.3.5-p4-linear-baseline`

## Hypothesis

Execution cost alone is not sufficient for planning. A planner should also estimate how much new proof state an operation can produce.

For an observed query run, retain the original coarse external delta:

```
knowledgeDelta = newTValues
               + positive rule count delta
               + positive solution count delta
               + positive result-row delta
```

and introduce a proof-internal effect inventory:

```
effectDelta = newTValues
            + unique new Causes
            + unique new TSolves
            + positive rule count delta
```

When Linker executed at least one unification:

```
proofYield  = knowledgeDelta / executedUnifications
effectYield = effectDelta    / executedUnifications
```

When `executedUnifications == 0`, both yields are reported as zero by convention and the externally visible semantic result is recorded separately as:

```
directDelta = knowledgeDelta
```

A positive `directDelta` identifies a semantic result produced outside the measured Linker-unification path. It must not be interpreted as zero semantic value.

## Isolation invariant

Every measured operation runs in an independent `Mind` populated from the same durable `value/3` fixture.

This is required because `SolutionsStore` and `ValuesStore` retain materialized query results. Sequentially evaluating several query shapes in one `Mind` causes an earlier query to suppress deltas in later queries through deduplication. Such a sequence is useful for cache/materialization studies, but it is not a valid independent semantic-yield baseline.

## Effect telemetry

`SemanticEffectTelemetry` is an opt-in `ThreadLocal` observation session started by the experiment runner immediately before a query and removed immediately afterwards.

The hooks are inactive during normal execution. They do not participate in inference, transactions, persistence, identity, equality, or execution ordering.

The current hooks observe:

```
NEW_CAUSE
NEW_TSOLVE
```

`Cause` instances are deduplicated by their existing semantic equality. `TSolve` construction is used both for lookup and insertion, so tuples are deduplicated by their sorted TValue IDs.

`NEW_TVALUE` continues to come from the existing direct Linker counter. Until a direct generated-rule hook is introduced, `rule_delta` remains an external observation.

## Runner

`org.kanger.KangerSemanticYieldRunner`

Default sizes:

```
100,500,1000
```

Override with:

```
-Dkanger.semantic.yield.sizes=100,500,1000
```

The runner evaluates exact, two-constant, one-constant, and all-variable `value/3` queries.

## Output

```
size
operation
result_rows
passes
executed_operations
new_tvalues
new_causes
new_tsolves
rule_delta
solution_delta
value_row_delta
materialization_delta
knowledge_delta
effect_delta
direct_delta
proof_yield
effect_yield
```

`result_rows` is the positive value-row delta of the isolated operation, not the cumulative size of the result store.

## Verified value/3 invariant

For every inferred `value/3` workload in the 100/500/1000 baseline:

```
newCauses            = resultRows
newTSolves           = resultRows
materializationDelta = 2 * resultRows
knowledgeDelta       = effectDelta
proofYield           = effectYield
```

The correspondence is now enforced by CI rather than recorded only as an observation.

This establishes that, for the current `value/3` query family, the two externally materialized result objects mirror two proof-internal effects: one Cause and one TSolve per result row. It does not yet establish the same equivalence for generated rules, hypotheses, calculated domains, or other predicate shapes.

An exact lookup remains a separate direct path:

```
executedOperations = 0
effectDelta        = 0
directDelta        = 1
```

## Interpretation boundary

`solution_delta` and `value_row_delta` describe externally materialized query state. They remain in the coarse `knowledgeDelta` for historical comparison.

`new_causes` and `new_tsolves` describe proof-internal objects created below the public query boundary. Their aggregate `effectDelta` is a better candidate for Planner semantics, but it is still query-level rather than attributed to a particular unification attempt.

## Safety boundary

The experiment is observational only. It does not modify candidate selection, pass ordering, unification, materialization, storage, transactions, or lifecycle ownership.

## Next layer

The remaining P5a effect mask is:

```
NEW_TVALUE
NEW_GENERATED_RULE
NEW_CAUSE
NEW_SOLVE
USED_ONLY
NO_OBSERVABLE_EFFECT
```

Next, introduce direct generated-rule observation and then place an operation-local effect scope around each Linker unification. Deferred effects must remain separate until they can be reliably attributed to the operation that caused them.
