# TValue checkpoint release: guarded index preservation

2026-09-16. Default-off `kanger.experiment.preserveTValueIndex=true`; independent verification via `kanger.experiment.verifyTValueIndex=true`.

## Guard and mutation audit

Preservation applies only to schema tvalues, when the live index is valid, indexedRoot is the current root, the restored checkpoint root is the same object, and no tracked cache mutation occurred since mark. A monotonically increasing counter is captured in each checkpoint. add, delete, deleteAll, clear, setRoot and update advance it conservatively, including no-op calls. A fallback release also advances it; commit and preserved no-change release do not. Other schemas and changed-root/already-invalid/mutated cases retain invalidation.

Existing Escalera mutators either maintain derived maps or invalidate them. TValue canonical hash/equality use stable variable/value IDs; logical deletion is separate and lookup intentionally includes deleted objects for resurrection. Materialization update clears checkpoints. The guard avoids relying on root equality alone and rejects even explicit setRoot(sameRoot). Raw externally modified IStep chains, arbitrary identity mutation and unsupported concurrent parent changes are not newly guaranteed by this experiment.

The verifier walks the root chain into separate memory-ID, persistent-ID, hash-bucket and predecessor maps, comparing every collection and memory-step identity. It does not replace or repair live maps. Failure throws AssertionError. No semantic object hydration, persistence change, new index representation or manager is introduced.

## Qualification

With preservation enabled, both actual preservation and independent verification passed:

- Existing 123-case corpus (factory-verify domain oracle active).
- 16-operation state output byte-identical to old baseline.
- 13-stage lifecycle including storage close/reopen/replacement.
- 20-operation nested transaction output byte-identical to old baseline (latent flags omitted for these runs).

TValueIndexPreservationRunner separately checks empty and nested no-change checkpoints; nested commit; changed-root rollback visibility; same-root explicit mutation fallback; already-invalid fallback; deleted TValue lookup and resurrection identity; and other-schema fallback. Its verification run deliberately corrupts a live hash map and asserts that the independent reconstruction detects the fault without repairing it. All checks passed.

Canonical Maven/Java 8/21 qualification remains outstanding. Corpus equality is not a universal proof for arbitrary extensions; default enablement is not proposed.

## First unprofiled timing pair

Ten-predicate shared-path workload, 100 input edges. OpenJDK 17, 512 MiB heap, factory occurrence mode in both runs, other optimizations off. One fresh JVM OFF followed by one ON; each has one warmup and three measured samples. No internal timers, rebuild profiler or verification enabled; semantic fingerprints computed after query timing.

| Mode | Query samples ms | Median ms |
| --- | --- | ---: |
| OFF | 11,379.076; 11,263.280; 11,411.380 | 11,379.076 |
| Preserve | 3,873.262; 3,797.895; 3,838.471 | 3,838.471 |

About 2.96x faster, or 66.3% lower query time **in this first paired run**. All six fingerprints, row counts (201), domain visits and unifications (318,210) match. The magnitude is encouraging, but multiple fresh JVMs with reversed order and other workloads are needed before calling it a stable/general improvement.

A separate profiling-enabled ON sample recorded one rebuild and 800 walked steps, versus the preceding OFF diagnostic's 35,088 rebuilds and 131,521,122 steps. All 278,088 releases saw the same root with a coherent valid index; zero already-invalid releases and zero rebuilds following release. The one initial rebuild took 0.406 ms in that sample. Its query time is excluded from the unprofiled comparison. This supports the identified mechanism rather than attributing the speed difference only from wall time.

Evidence: [preservation](latent-substitution-evidence/tvalue-preservation/). Use the partitioned benchmark command with benchEdgeSizes=100, benchPartitions=10, benchWarmups=1, benchSamples=3, latent=factory and benchFingerprint=true; toggle preserveTValueIndex. Keep verifyTValueIndex and all profilers off for timing. Qualification uses the same preservation toggle with verification false/true. Focused runner sets preservation itself.

Next: replicate timings with reversed order and native/small-fact fixtures, then extend mutation-boundary checks as needed. No merge, default promotion or removal of reference invalidation is authorized by this checkpoint.
