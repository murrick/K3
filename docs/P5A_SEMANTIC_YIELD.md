# P5a — Semantic Yield and Causal Operation Effects

Target line: KANGER III 3.4.1

Baseline: `snapshot/3.3.5-p4-linear-baseline`

Verified CI run: `30517349239`

## Status

P5a is complete as an observational checkpoint.

It establishes:

- isolated semantic-yield experiments;
- operation-local immediate-effect classification;
- canonical deferred-effect boundaries;
- contributor groups for deferred tuples;
- complete generated-Rule attribution;
- conservative shared causal credit with effect conservation.

P5a does not alter Planner decisions or inference semantics. The next architectural layer is P5b: estimating these quantities before execution and using the estimates in Planner ranking.

## Purpose

Execution cost alone is not sufficient for planning. Planner must estimate both:

1. how much work an operation performs;
2. how much new proof state that work can produce.

P5a supplies the vocabulary, instrumentation, experiments, and reproducible baselines required for that transition.

## Two yield measures

The original coarse external measure is retained for comparison:

```text
knowledgeDelta = newTValues
               + positive rule count delta
               + positive solution count delta
               + positive result-row delta
```

Canonical proof-internal production is measured directly:

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

`SolutionsStore` and `ValuesStore` retain materialized query results. Sequentially measuring different query shapes in one `Mind` contaminates later deltas through deduplication. Such a sequence may be useful for cache studies, but it is not a valid independent semantic-yield baseline.

## Canonical telemetry boundaries

`SemanticEffectTelemetry` is an opt-in `ThreadLocal` session opened immediately before a measured query and removed immediately afterwards. It is inactive during normal execution.

Direct hooks are placed at semantic boundaries rather than constructors:

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

Constructor calls are not treated as semantic production. The hooks do not participate in logical equality, candidate selection, transactions, persistence, or execution ordering.

## Operation-local immediate masks

Every recorded Linker unification receives exactly one immediate-effect mask. The defined bits are:

```text
1  NEW_TVALUE
2  NEW_CAUSE
4  DEFERRED_SOLVE_CANDIDATE
8  USED_ONLY
```

Mask `0` means `NO_IMMEDIATE_EFFECT`.

The isolated `value/3` workload produces only three masks:

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

An immediate mask reports a deferred candidate, not a completed `TSolve`.

At the canonical `Mind.addTSolve` boundary, the inferred `value/3` workload shows:

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

Candidate attempts are measured as cost. Only the tuple that survives canonical deduplication is counted as semantic production.

## Transient generated Rules

The direct RuleFactory hook reveals one canonical generated Rule per inferred result even when the externally visible parent rule count does not grow:

```text
newGeneratedRules = resultRows
ruleDelta         = 0
```

The generated Rule is a real transient proof object in the child inference lifecycle. A before/after rule count on the parent `Mind` cannot observe it.

For the inferred `value/3` family:

```text
effectDelta = knowledgeDelta + resultRows
```

The earlier coarse measure therefore systematically undercounted proof-internal production.

## Deferred causal groups

Every Linker unification receives a stable query-local `operationId`.

When a unification contributes a deferred substitution, Linker preserves an observational envelope:

```text
DeferredSolveCandidate {
    operationId
    substitution
}
```

Contributors are grouped by the same canonical sorted TValue-ID tuple used for `TSolve` deduplication. No candidate is selected as an arbitrary winner.

The inferred `value/3` workload produces:

```text
1 deferred group per result
5 distinct operationId contributors per group
1 new TSolve per group
1 generated Rule per group
0 ungrouped generated Rules
```

The generated Rule is attached retrospectively to its group through the Rule's populated `solves` tuple. This attribution is complete for all verified scenarios.

## Shared causal credit

A deferred group may contain several contributors and several downstream effects.

For each group:

```text
groupEffects = (new TSolve ? 1 : 0)
             + number of generated Rules

creditPerContributor = groupEffects / contributorCount
```

Every contributor receives equal conservative credit. The allocation preserves the total effect:

```text
sum(contributor credits in group) = groupEffects
```

