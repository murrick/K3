# Compact snapshots integration qualification

Base develop/3.8.0: 3a2a6f0bccac11ac0d646178c4384ba73d7a5260.
Retains the isolated snapshot implementation and laptop/cloud evidence, changes
only the new flag default to true, and extends the existing defaults qualification
to all eight flags. No predicate-name prototype is included.

Local reproduction: compile the production sources with ECJ -1.8, then the eight
qualification runners (including SonProfileRunner and CompactFindSnapshotsRunner).
Run docs/optimization-defaults/qualify.py with --classpath and --output options.
Local logs here compare three fresh-process modes: all properties absent, all
eight true, all eight false. Qualification timing is not used as a benchmark.
