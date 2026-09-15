# Shadow activation: Rule granularity limit

This experiment compares a proposed next-pass work set with the complete old
traversal. No work is skipped. Enable `kanger.experiment.shadowActivation=true`;
`kanger.experiment.shadowClosure=true` selects component closure instead of one
structural hop. Both are off by default.

The first pass is unrestricted. At each completed-pass boundary, seeds comprise
newly visible Rules, current consumers of changed enumerated TValue sets, and
consumers of added/removed TSolve keys. Expansion follows predicate/opposite
polarity compatibility between Domains of entire Rules. The proposed set is
frozen throughout the following pass, including both traversal directions.

For each visited Rule outside the proposal, the observer records unification
attempts, operational new-TValue events, and the net size change of the TSolve
relation during that visit. TValue events can roll back. A positive tuple delta
means the relation grew across that reference visit, not necessarily that the
final logical answer would differ under a different scheduler.

## Results: six fixtures, 16 operations

| Proposal | Later-pass Rule slots covered | Total reference slots | Outside visits with observed work | Outside visits with TSolve growth |
|---|---:|---:|---:|---:|
| One structural hop | 896 | 1198 | 560 | 2 |
| Full component closure | 1198 | 1198 | 0 | 0 |

A Rule slot is one Rule in a pass's reference set; the two traversal directions
visit it separately. Outside-visit counts use those actual directional visits.
Coverage excludes first passes and counts only Rules in the reference set,
not unrelated Rules that might also be in a proposed component.

The concrete misses occur in `functions ?$x q(x);` after:

```text
!p(1); !p(2); !@x p(x) -> q(x+1);
```

On pass 2, the proposal contains IDs [2,3,4,6,7,8] while the reference includes
eight Rules. Visits of IDs 1 and 0 each perform one unification, create no new
TValue event, and add one TSolve. This is a reproducible reference-effect miss
for the frozen one-hop proposal. It is not a measured final-answer difference:
the experiment deliberately runs the entire reference path.

Full closure removes these misses by covering every reference Rule slot in all
observed later passes. Thus it supplies no Rule-visit reduction in this corpus;
zero misses here cannot be advertised as a useful optimized scheduler.

## Verification and limits

Off and factory-verify produce byte-identical output for each proposal. Removing
the new activation trace lines reproduces the preceding tuple checkpoint
exactly. Raw outputs are under `latent-substitution-evidence/shadow/`.
Compilation uses ECJ targeting Java 8 on OpenJDK 17. Full Maven / Java 8/21 gates
remain outstanding.

This is deliberately an incomplete candidate scheduler. It does not react within
a pass, track every Rule status change, retain old consumers of vanished
variables, or cover all Cause/used/excluded/calculated/function state changes.
The outside-work detector also does not observe every possible semantic effect.
Neither a zero detector count nor structural reachability proves equivalence.

## Decision

Do not implement either proposal as an execution filter. On the measured corpus,
one-hop Rule activation loses reference effects and component closure loses all
potential Rule-visit savings. This is evidence against this coarse frozen-set
approach, not against the latent domain index itself.

The next bounded experiment should operate on concrete Domain pairs and
substitution tuples, with observation of when a pair gains new inputs and when
its previous inputs are revisited. It must preserve orientation/order and
checkpoint effects. Avoid adding a general scheduler abstraction before those
dependencies and actual repeated work have been measured.

Reproduce with the tuple-checkpoint commands plus
`-Dkanger.experiment.shadowActivation=true`, then repeat with
`-Dkanger.experiment.shadowClosure=true`. Compare off/factory-verify separately
for each proposal. The added output prefix is `activation-trace=`.
