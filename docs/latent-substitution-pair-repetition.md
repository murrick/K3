# Pair/input repetition profile

Enable `-Dkanger.experiment.tracePairInputs=true` for diagnostic classification of
completed unification operations. The observation key includes ordered master
and slave Domain IDs, each argument's type and empty/value-ID state, current
TValue IDs for direct TVariable arguments, and the incoming accumulated
linkDomains `result` flag. The last flag matters to the reference checkpoint
decision. Keys reset at each link invocation.

This is an argument projection, **not a complete semantic memoization key**.
It omits, among other things, nonargument variable context, used/excluded state,
Causes, TSolve relation versions and function/runtime context. No operation is
skipped, cached or replaced.

## Six fixtures / 16 operations

| Observed input class | Completed operations | New TSolve at deferred pair boundary |
|---|---:|---:|
| First occurrence | 1343 | 378 |
| Repeat, preceding occurrence in same pass | 2233 | 0 |
| Repeat, preceding occurrence in earlier pass | 1960 | 0 |

Total operations: 5536. Repeats: 4193 (75.74%). Classification is relative to the
most recent occurrence, not necessarily the first occurrence of the key.

New-TSolve attribution checks relation size around the existing deferred
`mind.addTSolve` call and attributes growth to its contributing operation ID.
The 378 creations cover this boundary only. They must not be confused with the
394 tuple additions previously observed across entire passes: function and
predicate paths also call addTSolve outside deferred pair processing.

For repeated operations, effect masks include:

* 3540 deferred-candidate-only operations (mask 4).
* 161 TValue-event plus deferred-candidate operations (mask 5).
* 490 operations with no recorded immediate effect (mask 0).
* 2 used-only operations (mask 8).

No repeat records a new Cause through the existing operation mask or creates a
new TSolve at the observed deferred boundary in this corpus. However, TValue
events can represent rolled-back work or existing-value reuse, and domain/rule
state writes are not fully represented by these masks. The counts do not prove
that repeated execution is semantically redundant.

## Verification

Off and factory-verify produce byte-identical pair profiles and logical
projections. Removing pair-effects and pair-new-tuples lines reproduces the
previous action-histogram checkpoint exactly. Raw output is checked in under
`latent-substitution-evidence/pairs/`. Instrumentation is off by default; do not
use diagnostic timings as performance evidence. Local build uses ECJ targeting
Java 8 on OpenJDK 17; canonical Maven / Java 8/21 qualification remains pending.

The 48-element pair-effects array consists of three consecutive 16-mask groups:
first occurrence, same-pass repeat, later-pass repeat. Bits retain existing
LinkerStatistics meaning: TValue event=1, new Cause=2, deferred candidate=4,
used-only=8. pair-new-tuples has one entry per corresponding input class.

## Next boundary

The measurements identify a concrete repeated-work target at pair granularity,
unlike the coarse Rule-component experiment. Before proposing a skip or replay,
qualify retained binding changes and used/excluded/Cause effects on repeated
keys, including the carried result flag and checkpoint decisions. A replay must
preserve those effects and ordering; absence of new tuples alone is insufficient.

Reproduce by running LatentSubstitutionStateRunner in off and factory-verify
with `-Dkanger.experiment.tracePairInputs=true`, then compare stdout byte for byte.
