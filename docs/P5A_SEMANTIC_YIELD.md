# P5a — Semantic Yield Experiment

Target line: KANGER III 3.4.1

Baseline: `snapshot/3.3.5-p4-linear-baseline`

## Hypothesis

Execution cost alone is not sufficient for planning. A planner should also estimate how much new proof state an operation can produce.

For an observed query run, define a conservative external semantic delta:

```
knowledgeDelta = newTValues
               + positive rule count delta
               + positive solution count delta
               + positive result-row delta
```

and, when Linker executed at least one unification:

```
proofYield = knowledgeDelta / executedUnifications
```

When `executedUnifications == 0`, the runner reports `proofYield = 0` by convention and records the semantic effect separately as:

```
directDelta = knowledgeDelta
```

A positive `directDelta` identifies a semantic result produced outside the measured Linker-unification path. It must not be interpreted as zero semantic value.

This is deliberately not a complete semantic measure. Causes, used-only effects, generated-rule provenance, hypothesis changes, and deferred TSolve creation are not yet attributed to individual operations.

## Isolation invariant

Every measured operation runs in an independent `Mind` populated from the same durable `value/3` fixture.

This is required because `SolutionsStore` and `ValuesStore` retain materialized query results. Sequentially evaluating several query shapes in one `Mind` causes an earlier query to suppress deltas in later queries through deduplication. Such a sequence is useful for cache/materialization studies, but it is not a valid independent semantic-yield baseline.

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
rule_delta
solution_delta
value_row_delta
knowledge_delta
direct_delta
proof_yield
```

`result_rows` is the positive value-row delta of the isolated operation, not the cumulative size of the result store.

## Interpretation boundary

`solution_delta` and `value_row_delta` currently describe externally materialized query state. They are included in the coarse `knowledgeDelta`, but they are not yet proven to be durable proof production.

Accordingly, this experiment measures an observational semantic-output yield. The next instrumentation layer must distinguish proof effects from presentation/materialization effects before Planner uses the metric as an optimization objective.

## Safety boundary

The experiment is observational only. It does not modify candidate selection, pass ordering, unification, materialization, storage, transactions, or lifecycle ownership.

## Next layer

After the isolated baseline is validated, instrument individual proof operations with exact effect masks:

```
NEW_TVALUE
NEW_GENERATED_RULE
NEW_CAUSE
NEW_SOLVE
USED_ONLY
NO_OBSERVABLE_EFFECT
```

Deferred effects must remain separate until they can be reliably attributed to the operation that caused them.
