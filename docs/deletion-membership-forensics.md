# Deletion traversal membership forensics

Live base `develop/3.8.0 @ 1c943d02d39bd33ebd91fabb7c4190112ab49459`, checked 2026-10-04. Branch `experiment/3.8.0-deletion-membership-forensics`. Production is unchanged. All eight integrated optimizations use ON defaults; the weak-reference reuse prototype is absent.

## Question and scope

The post-eight sampled profile places about 19–20% of warm main-thread observations inside Mind.isUnitDeleted. Before proposing membership or boxed-ID reuse, count whether this workload actually enters Set.contains, how many parent levels it walks, and whether state maps are empty. These are exact instrumented operation counts, not CPU percentages or timing results.

A diagnostic copy of Mind is compiled outside the production source tree. The tracked instrumentation patch adds counters at the existing traversal sites. Original unit getter order, restored-before-deleted contains calls, lock scopes and parent callbacks remain in place. Counter observation reads isEmpty on the existing private final HashMaps and getClass on a non-null Set; it does not add contains callbacks or parent traversal. Counters target the main optimization thread only and reset immediately before optimizeHypothesis; compiler/query/text rendering work is outside the counted region. Public callbacks can still exist; no concurrency speed or scheduling equivalence is inferred from an instrumented JVM.

Two fresh sequential JVMs × six son(John,x) samples; first two samples per JVM excluded from warm summary. All eight warm count vectors and depth histograms are identical. The 12 raw/optimized hypothesis texts match one another and the earlier clean-base reference: 18 raw, six optimized, zero solutions/values in this hypothesis workload. Stderr is empty. Instrumented elapsed times printed by the reused runner are deliberately excluded from analysis.

## Result per warm optimization

| Operation | Count |
|---|---:|
| isUnitDeleted calls | 17,573,710 |
| Context levels visited | 46,897,159 |
| Restored map lookup sites | 46,897,159 |
| Deleted map lookup sites | 46,897,159 |
| Empty restored/deleted maps | 100% / 100% |
| Set.contains calls | 0 |
| Visible restored/deleted hits | 0 / 0 |
| Repeat built-in ID boxing opportunities | 0 |

Average traversal depth 2.668598. Depth distribution: 96,784 calls visit one level, 5,646,911 visit two, 11,813,507 visit three, 16,508 visit four. p50/p95/p99 = 3; max = 4. All calls exhaust the chain and return false. This is workload-specific: it says nothing about corpus deletion/collision paths or other contexts.

## Decision

Membership-set lookup and reusing a boxed ID cannot accelerate this workload: neither operation is executed. The expensive route is millions of empty traversals, including locks, map lookup sites and parent walking. Counts do not separate the cost of those components.

Do not infer that deleting checks, memoizing false, or skipping locks is safe. The previous duplicate-check forensic witnesses show parent/collection callbacks can change the result between reads; public state maps can be modified directly. The earlier empty-layer shortcut was measured and rejected, so this counter result alone does not justify reviving it. No production fast path, runtime flag or semantic change is introduced.

Next bounded lead: measure how many ArgumentsList.getTVariables traversals are requested by Domain.setMind and stamp generation, and whether a caller-level reduction can preserve both deletion reads, callback order and mutable overlay visibility. A cache would require a proved observation boundary and invalidation rules; this report does not establish one.

## Reproduction

From repository root, with external `../tooling/ecj.jar` and Java 17:

```sh
python docs/deletion-membership-forensics/instrument.py
python docs/deletion-membership-forensics/run.py
python docs/deletion-membership-forensics/analyze.py
```

ECJ targets Java 8. All modified production source goes into `../build/deletion-membership-source`, classes into `../build/deletion-membership-classes`; tracked production files remain byte-identical to the base. Full patch, runner, counter schema, logs, hashes and summary are alongside this report. There is no executable optimization to qualify with general CI at this diagnostic checkpoint.
