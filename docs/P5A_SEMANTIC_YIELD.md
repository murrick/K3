# P5a — Semantic Yield and Operation Effects

Target line: KANGER III 3.4.1

Baseline: `snapshot/3.3.5-p4-linear-baseline`

## Purpose

Execution cost alone is not sufficient for planning. Planner must estimate both:

1. how much work an operation performs;
2. how much new proof state that work can produce.

P5a establishes an observational vocabulary and a reproducible baseline. It does not yet change planning or inference order.

## Two yield measures

The original coarse external measure is retained for comparison:

```text
knowledgeDelta = newTValues
               + positive rule count delta
               + positive solution count delta
               + positive result-row delta
```

Canonical proof-internal production is now measured directly:

```text
effectDelta = newTValues
            + unique new Causes
            + unique new TSolves
            + canonical generated Rules
```

When Linker executes at least one unification:

```text
proofYield  = knowledgeDelta / executedUnifications
effectYield = effectDelta    / executedUnifications
```

When `executedUnifications == 0`, both ratios are zero by convention and the externally visible direct result is recorded separately:

```text
directDelta = knowledgeDelta
```

A positive `directDelta` must not be interpreted as zero semantic value.

## Isolation invariant

Every measured operation runs in an independent `Mind` populated from an equivalent durable fixture.

`SolutionsStore` and `ValuesStore` retain materialized query results. Sequentially measuring different query shapes in one `Mind` contaminates later deltas through deduplication. Such runs may be useful for cache studies, but they are not an independent semantic-yield baseline.

## Canonical telemetry boundaries

`SemanticEffectTelemetry` is an opt-in `ThreadLocal` session opened immediately before a measured query and removed immediately afterwards. It is inactive during normal execution.

The current direct hooks are placed at semantic boundaries rather than constructors:

```text
NEW_CAUSE
  Cause accepted into the canonical Cause set

SOLVE_CANDIDATE
  deferred variant reaches Mind.addTSolve

NEW_TSOLVE
  tuple survives Mind.addTSolve deduplication and enters ruleSolves

DUPLICATE_SOLVE_CANDIDATE
  tuple reaches Mind.addTSolve but resolves to an existing TSolve

NEW_GENERATED_RULE
  generated Rule survives canonical RuleFactory insertion
```

`NEW_TVALUE` continues to use the direct Linker counter.

The hooks do not participate in logical equality, candidate selection, transactions, persistence, or execution ordering.

## Operation-local immediate masks

Every recorded Linker unification receives exactly one immediate-effect mask. The currently defined bits are:

```text
1  NEW_TVALUE
2  NEW_CAUSE
4  DEFERRED_SOLVE_CANDIDATE
8  USED_ONLY
```

Mask `0` means `NO_IMMEDIATE_EFFECT`.

The current isolated `value/3` workload produces only three masks:

```text
mask 0  NO_IMMEDIATE_EFFECT
mask 4  DEFERRED_SOLVE_CANDIDATE
mask 7  NEW_TVALUE | NEW_CAUSE | DEFERRED_SOLVE_CANDIDATE
```

For each result-producing unit of work, the exact distribution is:

```text
3 × mask 0
4 × mask 4
1 × mask 7
----------------
8 Linker unifications
```

For an all-variable query over `N` rows this scales linearly:

```text
3N × mask 0
4N × mask 4
 N × mask 7
----------------
8N Linker unifications
```

This `3:4:1` invariant is enforced by `KangerOperationEffectMaskRunner` and CI.

## Deferred solve compression

Immediate masks deliberately report a deferred candidate, not a completed `TSolve`.

At the canonical `Mind.addTSolve` boundary, the current `value/3` workload shows:

```text
5 solve candidates per result
1 unique new TSolve per result
4 duplicate candidates per result
```

Therefore:

```text
solveCandidates = newTSolves + duplicateSolveCandidates
                = 5R

newTSolves = R
duplicateSolveCandidates = 4R
```