This avoids falsely awarding the complete downstream result to the first, last, or otherwise incidental candidate operation.

Verified credit densities:

```text
inferred value/3:
  2 downstream effects / 5 contributors = 0.400000000

one-hop:
  2 downstream effects / 4 contributors = 0.500000000

two-hop:
  group sizes = 4 and 6
  per-group credit = 0.500000000 and 0.333333333
  average = 4 effects / 10 contributor links = 0.400000000

deduplicated target:
  1 downstream effect / 4 contributors = 0.250000000
```

No verified deferred group lacks contributors, and no verified generated Rule remains outside a group.

## Verified semantic-yield baseline

Selective one-result queries:

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

All-variable query:

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

The direct exact lookup remains a separate path:

```text
executedOperations = 0
effectDelta        = 0
directDelta        = 1
```

## Generated-rule depth experiment

```text
one-hop:
  executedOperations        = 4
  solveCandidates           = 4
  newTSolves                = 1
  duplicateCandidates       = 3
  deferredGroups            = 1
  contributors              = 4
  newGeneratedRules         = 1
  ungroupedGeneratedRules   = 0
  deferredGroupEffects      = 2
  sharedCredit              = 0.500000000
  effectDelta               = 4

two-hop:
  executedOperations        = 10
  solveCandidates           = 10
  newTSolves                = 2
  duplicateCandidates       = 8
  deferredGroups            = 2
  contributorGroupSizes     = 4, 6
  newGeneratedRules         = 2
  ungroupedGeneratedRules   = 0
  deferredGroupEffects      = 4
  sharedCreditAverage       = 0.400000000
  sharedCreditMin           = 0.333333333
  sharedCreditMax           = 0.500000000
  effectDelta               = 8

deduplicated-target:
  executedOperations        = 4
  solveCandidates           = 4
  newTSolves                = 1
  duplicateCandidates       = 3
  deferredGroups            = 1
  contributors              = 4
  newGeneratedRules         = 0
  ungroupedGeneratedRules   = 0
  deferredGroupEffects      = 1
  sharedCredit              = 0.250000000
  effectDelta               = 3
```

Generated-rule production composes with inference depth. An already durable target still performs proof work but produces no additional generated Rule.

## Runners and artifacts

The main runners are:

```text
org.kanger.KangerSemanticProfileRunner
org.kanger.KangerOperationEffectMaskRunner
org.kanger.KangerSemanticYieldRunner
org.kanger.KangerGeneratedRuleYieldRunner
```

Default workload sizes are `100,500,1000`.

The semantic-yield CSV contains operational, canonical-effect, causal-group, shared-credit, materialization, and yield columns, including:

```text
solve_candidates
new_tsolves
duplicate_solve_candidates
deferred_groups
deferred_contributor_links
groups_with_new_tsolve
minimum_contributors_per_group
maximum_contributors_per_group
groups_with_generated_rule
generated_rule_group_links
ungrouped_generated_rules
deferred_group_effects
groups_without_contributors
average_deferred_credit_per_contributor
minimum_deferred_credit_per_contributor
maximum_deferred_credit_per_contributor
knowledge_delta
effect_delta
proof_yield
effect_yield
```

## Validation

CI run `30517349239` passed on:

- Ubuntu / Java 8;
- Ubuntu / Java 21;
- macOS / Java 21.

Ubuntu / Java 21 additionally passed:

- persistent storage lifecycle diagnostics;
- linear Linker profiling;
- semantic operation profiling;
- the exact `3:4:1` mask baseline;
- canonical `TSolve` compression accounting;
- contributor-group size invariants;
- complete generated-Rule-to-group attribution;
- shared-credit conservation and exact verified credit densities;
- semantic-yield and generated-rule-depth profiles.

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

## Next layer: P5b

P5a answers what happened and how proof effects should be attributed after execution.

P5b must estimate before execution:

```text
Expected immediate effect mask
Expected deferred group formation
Expected contributor count
Expected canonical TSolve production
Expected generated-Rule production
Expected shared causal credit
Expected Semantic Yield
```

Planner can then rank execution alternatives by expected semantic return rather than runtime cost alone.
