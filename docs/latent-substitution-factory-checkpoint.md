# Latent substitution: factory lifetime checkpoint

This follows `latent-substitution-experiment.md`. The experimental branch remains
based on develop/3.7.0 at `3ad50f1e5253304f6b530de11c078f332ea4db89`.
No persistence format or default inference path changes.

## Implementation

Select `-Dkanger.experiment.latent=factory` before constructing any Mind;
`factory-verify` additionally compares every selected occurrence list with the
old exhaustive Rule-tree predicate/polarity filter, including identity, order
and duplicates. `off` remains the default. Existing `index`/`verify` modes retain
the earlier per-rotator snapshot for comparison.

RuleCandidateIndex now optionally owns immutable ordered Domain-ID rows keyed
by Rule ID and predicate/polarity. Rule publication and hydration construct
rows. Accepted generated productions use the same RuleFactory publication
point. Opening storage prepares the derived index before queryCheck. Nested
checkpoint journals restore prior rows; child commit shares immutable rows;
transaction/generation reset discards them. Rows retain no Rule, Domain or Mind
objects. Active-factory lookup resolves IDs back to canonical Domains.

This is a factorized occurrence index, not a materialized full S-to-A graph.
The existing Rule selector still runs, including its runtime bindings and used
effects. The old coarse per-rotator Rule domain index and both traversal
directions also remain. Only the experimental occurrence rows have moved out of
the repeated rotator build. No arity or value restriction is added to the old
predicate/polarity compatibility gate.

## Evidence and limits

Final local run completed with `LATENT_PROFILE_EQUIVALENCE_PASS` and
`LATENT_EXPERIMENT_PASS`. Raw outputs and benchmark samples are checked in under
`latent-substitution-evidence/factory/`.

The qualification script compares the 123-case corpus in off, verify and
factory-verify modes. Lifecycle coverage includes publication, produced rules,
child rollback/commit, rejected collision, deletion, close/reopen and storage
replacement. Factory lookup checks compare against exhaustive visible Rule
trees and assert that repeated lookup does not rebuild rows. A separate journal
runner covers repeated occurrences, empty buckets, nested commit/rollback,
child merge and clear. Five modes compare the same serialized logical-state
projection; the profile compares semantic counters and allowed visit reduction.
These are corpus evidence, not a universal proof or a complete serialization of
every internal collision witness.

Benchmark runner uses three warmups and seven measured fresh-Mind iterations
per workload; formatting is outside the timed compile/query regions. Median
milliseconds from this workspace:

| Mode | natives compile | natives query | 100 singleton compile | 100 singleton query |
|---|---:|---:|---:|---:|
| off | 89.218 | 105.942 | 17.080 | 23.312 |
| index | 109.679 | 116.492 | 18.106 | 22.186 |
| factory | 98.069 | 104.913 | 13.666 | 21.095 |

Native-query domain visits fall from 14,282 to 3,251; unification attempts remain
3,251. Singleton visits and unifications remain 800. These short separate-JVM
runs do not establish a significant speedup. Factory map retained heap and
allocation cost are still unmeasured; the earlier adjacency-array measurements
must not be presented as the memory cost of this representation.

Environment remains OpenJDK 17.0.20 with ECJ 3.33.0 targeting Java 8. Canonical
Java 8/21 and full Maven qualification remain outstanding.

## Next investigation

Before skipping passes, classify all invalidation sources. Linker repeats on
Rule, TValue, FValue, Hypothesis and TempHypothesis actions. New productions are
only one trigger: bindings, function results and hypothesis changes can enable
work on existing topology. Used/excluded/calculated state and solve state also
constrain ordering. Direct adjacency alone is insufficient evidence to remove
either traversal or schedule only newly added Rules.

Next work should observe which actions cause each pass, map their affected
domains and compare a proposed activation set with the old traversal, without
skipping execution until equivalence is demonstrated. Preserve the old Linker
as oracle throughout.
