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

and:

```
proofYield = knowledgeDelta / executedUnifications
```

This is deliberately not a complete semantic measure. Causes, used-only effects, generated-rule provenance, hypothesis changes, and deferred TSolve creation are not yet attributed to individual operations.

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
proof_yield
```

## Safety boundary

The experiment is observational only. It does not modify candidate selection, pass ordering, unification, materialization, storage, transactions, or lifecycle ownership.

## Next layer

After this coarse hypothesis is validated, instrument individual proof operations with exact effect masks:

```
NEW_TVALUE
NEW_GENERATED_RULE
NEW_CAUSE
NEW_SOLVE
USED_ONLY
NO_OBSERVABLE_EFFECT
```

Deferred effects must remain separate until they can be reliably attributed to the operation that caused them.
