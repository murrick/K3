# TValue index preservation without latent indexing

2026-09-17. Measurement/qualification checkpoint on `59497b6`; no production changes.

## Isolation

All latent-index, candidate/intersection, occurrence reuse, solve-sync and other optimization flags are omitted. Only preserveTValueIndex=true is enabled; qualification additionally toggles verifyTValueIndex. The original Linker candidate traversal remains active. Thus this evaluates Escalera index maintenance independently of the original latent substitution experiment.

In both actual-preservation and independent-map verification modes:

- Existing 123-case corpus: PASS.
- 16-operation state output: byte-identical to old baseline.
- 13-stage lifecycle, including storage reopen/replacement: PASS.
- 20-operation nested-transaction state: byte-identical to old baseline.

TValueFactory still rolls back its additions index and restores action state after cache.release. The preservation guard changes only maintenance of coherent Escalera lookup metadata; no rollback of knowledge or context state is skipped.

## Standalone timing

100-edge, ten-predicate shared-path fixture. One fresh ON JVM followed by one OFF JVM, one warmup and three measured samples each. OpenJDK 17, 512 MiB heap. No profiling or verification; fingerprints after query timing.

| Mode | Query samples ms | Median ms |
| --- | --- | ---: |
| OFF | 14,600.192; 13,899.848; 13,432.168 | 13,899.848 |
| Preserve | 3,632.383; 3,656.267; 3,541.162 | 3,632.383 |

Approximately 3.83x faster in this standalone pair. This is not a standalone multi-JVM replication series or a universal percentage. It complements the earlier two large factory-mode pairs and three native pairs; comparisons across separate sessions/configurations should not be used to infer the incremental benefit of latent indexing.

All six fingerprints, row counts (201), domain visits (354,220) and unifications (318,210) match within this run. Domain visits remain higher than factory mode because no latent occurrence prefilter runs. The speedup therefore does not depend on removing candidate pairs.

Evidence: [standalone preservation](latent-substitution-evidence/tvalue-standalone/). Reproduce with LatentSubstitutionBenchmarkRunner, benchScaled=true, benchEdgeSizes=100, benchPartitions=10, benchWarmups=1, benchSamples=3, benchFingerprint=true and absolute benchOutput; toggle preserveTValueIndex, omit latent and all other switches. Qualification runners use preservation=true and verifyTValueIndex=false/true.

## Conclusion and remaining gate

Treat guarded TValue index preservation as a distinct candidate change discovered during the topology experiment. Do not attribute its speedup to the latent index or use it as evidence for removing Linker's bidirectional traversal. Keep its default off pending further qualification.

The experimental checkout still descends from historical `3ad50f1`; live develop was last reconciled at `7465493`, whose newer changes do not touch the studied inference/cache classes. No merge/rebase was performed. Full Maven and canonical Java 8/21 qualification remain outstanding; this environment still exposes neither mvn nor javac. Storage-specific mutation/materialization and parent/child shared-chain boundaries deserve focused coverage before proposing a production extraction.
