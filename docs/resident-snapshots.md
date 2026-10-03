# Isolated compact candidate snapshots

Base: develop/3.8.0 at 3a2a6f0bccac11ac0d646178c4384ba73d7a5260.

This experiment contains only compactFindSnapshots (default OFF). Predicate.java is byte-identical to the base; no directPredicateName code is included. Public ICache.find ownership remains unchanged. Internal factory iteration uses immutable empty/singleton snapshots for exact Escalera instances and the original HashSet copy for multiple candidates. Custom caches retain their original find calls.

Build locally with `bash docs/resident-snapshots-evidence/build-local.sh`, then `python docs/resident-snapshots-evidence/qualify.py`. The benchmark runs six fresh JVMs in reference / OFF / ON / ON / OFF / reference order, six samples per JVM, discarding the first two. Reference classes must be compiled from the base into ../build/defaults-classes. All seven integrated optimizations are explicitly ON. No diagnostic counters or profilers are enabled.

Timing, allocation, semantic equality and CI results will be appended after measurement. No default enable or merge is requested at this stage.
