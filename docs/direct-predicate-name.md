# Direct String predicate name experiment

Base: develop/3.8.0 @ 1828d7164159cb04df00dedd5c27e18d556af4ea.
Flag: `kanger.experiment.directPredicateName`, default OFF. No develop change.

Predicate.getName reads its current value once and returns it directly if it is a String. All other values use the original concatenation, including custom toString returning null or throwing. No name cache or persistence change. String object identity may differ from the reference path; name contents remain equal.

## Evidence
Java 17, Xmx512m, all six integrated optimization flags ON. Six sequential fresh JVMs, OFF/ON/ON/OFF/OFF/ON, four queries each; warm medians exclude the first. Allocation is main-thread cumulative allocated bytes during optimizeHypothesis, not retained heap or exact allocation counts.

| Pair | OFF seconds | ON seconds | OFF MB | ON MB | Time reduction | Allocation reduction |
|---|---:|---:|---:|---:|---:|---:|
| 1 | 9.690 | 10.059 | 5498.8 | 4851.0 | -3.81% | 11.78% |
| 2 | 9.853 | 10.921 | 5396.8 | 4870.2 | -10.84% | 9.76% |
| 3 | 9.913 | 9.288 | 5349.5 | 4732.1 | 6.30% | 11.54% |

All 24 raw and optimized hypothesis snapshots match. Every run has 18 raw hypotheses, six final hypotheses, zero solutions and values. No benchmark stderr.

Boundary runner passed 24 checks in OFF and ON, including mutable names, hydration, null, nonstrings, callback counts and exceptions. Regression corpus and 20-operation transaction qualification passed both modes; transaction state files are byte-identical.

Allocation reduction is reproducible here; elapsed-time improvement is not. Do not claim a speedup or integrate on this evidence alone. Cross-JDK CI is configured for Java 8/21/26 and prior flags OFF/ON, with the new flag tested in both modes, including cross-context projection tests. CI results pending at checkpoint.

Raw evidence and reproducible drivers: predicate-name-evidence/.
