# Compact current stamp experiment

Base `develop/3.8.0 @ 1c943d02d39bd33ebd91fabb7c4190112ab49459`, live checked 2026-10-04. `kanger.experiment.compactCurrentStamp` is default OFF. All eight integrated optimizations retain their defaults.

For an exact ArgumentsList, equalsStamp gathers a fresh ITerm array instead of a temporary ArrayList and its growth backing storage. Full getTVariables enumeration, both deletion checks, all variable isEmpty/getValue reads and their order precede target-list size and term comparisons. It retains term object references (no ID/value memoization) and the existing ParametersIncompleteException catch. Subclasses use the original virtual getStamp path. Public getStamp remains an owned mutable ArrayList. No state is reused across comparisons, no persistence or knowledge representation changes.

Focused qualification compares an independently copied old equalsStamp body with fresh identical fixtures, complete ordered callback traces, result and original exception identity. Nineteen scenarios cover empty/single/multiple/mismatched lists, value reads before early mismatch, incomplete variables, empty/null terms, custom lists and stamp/enumeration overrides, later callbacks mutating earlier terms and argument structure, target callbacks mutating later comparisons, deleted variables and null targets. Additional built-in binding/deletion and public snapshot ownership checks. Marker: COMPACT_CURRENT_STAMP_OK scenarios=19 checks=47.

## Qualification

Local Java 17: focused 19 scenarios / 47 checks, compact-find 23, resident-base 48, single-lookup 15, database reopen, regression corpus, 20 transaction operations and three concurrency iterations pass OFF/ON with the eight defaults ON. All stderr files are empty; transaction states are byte-identical.

All five workflows pass on production checkpoint `249f3ac`: general CI, distribution bundle, server, qualification isolation and [specialized current-stamp qualification](https://github.com/murrick/K3/actions/runs/37213375043). The specialized six jobs cover Java 8/21/26 × prior eight OFF/ON, with experiment OFF/ON in every job, including projection and concurrency. CI status is saved beside the report.

## Controlled measurement

Six fresh sequential JVMs: clean / OFF / ON / ON / OFF / clean. Java 17.0.20, 512 MB, all eight integrated flags explicitly ON, six samples per JVM; median of last four after discarding first two. No diagnostic/JFR counters. New clean reference compiled from exact base production; byte comparison across reference classes confirms only ArgumentsList differs, including identical benchmark runner bytecode.

| Run | Wall s | Main CPU s | Main allocated MB |
|---|---:|---:|---:|
| Clean 1 | 4.525 | 4.443 | 4766.6 |
| OFF 2 | 4.358 | 4.259 | 4714.5 |
| ON 3 | 4.566 | 4.481 | 4754.8 |
| ON 4 | 4.527 | 4.442 | 4659.8 |
| OFF 5 | 4.491 | 4.404 | 4803.7 |
| Clean 6 | 4.549 | 4.468 | 4674.3 |

ON vs clean: time 0.91% slower forward / 0.47% faster reverse; CPU 0.86% slower / 0.57% faster. Main-thread allocated bytes 0.25% / 0.31% lower. Against same-build OFF: ON is 4.78% / 0.80% slower, allocation changes are inconsistent (-0.86% reduction forward, +3.00% reverse). OFF is itself faster than clean by 3.70% / 1.26%; do not attribute that difference to enabling the snapshot. This batch demonstrates no useful production improvement, not a statistically established permanent regression.

All 36 complete raw and optimized text sets match (18 raw, six optimized, no solutions or values in this hypothesis workload). All 36 POST_OPTIMIZE_LAST_LINKER_STATS records match too; those are last-Linker records, not a complete globally accumulated candidate/collision trace. Corpus and focused runners supply additional semantic checks. Allocation is optimization-thread allocated bytes, not retained heap or whole-process memory.

## Decision and reproduction

Reject this prototype for integration. Keep the flag default OFF on the experiment branch; develop remains unchanged. No laptop run, merge or default enable is requested. The result does not prove every compact representation is pointless, but gives no reason to tune this replacement further without new evidence. Return to Domain.setMind frequency / caller structure; this local replacement retains the millions of enumeration/deletion reads.

From repository root: `bash docs/compact-current-stamp/build-local.sh`, `python docs/compact-current-stamp/qualify.py`, `python docs/compact-current-stamp/build-reference.py`, `python docs/compact-current-stamp/run-benchmark.py`, `python docs/compact-current-stamp/analyze.py`. ECJ jar is external. Logs, scripts, source/class hashes and CI status are alongside this report.
