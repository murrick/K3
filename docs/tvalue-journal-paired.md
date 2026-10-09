# Paired complete journal observation

## Outcome

The preceding direct-bucket comparison isolated Layer reuse. This stage compares complete old and memoized journal observations within each JVM, including candidate projection, fresh full authority, equality validation, trace reporting and phase-profile append. Neither full oracle is disabled or used to repair the candidate.

Across the 18 size/dirty/flag cells, median memo/old wall ratios are 0.780–0.872 for one changed bucket (13–22% less time) and 0.196–0.332 for eight (67–80% less). Individual JVM medians remain below one: 0.743–0.920 and 0.173–0.334 respectively. Thread allocation ratios are about 0.805–0.815 for one changed bucket and 0.218–0.257 for eight. These are complete observation costs on this explicit memory fixture, not inference throughput.

## Setup and correctness

54 sequential JVMs: 32/128/512 variables, one value per variable, one or eight existing values changed per update, three repetitions and on/off/verify. Each has 48 warmup paired updates and 64 measured pairs with alternating old-first/memo-first order. Native indexes are initialized and there is one root, no child, no I/O and no concurrent mutation or benchmark JVM.

Two independent journal classes/sessions share the same quiescent native state. Their sources are exact copies of the prior profiled journal and its observation-scoped-memo successor, with only TValueDirtyJournal renamed to PriorLayerJournal or MemoLayerJournal. Inverse rename and source hashes verify both copies. Each real native setValue is checked and followed by identical explicit metadata notifications to both sessions, before either timed observation. The native clean runtime has no journal setter hooks. This explicit bridge is intentional: it permits pairing complete journals without changing native classes. Hook integration, transaction boundaries, persistent storage and engine execution are not requalified by this fixture.

There are 3,456 measured paired updates. Every observer preserves the native fingerprint. Both sessions perform baseline, 64 update observations and session-end, yielding 108 successful traces and 7,128 independently replayed authority views. Complete traces are byte-identical within every pair, including touches, changes, counts, order and full views. Each session reads only the selected dirty buckets after its single baseline. Final native views agree across flags, repetitions and dirty counts. All JVMs passed with empty stderr; there were no preliminary fixture failures.

The inherited clean runtime class hashes, exact journal source copies, overlay classes and source paths are recorded in build-validation.json and copies.json. Production sources, defaults and develop are unchanged. All work is a docs-only diagnostic experiment.

## Where the cost remains

This compact table takes medians across the three flag cells. Full cell/JVM metrics, order-separated and first/second-half ratios, raw wall/allocation samples and all phase rows are retained.

| Variables | Changed buckets | Memo / old whole time | Memo / old allocation | Memo projection, µs | Full oracle, µs | Reporting, µs |
|---|---|---:|---:|---:|---:|---:|
| 32 | 1 | 0.807 | 0.815 | 54.7 | 82.2 | 31.4 |
| 32 | 8 | 0.266 | 0.255 | 55.9 | 53.6 | 33.7 |
| 128 | 1 | 0.838 | 0.809 | 110.9 | 161.9 | 90.5 |
| 128 | 8 | 0.222 | 0.223 | 121.6 | 153.5 | 99.1 |
| 512 | 1 | 0.870 | 0.805 | 232.0 | 563.9 | 400.6 |
| 512 | 8 | 0.321 | 0.218 | 200.1 | 403.2 | 397.4 |

Equality validation is also timed and included in whole time; it is in the full CSVs. For 512 variables, the memo observer spends about 200–232 µs on projection, 403–564 µs on the oracle and 397–401 µs on reporting in these aggregated cases. Candidate projection still copies the full TreeMap and extracts one complete qualified Layer. Reporting compares all keys and serializes a full VIEW. Therefore a cheaper bucket algorithm alone cannot remove the remaining O(N) work or all diagnostic costs.

Each JVM wall/phase/allocation ratio is the median of 64 paired ratios; each cell takes the median of three JVM ratios. Aggregated durations are separate medians and need not reproduce paired ratios exactly. The shared unchanged oracle's cell ratios range from 0.915 to 1.068, illustrating measurement variation. Both order groups independently show a median win in every cell. Timing remains a fixed-order JVM microbenchmark with GC/JIT effects, shared allocation pressure, finite warmup and serial data. It is not a stabilized JMH or application-level result.

ThreadMXBean allocation measures allocated bytes, not retained heap/RSS. Wall/allocation intervals include the whole observe call and profile-string append. Native setter, explicit metadata notifications, fingerprints, fixture construction, baseline and finish wall time, file I/O and trace replay are outside those intervals. Baseline/end phase rows are retained as evidence but excluded from steady-update aggregates.

The result qualifies this full diagnostic observation path for initialized, sparse native-memory updates. It does not qualify total session or native hook costs, child contexts, persistence, unsupported identity changes, concurrent observation, engine throughput or a production optimization default. Complete corpus semantics remain unqualified; earlier rejected attempts remain retained in the parent history.

## Evidence

All commands, logs, phase and allocation samples, both traces, analysis and hashes are under docs/tvalue-journal-paired/. Parent: ada5483103193fb646e87ae1c456ed043ee5dd8f. Branch: experiment/3.8.0-tvalue-journal-paired.
