# Candidate groups from existing factory topology

`-Dkanger.experiment.factoryCandidates=true` replaces Linker's per-direction
Rule-tree scan with a snapshot of RuleFactory's existing predicate/polarity
metadata, restricted to the selected Rule IDs. It is optional and off by
default. The argument-mask and output-replay experiments are not enabled.

## Implementation boundary

RuleFactory.snapshotDomainRuleIds returns raw structural membership, without
hydrating Rules or applying logical-deletion/value filters. Linker already owns
the selected Rule objects and maps IDs back to those exact references, ordered
by their original ascending rank. It builds the groups once per pass and reverses
their lists for descending traversal. Both original rotators still execute.

Raw membership is intentional: adding a visibility/value filter at this stage
could suppress the original nonempty-bucket gate and thereby skip the
side-effecting findByResolvedDomain call. That call, its placement, and the
subsequent intersection are unchanged. Newly generated Rules outside the
pass's selected set cannot leak into the snapshot.

In factory-verify mode, each rotator independently builds the old reference
groups at its original entry boundary and compares signature sets, bucket sizes,
Rule identity and order. Thus the second direction also checks that sharing the
earlier snapshot did not miss a structural change. Verification deliberately
retains the old scans; timing uses factory mode without these checks.

## Qualification

* Full 123-case corpus passes with factoryCandidates and factory-verify.
* Lifecycle runner passes across publication, rollback/commit, deletion,
  reopen and replacement.
* Six fixtures / 16 operations match the original off-mode logical projection
  and action histograms byte for byte.
* Long-ID ascending/descending ordering and checkpoint-balance runners pass.

Raw completed-run outputs are in `latent-substitution-evidence/factory-candidates/`.
Local build uses ECJ targeting Java 8, execution uses OpenJDK 17. Full Maven and
canonical Java 8/21 gates remain outstanding.

## Timing observation

Three warmups, seven measured fresh-Mind iterations per case, separate JVMs,
without concurrent qualification. Median milliseconds:

| Case | Factory compile | Shared groups compile | Factory query | Shared groups query |
|---|---:|---:|---:|---:|
| natives | 99.725 | 95.387 | 110.751 | 111.315 |
| 100 singleton facts | 14.419 | 13.141 | 22.130 | 20.503 |

Rows, pair visits and unification counts match every corresponding sample.
The small run does not establish a statistically significant speedup. Native
query time is effectively unchanged; the singleton median is lower in this run.

This removes repeated tree discovery in the nonverify experimental path, but
still allocates pass-local maps/lists and scans metadata in visible factory
layers. In a large database with a small selected set, scanning all signature
buckets may be costly. Heap/allocation and that scaling case remain unmeasured.

Keep optional. The architectural reuse is qualified on the corpus, but there is
no basis to enable it by default as a performance improvement. Next useful
measurement is cost of runtime resolved selection, intersection and allocations
versus these group preparations, under larger sparse and dense active sets.

Reproduce with existing state/lifecycle/corpus runners, adding
`-Dkanger.experiment.factoryCandidates=true -Dkanger.experiment.latent=factory-verify`.
For timing, run LatentSubstitutionBenchmarkRunner with latent=factory and compare
factoryCandidates=false/true in separate JVMs.
