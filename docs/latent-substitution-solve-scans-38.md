# Repeated solve-index scans after TValue preservation

2026-09-17, based on `4f1476d` / develop baseline `9479cd4`.
Diagnostic only: `kanger.experiment.profileSolveScans=true`. It counts calls,
visited map groups, newly processed list slots, completed calls with no new
slots, and all completed calls. It neither skips nor reorders any operation.
Thread-local numeric counters do not retain Mind/Rule/TSolve references.
Benchmark snapshots bracket only the query, so all nested Linker invocations
are included. Counter snapshots are outside the measured interval.

Java 17 / ECJ Java 8 source target, 512 MiB heap, TValue preservation ON.
One warmup and three measured samples per fixture; separate large/small JVMs.
Every sample within each fixture gives exactly the same counters:

| Fixture | Calls | Group visits | New list slots | No-new-slot calls | Calls processing new slots |
| --- | ---: | ---: | ---: | ---: | ---: |
| 100 edges / 10 predicates | 1,119,114 | 23,127,334 | 4,200 | 1,118,910 | 204 |
| Native values | 9,200 | 243,823 | 362 | 9,169 | 31 |
| 100 singleton facts | 300 | 300 | 100 | 299 | 1 |

All calls completed. No-new-slot fractions are 99.982%, 99.663%, 99.667%.
New slots count processing attempts, not unique newly created TSolve objects:
indexSolve still applies its own identity deduplication. A no-new-slot call still
performs existing map reads/writes. These counts do not prove that arbitrary
calls may be skipped, and do not measure the expected speedup.

All nine semantic SHA-256 values match their corresponding prior uninstrumented
large ON or sampled-small baseline; rows, domain pairs and unifications remain
the same. Instrumented timings are recorded for completeness, not used to claim
a performance gain. No new optimization is enabled by this checkpoint.

## Mutation boundary audit

Core references to getRuleSolves are confined to Mind and Linker. Mind.addTSolve
appends a fresh tuple only after duplicate lookup. Mind.clearMind clears the map;
Linker clears it at invocation initialization, together with its private index.
The next prototype can version these publication/clear points and remember the
last synchronized version, resetting it whenever the private index is cleared.

However, public getRuleSolves returns the actual mutable map and mutable lists.
Any exposure must conservatively disable version-based skipping for that Mind;
internal access needs a separate package-private route. Mind subclasses also
require fallback because overridden access/publication can escape accounting.
TSolve.getSolve itself exposes mutable tuple content; the old synchronizer does
not reindex existing slots after such changes. A new path must preserve the old
contract rather than silently claim stronger mutation tracking.

The archived prototype used this exposure fallback. It is a reference for the
next implementation, not authority to transplant all experimental modifications.
Next: minimal default-off version guard, reference execution on proposed skips,
then state/transaction qualification and unprofiled ON/OFF comparison with TValue
held ON. Full bidirectional traversal and latent topology remain unchanged.

Evidence: `latent-substitution-evidence/solve-scans-38/`. Use the preceding
post-TValue benchmark command with profileSolveScans=true, sampleQuery omitted,
benchWarmups=1, benchSamples=3. For the large fixture use benchScaled=true,
benchEdgeSizes=100 and benchPartitions=10; omit those for native/singleton facts.
All these options have the `kanger.experiment.` prefix.
