# set_08_02: initial profile on 3.8

Base: develop/3.8.0 5a70f80c14a9d086faf2c2d177cd32f99d618e16.
Java 17, 512 MiB, TValue preservation ON, solve-sync OFF/ON. No production edits.
Set0802ProfileRunner calls the unmodified KangerTest.set_08_02 method directly.
Its elapsed interval includes workspace reset, four concurrent child workloads,
commits, the final query and formatting/output of 493 solutions and values.
Output is redirected to a file, not an interactive terminal. User/UDF setup is
outside the interval. This is not the full console command or persistent DB
wrapper. Absolute times should not be compared with Rick's machine.

## Fresh JVMs

Six sequential JVMs, mode order OFF/ON/ON/OFF/OFF/ON, one method invocation each.
Median OFF 2.130844016 s; ON 2.128265242 s. Overall range 1.9807–2.2898 s.
There is no convincing cold-run speedup in this small sample. All runs pass the
historical assertions and end with 493 solutions and 493 values. This does not
assert identical concurrent commit ordering or a complete semantic fingerprint.
The original test catches worker exceptions; logs were separately checked for
exceptions and the success marker. No worker exceptions were found.

## Diagnostic runs (not performance comparisons)

Two warmups and three samples per mode, 5 ms all-thread stack sampling and scan
counters enabled. These timings include profiler overhead and are not used as
speedup evidence. Main-thread reference scans are 244,035 OFF versus 1 ON in
each measured invocation. These ThreadLocal counters exclude worker threads;
zero counters in cold CSVs mean profiling disabled, not zero work.

Across three measured invocations per mode, inclusive stack observations were:

| Frame (RUNNABLE) | OFF | ON |
| --- | ---: | ---: |
| rotateVariables | 381 | 361 |
| linkDatabase | 186 | 178 |
| isValidFor | 54 | 49 |
| synchronizeSolveIndex | 14 | 1 |
| synchronizeSolveIndexReference | 11 | 1 |
| showResult | 3 | 2 |
| All scenario thread-stack observations, including waiting states | 849 | 844 |

Frames overlap: these are wall-stack observations, not additive CPU percentages.
Thread states are observed separately from stack capture. Sampling perturbs
execution and short paths can be missed. Nevertheless, synchronization occupies
few observations even in OFF mode, consistent with its small end-to-end impact.
The original broad suspicion about commit/printing cost is not supported as the
dominant cost by this sample; variable rotation and database matching warrant
the next focused investigation. CachedDomain.getCauses and TValue iteration
occur frequently underneath those paths.

Raw CSVs and stack summaries: set-08-02-evidence/. Full console dumps were used
for local error checks but are omitted from the commit. Reproduce from repo root
with org.kanger.test.Set0802ProfileRunner and an output CSV argument; use
bench.warmups, bench.samples, bench.sample and the existing optimization flags.
Do not use this measurement to enable either optimization by default.