where `R` is the number of result rows.

This distinction prevents candidate attempts from being counted as semantic production.

## Transient generated Rules

The direct RuleFactory hook reveals one canonical generated Rule per inferred result even when the externally visible rule count does not grow:

```text
newGeneratedRules = resultRows
ruleDelta         = 0
```

The generated Rule is a real transient proof object of the child inference lifecycle. It is not visible through a before/after count on the parent `Mind`.

Consequently, the earlier coarse measure systematically undercounted proof-internal production:

```text
effectDelta = knowledgeDelta + resultRows
```

for the current inferred `value/3` family.

## Verified semantic-yield baseline

For selective one-result queries:

```text
query-two-constants:
  executedOperations = 8
  knowledgeDelta     = 3
  effectDelta        = 4
  proofYield         = 0.375
  effectYield        = 0.500

query-one-constant:
  executedOperations = 8
  knowledgeDelta     = 4
  effectDelta        = 5
  proofYield         = 0.500
  effectYield        = 0.625
```

For the all-variable query:

```text
size 100:
  executedOperations = 800
  knowledgeDelta     = 401
  effectDelta        = 501
  proofYield         = 0.501250
  effectYield        = 0.626250

size 500:
  executedOperations = 4000
  knowledgeDelta     = 2001
  effectDelta        = 2501
  proofYield         = 0.500250
  effectYield        = 0.625250

size 1000:
  executedOperations = 8000
  knowledgeDelta     = 4001
  effectDelta        = 5001
  proofYield         = 0.500125
  effectYield        = 0.625125
```

Thus:

```text
proofYield  → 0.500
effectYield → 0.625
```

The direct exact lookup remains separate:

```text
executedOperations = 0
effectDelta        = 0
directDelta        = 1
```

## Generated-rule depth experiment

The independent generated-rule runner verifies:

```text
one-hop:
  executedOperations  = 4
  solveCandidates     = 4
  newTSolves          = 1
  duplicateCandidates = 3
  newGeneratedRules   = 1
  effectDelta         = 4

two-hop:
  executedOperations  = 10
  solveCandidates     = 10
  newTSolves          = 2
  duplicateCandidates = 8
  newGeneratedRules   = 2
  effectDelta         = 8

deduplicated-target:
  executedOperations  = 4
  solveCandidates     = 4
  newTSolves          = 1
  duplicateCandidates = 3
  newGeneratedRules   = 0
  effectDelta         = 3
```

Generated-rule production composes with inference depth, while an already durable target still performs proof work without producing another generated Rule.

## Output artifacts

The main runners are:

```text
org.kanger.KangerSemanticProfileRunner
org.kanger.KangerOperationEffectMaskRunner
org.kanger.KangerSemanticYieldRunner
org.kanger.KangerGeneratedRuleYieldRunner
```

Default sizes are `100,500,1000`.

The semantic-yield CSV contains:

```text
size
operation
result_rows
passes
executed_operations
new_tvalues
new_causes
solve_candidates
new_tsolves
duplicate_solve_candidates
new_generated_rules
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

## Safety boundary

P5a remains observational. It does not modify:

```text
candidate selection
Linker pass order
unification semantics
materialization behavior
storage format
transaction ownership
lifecycle ownership
```

## Remaining causal boundary

Immediate effects are attributed to individual unifications. Deferred effects are currently canonical and exact at query level, but not yet assigned to a single originating unification.

The current evidence shows why assigning a deferred result to the first candidate would be unsound:

```text
5 candidate-producing operations
        ↓ canonical deduplication
1 unique TSolve
4 duplicate candidates
```

The next P5a layer must preserve the contributor set for each deferred tuple and represent shared causal credit explicitly. `NEW_TSOLVE` and downstream `NEW_GENERATED_RULE` should be attached to a deferred-effect group, not arbitrarily awarded to one candidate operation.
