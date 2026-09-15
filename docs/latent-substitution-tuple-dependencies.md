# Tuple-only activation checkpoint

`-Dkanger.experiment.traceTuples=true` independently observes the TSolve relation
at each completed-pass boundary. Tuple identity is the sorted list of TValue
IDs. Added tuples record current Rule consumers and whether all member IDs were
already exposed by variable enumeration before the pass. Removed tuples retain
the consumers observed before removal. This is optional diagnostics, not a new
scheduler, persistent index or continuation condition.

## Qualification

LatentBindingObservationRunner now exercises the existing isValidFor method:

1. Create all three TValue objects for x=10, y=20 and y=30.
2. Select x=10, y=30. With no TSolve constraints the assignment is admissible.
3. Add only the tuple (x=10,y=20). The selected assignment becomes inadmissible.
4. Add (x=10,y=30), using the already existing values. It becomes admissible.
5. Re-add that tuple; the observation still reports exactly one addition.

The variable/value sets remain equal across step 4. Thus tuple-only changes can
change admissibility in the actual reference implementation. This focused test
does not establish which scheduling policy preserves complete inference.

## Corpus observations

Six fixtures / 16 operations expose 394 new tuples at completed-pass boundaries;
341 use only values already exposed before their respective pass. Of those 341,
44 are singleton tuples and 297 have multiple elements. These categories must
not be equated with 341 newly enabled assignments: constraints, tuple shape and
other runtime state determine actual impact.

| Native query, pass 2 | New tuples | All values preexisting | Rule consumers | Outgoing action mask |
|---|---:|---:|---:|---:|
| `?$x male(x);` | 90 | 90 | 9 | 8 (TempHypothesis only) |
| `?male(Tom);` | 94 | 94 | 9 | 8 (TempHypothesis only) |

These passes change the TSolve relation despite raising neither Rule nor TValue
action. Their subsequent passes produce Rule/TValue actions, as recorded in the
previous checkpoint. The observations identify a missing dependency in any
proposal based only on existing action flags; they do not isolate TSolve as the
sole cause of the later activity.

Off and factory-verify traces and logical projections matched byte for byte.
Removing tuple-trace lines exactly reproduces the previous binding checkpoint.
The focused runner passes both binding rollback and tuple-only activation tests.
Raw logs are in `latent-substitution-evidence/tuples/`.

## Boundary for the next implementation

A proposed work set must include Rules affected by changes to the tuple
relation, even with an unchanged set of TValue IDs. New tuples can both
introduce constraints and extend allowed combinations. Reference isValidFor
also has special unary-tuple behavior; do not replace it with a generic join.

This observer reports additions/removals between completed passes. It does not
report changes that cancel within a pass, changes of consumers for an unchanged
tuple key, or early-exit state. It is not a transaction event journal. Used,
excluded, calculated and Cause state, function dependencies, and ordering still
need coverage before any pass/direction can be skipped.

Reproduce with the binding-checkpoint commands, additionally setting
`-Dkanger.experiment.traceTuples=true`. Run
`org.kanger.LatentBindingObservationRunner` for the focused admissibility test.
Local environment remains ECJ / Java 8 target on OpenJDK 17; full Maven and
canonical Java 8/21 qualification are still outstanding.
