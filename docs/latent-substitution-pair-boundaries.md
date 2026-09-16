# Repeated-pair boundary effects

The pair-input diagnostic now samples state immediately before the pair's local
checkpoint and after commit/release plus markExcluded/Rule-used processing.
It records TValue IDs exposed by forEach for variables occurring in the pair,
cardinalities of the used/excluded Domain argument sets and used-Rule sets, the
checkpoint decision, and the accumulated result transition. Deferred TSolve
creation remains separately attributed at its existing boundary.

## Six fixtures / 16 operations

| Class | Operations | Commit | Release | Added enumerated TValue IDs | Used entries added | Excluded entries added | Used-Rule entries added | result false -> true |
|---|---:|---:|---:|---:|---:|---:|---:|---:|
| First input occurrence | 1343 | 127 | 1216 | 53 | 256 | 507 | 81 | 40 |
| Repeat within pass | 2233 | 102 | 2131 | 0 | 0 | 0 | 0 | 12 |
| Repeat in later pass | 1960 | 135 | 1825 | 0 | 0 | 0 | 0 | 26 |

No removed enumerated TValue IDs were observed in these groups. The 161 repeated
operations with the earlier new-TValue event bit leave no added enumerated IDs
at this boundary. The operational event is therefore not equivalent to a newly
retained binding. These measurements do not individually distinguish rollback
from canonical reuse or establish persistence-level durability.

Repeated operations still activate the accumulated result flag 38 times. A skip
that simply returns without preserving that change does not reproduce the old
control state, even when the measured sets remain unchanged. Carried result can
influence the checkpoint decision for subsequent pairs in the same call.

The diagnostic also compares the outgoing result with the previous completed
operation having the same observed input key (which includes incoming result).
There were zero disagreements, in both repeat classes. Replaying that one bit
is consistent with this corpus; it is not proof of a complete reusable result.

## Verification and scope

Off and factory-verify stdout matched byte for byte. Removing pair-boundaries
and pair-result-changes lines reproduces the prior pair profile exactly. Raw
outputs are under `latent-substitution-evidence/pair-boundaries/`.

Instrumentation remains under `kanger.experiment.tracePairInputs=true` and never
skips or replays inference. The 27-element boundary array has three groups of
nine columns matching the table, with removed-value count inserted after
added-value count. pair-result-changes has one disagreement count per class.

Set cardinalities do not serialize entry contents or detect all equal-size
mutations. Observed TValue sets follow forEach rather than defining a new
visibility policy. Nested argument context, Causes, CVariable lineage, function
state and deferred contributions remain relevant. The absence of measured
growth cannot certify absence of semantic effects.

## Next experiment

Compare a proposed replay of repeated-pair outputs against the reference,
including result, checkpoint disposition and deferred substitution IDs. Continue
executing the old operation during comparison. Establish invalidation and
context requirements before any cached replay replaces execution.

This narrows the optimization target to avoiding repeated search/unification
while preserving its control and state effects. It does not justify changing
the default path or removing either traversal.

Local compilation/execution remains ECJ targeting Java 8 on OpenJDK 17; full
Maven and canonical Java 8/21 gates are still outstanding.
