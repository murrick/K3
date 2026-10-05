# Deletion visibility chain forensics

## Decision

The hot deletion method performs a very large number of short traversals, principally for TVariables. This checkpoint measures that work and identifies a semantic constraint; it does not introduce an optimizer or establish removable CPU cost.

**Do not repeat the rejected empty-map shortcut, unconditionally cache a false deletion result, remove ancestor monitors, or drop the second deletion callback in `getTVariables`.** Public deletion/restoration maps remain authoritative, and repeated callback results can differ. A future reduction needs an explicit proof of stable visibility and callback eligibility, not only an observation that the son fixture has no matching deletion state.

Baseline: verified live `develop/3.8.0 @ 1c943d02d39bd33ebd91fabb7c4190112ab49459`. All eight integrated optimizations are ON. The preceding boxed-key and lazy-classification prototypes are absent. All production, qualification, and workflow files remain byte-identical to baseline.

## Measurement

Two sequential fresh Java 17 JVMs, 512 MB heap, six ordinary son-query samples each. Counters are enabled only on the main thread during the separate `optimizeHypothesis` invocation. Discard the first two samples in each JVM.

The build script restores baseline Mind into a temporary source directory. Instrumentation counts visited layers after the original restoration-map lookup, counts the original deletion-map lookup, and records completed calls at their existing return sites. Original unit type/ID reads, monitor acquisition/release, restoration-first precedence, set membership calls, and `getNext` traversal remain. The observer invokes no additional semantic getters or collection callbacks. It records requested-type set presence, not `Map.isEmpty` or set size.

The depth histogram records the number of visited layers per completed call. Bucket 63 is overflow; maximum observed depth is four. All observed sample calls complete normally; exceptional production calls are not claimed to be counted by this return-site instrumentation. Diagnostic timings include observer work and are not performance evidence.

## Findings

All **eight warm samples have exactly identical counters and depth histograms**:

- **17,573,710** completed `isUnitDeleted` calls per optimization.
- **46,897,159** visited layers and original monitor entries.
- **29,323,449** ancestor-layer visits beyond the first layer, 62.53% of visits.
- Average visited depth **2.669**, maximum **4**.
- **46,897,159** restoration-map lookups and the same number of deletion-map lookups: **93,794,318** keyed map probes in total.
- Every requested-type lookup returns a null set: no membership checks and no restoration/deletion hits are observed. This does not prove all maps are globally empty or that other workloads never use deletion markers.

| Visited layers per call | Calls | Share |
| --- | ---: | ---: |
| 1 | 96,784 | 0.551% |
| 2 | 5,646,911 | 32.133% |
| 3 | 11,813,507 | 67.223% |
| 4 | 16,508 | 0.094% |

| Unit type | Calls | Visited layers |
| --- | ---: | ---: |
| TVARIABLE | 15,203,598 | 40,629,530 |
| RULE | 2,309,593 | 6,137,467 |
| TVALUE | 45,665 | 112,157 |
| DOMAIN | 7,632 | 7,632 |
| TERM | 5,649 | 8,800 |
| COMMENT | 816 | 816 |
| PREDICATE | 757 | 757 |

TVariables account for **86.51% of calls / 86.64% of layer visits**. The earlier warm JFR attributed approximately 18–19% of selected CPU samples to this method. That attribution is statistical and is not an exact amount of CPU that a hypothetical shortcut can recover. This investigation does not assign exact caller counts, measure lock contention, identify distinct unit IDs, or establish that the same object is queried consecutively.

## Why an apparently duplicate read is not generally removable

`ArgumentsList.getTVariables` evaluates `!a.isDeleted(mind)` twice in sequence for a TVariable. `Argument.isDeleted` delegates to its held object; an exact built-in Argument can hold a custom TVariable. An Argument class guard alone therefore does not establish callback purity.

`RepeatedDeletionWitness` uses an exact Argument containing a custom TVariable and compares the reference collector with a deliberately hypothetical one-check collector, limited to that single-variable case:

| Scenario | Reference two-check collector | Hypothetical one-check collector |
| --- | --- | --- |
| First callback false, second true | Two callbacks; variable excluded | One callback; variable included |
| Second callback throws | Original exception propagates after two callbacks | One callback; variable included; exception bypassed |

Both scenarios pass **eight assertions**, including exception identity. This is a witness against an unconditional shortcut, not a proposed alternate production collector or proof that an exact-TVariable-only specialization is impossible. Additional guards would need to account for mutable IDs, exposed map/set mutations, custom context traversal, and concurrent visibility changes before a second observation can be eliminated.

The archived visibility boundary runner is also rerun on the instrumented baseline: **27 checks** cover direct public-map insertion/removal/clear, parent deletion, nearer restoration, both markers in one layer, null/empty sets, unrelated IDs, and `setUnitDeleted`. It confirms that a previously false result can become true via authoritative public mutation and that restoration precedence is meaningful. No new API restriction is introduced.

## Validation and scope

All 12 complete RAW/OPTIMIZED hypothesis snapshots match the clean oracle: 18 raw hypotheses, six final hypotheses, unknown logical result, zero solutions and values. Last Linker statistics match in every sample: passes 6, rule visits 1,292, rotations 6,824, domain pairs 13,281, unifications 3,080. Both profile stderr files and both witness stderr files are empty.

The analyzer reconciles completed calls with outcomes and histogram counts, visited layers with weighted histogram depths, deletion probes with restoration early exits, and corresponding sample pairs across JVMs. It also asserts all eight warm counter groups agree. Full stdout is retained as deterministic gzip files.

This is a docs-only diagnostic checkpoint with unchanged reactor code. No new cross-JDK CI matrix, production flag, default activation, merge, or speedup is claimed. The older empty-deletion-layer timing result remains negative and is not overturned by these counts.

## Reproduce

From the repository root, with Java and `../tooling/ecj.jar` available:

```bash
python docs/deletion-chain-forensics/build.py
python docs/deletion-chain-forensics/run.py
python docs/deletion-chain-forensics/analyze.py
git diff --exit-code 1c943d02d39bd33ebd91fabb7c4190112ab49459 -- kanger kanger-qualification .github
```

The evidence directory contains the observer, derived runner, both witnesses, build/run/analyzer scripts, instrumentation patch, source manifest, expected snapshot oracle, JVM manifest, full logs and JSON/text summaries.
