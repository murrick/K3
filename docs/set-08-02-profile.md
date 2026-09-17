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

## Rotation frontier counters

Follow-up diagnostics retain the existing algorithm and count isValidFor returns
by suffix width. Flag profileRotations is default OFF. Counters are ThreadLocal;
the runner reports only its main thread, not the four publication workers.
OFF/ON each ran one warmup and three samples, with TValue preservation ON.
All six measured invocations give exactly:

| Assigned suffix width | Attempts | Rejected | Accepted |
| --- | ---: | ---: | ---: |
| 1 | 1,479 | 0 | 1,479 |
| 2 | 242,556 | 241,077 | 1,479 |
| 3 or more | 0 | 0 | 0 |

99.39% of width-two candidates are rejected. 242,556 equals 492 x 493, but
the counters alone do not establish the per-rule or per-pass distribution.
Five final Linker snapshots have 2 passes, 2,964 rule visits, 4,440 terminal
rotations and 4,440 database evaluations. OFF sample 2 has 2,962 rule visits
and 4,438 rotations/database evaluations. All six have 3,944 domain pairs and
unifications and 493 final solutions/values. The concurrent scenario therefore
does not establish identical internal work across modes; the per-width attempt
and rejection counts do match exactly. Reference synchronizations
remain 244,035 OFF versus 1 ON. Timings here include diagnostic overhead and are
not new performance evidence. No worker errors appeared in the captured logs.

This identifies a better target than another synchronization optimization:
avoid hydrating/binding most incompatible inner TValue candidates. Current
TValueFactory.forEach snapshots IDs in insertion order, hydrates each TValue,
sets current, then isValidFor rejects most combinations. The tuple index lookup
inside isValidFor uses the candidate just assigned; it does not constrain the
inner loop using the already-bound outer variables.

A prospective prefilter must preserve the existing OR across matching solve
groups, unary/unconstrained cases, identity/order and dynamic tuple publication.
In particular, terminal callbacks can publish tuples while a loop is in flight;
a candidate list computed once at loop entry is not automatically safe. The
existing forEach ID snapshot and current-binding side effects also require
qualification. No pruning is implemented in this checkpoint. Next is a shadow
candidate-selection probe against isValidFor before any skipping is attempted.

Evidence: rotation-on.csv, frontier-{off,on}.csv and frontier-{off,on}.counts.
The former records existing final-Linker statistics without frontier counters;
the latter include the explicit per-width diagnostic. This instrumentation
remains confined to the experimental branch.
