# Structural argument-position prototype

Startup option `-Dkanger.experiment.argumentPlan=true` requires factory or
factory-verify mode selected before constructing Mind. Default is disabled.

OccurrenceRows optionally stores an immutable string per Domain: `v` marks a
direct TVariable position and `.` marks everything else. Publication/hydration
builds it. Existing row journals, child merge and generation reset also govern
the plan. There is no persistent schema change or separate manager.

Linker uses these masks for the four direct-TVariable classifications in its
direction guards and substitution loops. It keeps the old loop order, arity
behavior, empty/value checks, binding lookup, CVariable logic, checkpoint choices,
used/excluded effects and deferred TSolve processing. It does not precompute
blockLeft/blockRight or cache runtime outputs.

## Scope of structural stability

Argument.getType returns the stored type. Setting a TVariable's value updates its
runtime binding; it does not change the direct-TVariable classification.
Argument.setValue can change EMPTY to TERM, which remains `.` in this mask.
Public setObject/clear or mutation of published argument lists could invalidate
the plan. This experiment does not promise support for arbitrary external
mutation of published structures. Factory-verify compares each retrieved mask's
length and every position against the live Domain and fails on mismatch.

## Qualification

* All 123 corpus cases pass with argumentPlan plus factory-verify.
* The six-fixture / 16-operation logical-state and action-histogram output is
  byte-identical to the original off-mode checkpoint.
* Lifecycle runner checks masks for all observed Domains twice, covering child
  commit/rollback, generated Rules, deletion, close/reopen and replacement.
  Repeated lookups do not rebuild the occurrence index.

The first captured state output was incomplete; it is not used as evidence.
A separate completed run matched the original output exactly. Only that run is
checked in. No mismatch was reported by the completed qualification runs.

## Cost and decision

An initial timing run overlapped qualification work and is excluded. An isolated
follow-up used three warmups and seven measured fresh-Mind iterations per case,
running plan-on then plan-off in separate JVMs. Median milliseconds:

| Case | Factory compile | Plan compile | Factory query | Plan query |
|---|---:|---:|---:|---:|
| natives | 91.110 | 95.396 | 99.435 | 116.814 |
| 100 singleton facts | 16.219 | 15.142 | 22.209 | 21.707 |

Rows, domain-pair visits and unification counts match for every corresponding
sample. This small benchmark is not statistically qualified. It shows no
established performance benefit and a worse native-query median in this run.

The prototype fetches two masks through factory/index lookup for each compatible
pair to replace inexpensive getType checks. That overhead is a plausible reason
for the result, not a measured attribution. Mask retained heap is unmeasured.

Keep disabled. Do not promote this implementation as an optimization. It
qualifies one narrow structural boundary, but moving a cheap classification
alone is insufficient. Any next proposal should remove substantial candidate
discovery or repeated traversal, with an explicit cost model, rather than add
more index lookups around existing work.

Raw corpus/lifecycle/state evidence and the isolated benchmark samples are under
`latent-substitution-evidence/argument-plan/`. Reproduce with the existing runners
and argumentPlan=true plus factory-verify; use factory without verify for timing.
Local environment remains ECJ targeting Java 8 on OpenJDK 17; full Maven and
canonical Java 8/21 qualification remain outstanding.
