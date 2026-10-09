# Mandatory diagnostic consumer gate

This isolated experiment follows `78bed5b51401244ab65cf3624af237fa8f7d78cd` on exact SMART-era native core `5d5f6aff271f1abf371fbde55d637744c53f0bc3`. The preceding stage only recorded an unsupported recycled-identity verdict. This stage enforces that verdict at a concrete diagnostic consumer API: `QualifiedJournalConsumer.finish()` either returns an immutable checked trace or throws a typed refusal. No partial trace is returned on refusal.

## Export contract

`begin()` opens both unchanged diagnostic journals and resets the adapter's session registry. Native callbacks record identity collisions automatically: an existing descendant resolving the new value ID to a distinct captured object taints the whole export session. The guard has no disable property. Retirement cannot erase previously recorded taint, and a fresh descendant does not make the earlier trace safe.

`finish()` always completes both journal checks, including when the first throws its assertion. It then applies the following refusal priority. Its `finally` block stops the adapter and closes the consumer session. Only a fully accepted, equal pair of traces is copied into an immutable result.

| Refusal code | Trigger |
| --- | --- |
| `NO_SESSION` | No open consumer session |
| `ADAPTER_FAILURE` | Unsupported or failed diagnostic capture was recorded |
| `RECYCLED_ID` | A distinct descendant object shares a newly added value ID |
| `FULL_AUTHORITY_MISMATCH` | Either underlying journal refused its checks |
| `JOURNAL_DISAGREEMENT` | Both checks returned, but their traces differ |

Adapter failures and collisions are recorded without throwing from the native mutation callback. The refusal occurs when the diagnostic consumer attempts to export. Native mutation results, exception behavior, cache state and context semantics remain unchanged. Journal assertions are drained even for a colliding session so their independent accept/reject evidence is retained as a boolean in the typed refusal. Unexpected runtime failures also prevent a trace return and close the consumer; this bounded fixture qualifies the named refusal paths listed below.

The diagnostic driver deliberately continues native observation after a collision to characterize the full boundary, then attempts export. That continuation is not a qualified result: only the consumer's returned trace may be treated as an accepted export. This API is not wired into the production engine or an external application.

## Validation

The matrix contains three case-order permutations × three optimization flag settings × clean/instrumented execution: 18 fresh JVMs and 162 native boundary cases. Native enumeration, canonical pointer identity and both pure authorities retain exactly the preceding characterization's rows, with byte equality across builds and flags. The source-only clean rebuild verifies all 730 prior class hashes. Reversing recorded instrumentation recovers every original native source byte; only Mind, Mind$1, TValueFactory and TValue native class bytes differ in the diagnostic runtime. Journals/readers are unchanged.

Every instrumented JVM attempts the same nine exports. All three root-clear/replacement cases refuse with `RECYCLED_ID`, each recording the child and grandchild alias. This includes the same-variable and other-existing-variable cases where both underlying journals accept. The previously empty-variable case still independently fails the full authority comparison. No trace artifact is created for any refused case. All six child-clear/pack controls export successfully, matching the preceding raw accepted trace byte for byte.

Each instrumented JVM also suppresses one real metadata callback and requires `FULL_AUTHORITY_MISMATCH`; injects one adapter capture error and requires `ADAPTER_FAILURE`; then requires a new session to export successfully after those refusals. A second finish without a session must return `NO_SESSION`. Every successful boundary export is tested for immutability. The consumer invokes both old and stream checks before returning its single accepted trace.

There are 27 recycled-identity refusals, 27 additional negative refusals, 54 ordinary exports and 9 fresh-session recovery exports. Independent replay checks all 63 accepted exports, covering 2,052 authority views and 4,752 serialized value entries. The matrix performs 5,184 native variable/context controls including the negative and recovery fixtures. `JOURNAL_DISAGREEMENT` is a defensive branch, not fault-injected or separately qualified here.

See [summary.json](tvalue-consumer-gate/summary.json), [build-validation.json](tvalue-consumer-gate/build-validation.json), [analyze.py](tvalue-consumer-gate/analyze.py), [build.py](tvalue-consumer-gate/build.py), [run.py](tvalue-consumer-gate/run.py) and [QualifiedJournalConsumer.java](tvalue-consumer-gate/QualifiedJournalConsumer.java). JVM commands, native rows, typed refusal diagnostics and accepted compressed traces are retained; the evidence manifest covers all deliverables.

## Limits

This qualifies a diagnostic export gate for the legacy DUMB memory fixture. It does not repair recycled identity in native clear, alter production/defaults, qualify SMART persistence or concurrent sessions, rerun the complete inference corpus, or establish a speedup. The observer registry follows the preceding single-threaded diagnostic adapter; it is not a concurrent production consumer. Full authority checking remains enabled. Previous full-corpus limitations remain in force.

The next step is to consolidate these isolated qualification stages into a short integration proposal with the supported boundaries, exclusions and remaining native clear decision explicit. Changing counter policy or descendant snapshot semantics remains a separate core change.
