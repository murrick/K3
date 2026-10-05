# Integer-key probe forensics

## Decision

Repeated boxing in `Escalera.findCandidates` is real, but its maximum nominal allocation budget is modest. A bounded cache of immutable boxed keys is a justified next prototype; replacing the complete index with a primitive map is not yet justified. No production change, experiment flag, or speedup is introduced by this branch.

Baseline: `1c943d02d39bd33ebd91fabb7c4190112ab49459` (`develop/3.8.0`, verified live). The eight integrated optimizations remain enabled. This investigation does not include the preceding lazy-classification experiment.

## Measurement

Two sequential Java 17 JVMs, 512 MB heap, six son-query samples each. Each sample performs the ordinary query followed by `optimizeHypothesis`; counters are active only on the main thread during that separate optimization. Discard samples 0 and 1. Diagnostic source adds one observer immediately after the unchanged `owner.idsByHash.get(hash)` lookup. All production, qualification, and workflow files remain byte-identical to baseline.

Only the exact `Escalera` compact-candidate path is counted. Public `find`, custom-cache fallback, index insertion/removal, and other boxing sites are outside this scope. The observer records integer key frequencies, result cardinality, owner identity, and simulated direct-mapped key caches with 1, 4, 16, 64, or 256 slots per owner. All simulations start empty for each optimization, including owners that existed before the measured window. Slot selection is `(hash ^ (hash >>> 16)) & (capacity - 1)`. Every probe updates each simulation. No live candidate collection is cached or changed.

Instrumented timings and allocations are deliberately not interpreted: observation adds maps, arrays, boxing, and output. This is a count investigation, not an OFF/ON benchmark.

## Findings

Every warm sample has **3,884,221 probes**, **399 observed owners**, and **zero keys in the ordinary Integer cache range -128 through 127**. Thus each reference probe is outside that default cache range. Distinct numeric hashes vary from 19,962 to 20,133 as the workspace evolves. Empty results range from 1,934,579 to 1,939,801, singleton results from 1,899,091 to 1,904,323, and larger results from 45,043 to 45,329.

| Slots per owner | Reused keys per optimization | Share of probes | Nominal avoided Integer bytes |
| --- | ---: | ---: | ---: |
| 1 | 264,847 | 6.82% | 4.24 MB |
| 4 | 1,157,029–1,194,929 | 29.79–30.76% | 18.51–19.12 MB |
| 16 | 1,692,011–1,699,995 | 43.56–43.77% | 27.07–27.20 MB |
| 64 | 2,414,284–2,428,614 | 62.16–62.53% | 38.63–38.86 MB |
| 256 | 3,114,779–3,137,114 | 80.19–80.77% | 49.84–50.19 MB |

MB is decimal. Byte estimates assume a 16-byte `Integer`; object layout was not measured here. They also assume reference boxing actually materializes: escape analysis can eliminate some allocations, and a configured larger Integer cache changes the budget. All probes together represent a nominal 62.15 MB ceiling for this specific boxing site. Compared with the approximately 4.73–4.84 GB baseline allocation totals in the preceding clean benchmark, that ceiling is about 1.3%; the simulated caches save less, before their own overhead. These are estimates, not measured savings. The earlier warm JFR allocation sampling identified this site, but its sampled weights are not exact allocation counts.

Corresponding warm sample counters and full key frequencies agree exactly between the two JVMs. Counters across different sample numbers are not all identical; the table retains their range rather than claiming eight identical repetitions.

## Verification and boundaries

All 12 complete RAW and OPTIMIZED hypothesis snapshots match the archived clean oracle: 18 raw hypotheses, six optimized hypotheses, unknown logical result, zero solutions and values. Last Linker statistics match in every sample: passes 6, rule visits 1,292, rotations 6,824, domain pairs 13,281, unifications 3,080. Both stderr logs are empty. The analyzer reconciles result categories and full key-frequency totals, verifies eight warm observations, and compares corresponding sample pairs across JVMs.

This diagnostic does not qualify a production key cache, thread safety, storage reopen behavior, or a new primitive index. No new CI matrix is necessary for a docs-only branch; no new CI success is claimed.

## Next bounded candidate

Prototype reuse of boxed keys in the internal exact-cache lookup, with the original `Map<Integer, Set<Long>>`, all index mutations, and all candidate snapshots retained. Store only immutable `Integer` keys; do not cache candidate sets or absent lookup results. A single `Integer[]` slot, read into a local variable and checked by numeric value, avoids publishing a separately mutable primitive-key/value pair. Collisions must fall back to a newly boxed exact key. Concurrency still needs qualification; benign replacement must never produce a lookup for a different key. Keep the prototype default OFF, qualify signed/extreme hashes, collisions, index mutation/rebuild and ownership, and then compare clean/OFF/ON before any integration decision.

## Reproduce

From the repository root, with Java and `../tooling/ecj.jar` available:

```bash
python docs/hash-probe-forensics/build.py
python docs/hash-probe-forensics/run.py
python docs/hash-probe-forensics/analyze.py
git diff --exit-code 1c943d02d39bd33ebd91fabb7c4190112ab49459 -- kanger kanger-qualification .github
```

`build.py` restores the baseline Escalera source into a temporary build directory before inserting the observer. `run.py` retains complete stdout, including full key frequencies, as deterministic gzip files. The directory also contains the observer, derived runner, source list, instrumentation patch, expected snapshot oracle, JVM manifest, analyzer, and JSON/text summary.
