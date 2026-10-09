# Ordered exact validation experiment

## Decision

An ordered comparison of private journal views preserves exact results, but this experiment does not establish an improvement in complete observation time. The candidate remains experimental and is not selected as the next diagnostic baseline. Continue from docs/tvalue-reporting/StreamReportJournal.java with the ordinary Map.equals check.

Across the 18 size/dirty/flag cells, whole-observation ratios range from 0.941 to 1.033; individual JVM medians range from 0.907 to 1.068. The equality phase is somewhat cheaper, while whole time remains mixed. Allocation is effectively unchanged and slightly higher because the candidate iterates both maps. The small local saving does not justify treating this additional ordering-dependent helper as an accepted acceleration.

## Change and assumptions

The preceding journal compares its candidate and fresh authority with next.equals(authority). AbstractMap equality visits entries and looks up each corresponding key in the other map. The candidate uses size equality followed by two simultaneous entry iterators, comparing every corresponding key and complete value. There are no hashes, sampling, dirty-key restriction, repair, partial value comparisons or skipped authority reads.

Both actual maps are private, natural-order TreeMaps. This private invariant permits ordered comparison, but the helper is not a general order-independent replacement for Map.equals: arbitrary caller comparators, reversed ordering and concurrent modifications are outside its scope. Source recovery proves that only this equality call and its private helper differ from the preceding journal after reversing class renames. Projection, full authority, reporting, errors, resets, routes and runtime hooks remain unchanged.

## Validation

57 final JVMs passed with empty stderr and no preliminary fixture failures: 54 timing and three scope runs. There are 3,456 measured paired updates, 114 successful traces and 7,188 replayed authority views. Every timing trace is byte-identical to the preceding reporting-stage trace for that label. Both scope traces in each pair are identical.

Each scope JVM checks the inherited 1,028 natural-order map pairs and adds equality checks in both directions, separately allocated equal copies, changed final values and the same-size/different-key case. Empty, interleaved, extreme-long-key and nullable-value controls agree with ordinary Map equality within the admitted natural-order scope. Input maps remain unchanged. The native transition fixture still covers empty baseline, insertion, unchanged observation, term rewrite, context deletion/restoration, removal of all keys, subsequent insertion and explicit reset.

The native clear transition is an explicit comparison model: actual clear followed by invalidation of known routes; it does not qualify a native clear hook. Nine negative scenarios across the three scope JVMs omit notifications after actual setter/add/clear operations. Both journals reject every gap with identical full error messages, preserving mismatch detection. Before/after native fingerprints remain equal around each observer.

## Cost protocol and results

The inherited runner is unchanged except class names. Two independent journals share a clean native-memory state with one root, 32/128/512 variables and one value each. One or eight existing values change per update. Each of 54 sequential JVMs uses 48 warmup and 64 measured paired updates, alternating reference-first/candidate-first order, over three repetitions and on/off/verify. Both full authority reads and exact validation remain enabled.

Real native setters are checked and followed by identical explicit metadata notifications before timing. This is the established pairing bridge, not native hook or engine integration. All timings include complete observe and profile append, while setter/bridge, fingerprints, fixture construction, baseline/finish wall time, file output and replay are outside the intervals.

The table takes medians across the three flag cells. Lower ratios favor the candidate.

| Variables | Dirty buckets | Ordered / previous whole time | Ordered / previous equality time | Ordered / previous allocation |
|---|---|---:|---:|---:|
| 32 | 1 | 1.033 | 0.803 | 1.000362 |
| 32 | 8 | 0.980 | 0.765 | 1.000300 |
| 128 | 1 | 0.992 | 0.907 | 1.000102 |
| 128 | 8 | 0.987 | 0.881 | 1.000093 |
| 512 | 1 | 1.015 | 0.924 | 1.000025 |
| 512 | 8 | 0.994 | 0.935 | 1.000024 |

Each JVM ratio is the median of 64 paired sample ratios; each flag cell is the median of three JVM ratios. The compact table aggregates flag-cell medians. Duration medians and paired ratios are calculated separately. Full cells, raw wall/allocation/phase samples, order-separated and first/second-half results are retained. The unchanged oracle's cell ratios span 0.946–1.070, illustrating variation on the scale of the whole-time result.

This finite-warmup, fixed-order JVM benchmark includes JIT/GC effects and shared allocation pressure. It is not stabilized JMH or application throughput. ThreadMXBean allocation measures allocated bytes, not retained heap or RSS. No end-to-end inference, hook-integrated, persistent, child or concurrent performance claim is qualified. Complete corpus semantics remain unqualified; previous rejected attempts remain retained in parent history.

## Evidence

Declared edits and source recovery, complete commands, classes/source hashes, all traces and errors, raw samples and analyses are under docs/tvalue-ordered-validation/. Parent: 24770ae402c41ea43dce0cc7309245dc9396fdfd. Branch: experiment/3.8.0-tvalue-ordered-validation. Production sources, defaults and develop are unchanged.
