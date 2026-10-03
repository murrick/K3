# Compact snapshots integration qualification

Base develop/3.8.0: 3a2a6f0bccac11ac0d646178c4384ba73d7a5260.
Retains the isolated snapshot implementation and laptop/cloud evidence, changes
only the new flag default to true, and extends the existing defaults qualification
to all eight flags. No predicate-name prototype is included.

Local reproduction: run `bash docs/snapshot-integration-evidence/build-local.sh`
from the repository root (ECJ must be available at ../tooling/ecj.jar). It compiles
the production and eight qualification runners with Java 8 target compatibility,
then calls the existing defaults qualification with a direct classpath.
Local logs here compare three fresh-process modes: all properties absent, all
eight true, all eight false. Qualification timing is not used as a benchmark.

All six GitHub workflows passed on code commit
8e22b1ff5e854b92522b5b8a780ec1305b4b5a23: defaults qualification, resident
snapshots qualification, general CI, qualification isolation, server and
distribution. See ci-status.json. The defaults matrix passed on Java 8/21/26
with no parameters / all eight ON / all eight OFF. The snapshot matrix passed
on Java 8/21/26 with seven prior flags OFF/ON and snapshots OFF/ON.

The same three local modes passed all eight runners, six complete raw/final
hypothesis texts and identical transaction states. Results are in summary.json.
No performance benchmark was repeated because the snapshot algorithm is
unchanged; earlier measured ON behavior is now selected by an absent flag.

Integration is prepared in perf/3.8.0-compact-find-snapshots. Develop remains
on the base above. No merge, release, tag or deploy has been performed.
