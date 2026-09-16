# Factory occurrence index: object graph and row construction

2026-09-16. Measurement-only runner; no production changes. Factory mode enabled, argument plans disabled. OpenJDK 17, 512 MiB maximum heap, ECJ Java 8 source target.

## What is counted

Instrumentation.getObjectSize measures each distinct object reachable from the actual root factory's `occurrences` map and `occurrenceJournals` stack. Identity deduplication prevents double counting; static fields and the owning factory are not traversed. The walker rejects unexpected object types, including Rule, Domain and Mind. Measurement precedes creation of diagnostic collection views.

This is an actual reachable object-graph footprint, **not a heap-dominator retained-size measurement**. Boxed Long IDs can be shared outside the index; a separate column removes all their sizes. Neither number is a formal retained-heap bound in the presence of shared transaction rows. These fixtures are measured after compilation at the root, not during an active nested transaction. Other pre-existing candidate indexes, lock objects, scalar fields on their owner, temporary construction garbage and whole-DB memory are excluded. The empty map/stack backing arrays cost 136 bytes and exist even when the experimental mode is off; do not present the full number as incremental mode overhead.

| Fixture | Rules | Buckets | Domain slots | Reachable bytes | Without boxed IDs | Objects |
| --- | ---: | ---: | ---: | ---: | ---: | ---: |
| Empty | 0 | 0 | 0 | 136 | 136 | 3 |
| Native | 128 | 156 | 167 | 41,616 | 38,544 | 1,112 |
| Dense 10 | 120 | 130 | 130 | 37,576 | 34,696 | 994 |
| Sparse 10 | 30 | 40 | 40 | 10,168 | 9,448 | 274 |
| Dense 30 | 960 | 990 | 990 | 295,144 | 272,104 | 7,774 |
| Sparse 30 | 90 | 120 | 120 | 29,944 | 27,784 | 814 |

All structural columns reproduced exactly in two sequential JVMs. Native footprint is 40.6 KiB including potentially shared boxes, or 37.6 KiB excluding them. Dense and sparse fixture sizes count input fact/rule pairs, not compiled rules. Dense compilation derives additional rules: comparisons must normalize against the resulting graph, not source length. Repeated domain slots preserve traversal order and need not equal unique domain count.

For perspective only, a hypothetical packed occurrence layout using 64-bit rule IDs, 32-bit rule bucket offsets, 64-bit predicate IDs, one polarity byte per bucket, 32-bit domain offsets and 64-bit domain IDs would have payload `12R + 13B + 8D + 8`: 4,908 bytes for native. This excludes array headers/alignment, lookup support, journals, updates and parent-layer handling. It is not implemented or qualified and is not the earlier full-pair CSR estimate.

## Construction micro-measurement

The runner invokes the existing private OccurrenceRows constructor reflectively for every represented Rule, assembling a new outer HashMap. Five warmups and 21 samples per fixture; below are each JVM's median milliseconds. Every rebuilt signature set and ordered domain-ID array is compared to the published factory rows outside timing, for all 26 builds. No constructor reimplementation participates in the measurement.

| Fixture | JVM 1 median ms | JVM 2 median ms |
| --- | ---: | ---: |
| Native | 0.110597 | 0.110817 |
| Dense 10 | 0.143866 | 0.084187 |
| Sparse 10 | 0.035093 | 0.022383 |
| Dense 30 | 0.244769 | 0.507534 |
| Sparse 30 | 0.017727 | 0.048774 |

Includes reflection, rule-tree traversal, row allocation, temporary collections and map insertion. Excludes compilation, storage IO/hydration, publication locks, journaling and rebuilding the other candidate indexes. Fixed fixture order, JIT and GC effects prevent treating these microtimes as scaling evidence or incremental compile/open latency. Native's small rebuild time is encouraging but does not establish end-to-end cost.

Initial larger-size attempts were discarded: one interrupted before producing a complete output; another overlapped that process. No timing or footprint from either is used here. Both final runs were sequential after those processes ended. Final direct CSV files are authoritative; process output may be truncated by the execution environment.

## Reproduction and remaining boundary

Compile `LatentFactoryFootprintRunner` with the existing qualification classpath. Package its class in an agent JAR whose manifest contains `Premain-Class: org.kanger.LatentFactoryFootprintRunner`. Run:

```sh
java -Xmx512m --add-opens java.base/java.util=ALL-UNNAMED \
  -javaagent:/absolute/path/factory-footprint-agent.jar -cp "$CP" \
  org.kanger.LatentFactoryFootprintRunner /absolute/path/footprint.csv
```

The module-opening flag applies to Java 9+; Java 8 does not accept it. The agent is qualification-only. Raw evidence is in [factory footprint](latent-substitution-evidence/factory-footprint/).

This closes the initial actual-representation graph accounting and constructor-cost questions for six fixtures. Exclusive retained heap with parent/child shared rows, incremental publication cost, storage-open cost and larger representative corpora remain unmeasured. No representation change or default enablement follows from this checkpoint.
