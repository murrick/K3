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

## Shadow candidate frontier

Flag `kanger.experiment.shadowRotationFrontier=true` enables an experimental
prediction before each candidate is assigned. The old loop still hydrates and
assigns every candidate, and isValidFor remains the decision maker. Any prediction
disagreement throws AssertionError. No candidate skipping is implemented.

For each solve group containing the inner variable, the predictor chooses an
already-bound outer variable present in that group and looks up its tuple bucket.
It checks the other outer bindings, then collects allowed inner TValue IDs.
Groups are unioned, preserving the oracle's OR semantics. Unary and unconstrained
groups permit all values. With no outer pivot, it inspects the group's existing
indexed tuples. This is a runtime-derived frontier, not compile-time topology.

The frontier cache lives only within one rotateVariables invocation. It rebuilds
when the solve-map version or outer binding IDs change. Public map exposure and
Mind subclasses force rebuilds for each candidate. Synchronization precedes
prediction; the regular isValidFor synchronization still executes too. These
extra operations make shadow timings unsuitable as acceleration evidence.

On set_08_02, one warmup and three samples each report main-thread counters:
244,035 comparisons, 1,482 frontier builds, 241,077 predicted rejections,
zero disagreements, and 1,479 tuple inspections. Results remain 493/493.
All 123 existing corpus cases pass locally, and the 20-operation transaction
state file is byte-identical to a separate non-shadow run. Java 17 only, both
TValue preservation and versioned solve synchronization ON. Full Java 8/21
qualification and adversarial frontier-invalidation tests are still pending.

Next gates before an actual prefilter: explicitly exercise publication during
rotation, outer-binding mutation, empty/unary/multi-group cases and exposed-map
fallback. Then preserve the forEach snapshot order and current-binding effects
while skipping candidates, and compare the complete inference results again.
The shadow result alone does not establish those skipping semantics or speedup.

Evidence: shadow-frontier.csv, shadow-frontier.counts, shadow-state.txt and
shadow-qualification.txt. Production develop remains untouched.

## Minimal rejection bypass

Default-OFF `kanger.experiment.filterRotationFrontier=true` now skips isValidFor
only for frontier-predicted rejections. It retains TValue hydration, iteration
order, setCurrent, and every accepted-path oracle check. Exposed solve maps and
subclasses retain oracle checks on every candidate. Adding shadowRotationFrontier
restores comparison against the oracle on every rejection (verified mode does
not actually bypass those calls).

RotationFrontierSafetyRunner invokes the real rotation loop with publication and
outer-binding mutation inside terminal callbacks. Eight scenarios run in four
modes: reference, shadow, filter and verified. They cover fresh tuple publication,
outer-binding change, external-map append, empty eligibility, unary wildcard,
unconstrained groups, union of groups with/without an outer pivot and null outer
binding. Terminal visit order and final current binding match reference. The
empty-eligibility probe confirms actual bypass (zero oracle calls versus three);
the exposed-map probe confirms three retained oracle calls. All 32 runs pass.

Filter and verified modes separately pass all 123 corpus cases; both 20-operation
transaction projections are byte-identical to the prior reference projection.
No worker exceptions were found in the captured logs. Qualification is still
local Java 17 only; no default activation, merge or broader contract claim.

Fresh JVMs in OFF/ON/ON/OFF order, three warmups and five timed invocations each,
TValue preservation and versioned sync ON, stack sampling/scan counters OFF:

| Pair | OFF median seconds | ON median seconds | Reduction |
| --- | ---: | ---: | ---: |
| OFF then ON | 0.987887476 | 0.946448606 | 4.2% |
| ON then OFF | 1.073768273 | 0.976442166 | 9.1% |

All 20 measured invocations finish with 493 solutions and values. These are warm
method measurements including formatting/file output, not cold console timings.
The small observed improvement is not evidence of a radical speedup. This bypass
still pays for per-candidate hydration, binding and frontier-cache validation.
Frontier counters also remain active in the enabled prototype. Skipping that
earlier work would be a different experiment requiring preservation of ID-snapshot
order, final current state and mutation during callbacks.

Evidence: frontier-boundaries.txt, filter/verify-state.txt and filter-bench-*.csv.

## Selected-ID hydration experiment

Default-OFF `kanger.experiment.selectRotationIds=true` uses the same ordered ID
snapshot, consulting the frontier before TValueFactory.get. Rejected IDs skip
hydration and callback execution. At normal loop completion, the last existing
value in the rejected suffix is loaded and restored as current. An accepted
value clears the pending rejected suffix. A nonempty rejected suffix also counts
as a nonempty variable domain, preventing the empty-domain recursion fallback.
The original forEach remains unchanged.

This path is restricted at entry to in-memory Mind without exposed solve maps;
shadow mode takes precedence and retains the reference traversal. Persistent
storage continues to use the reference path. Outer bindings and tuple version
are checked before every ID, allowing terminal callbacks to publish tuples or
change bindings. IDs newly created inside callbacks are excluded by the original
snapshot semantics. Source inspection confirms TValueFactory.set only changes
the current map; hydration may still have cache effects, and the restricted
experiment is not a general equivalence guarantee for persistent storage.

Nine boundary scenarios across five modes pass (45 runs), including a new
snapshot-publication case, retained final current binding, actual bypass and
exposed-map fallback. The existing 123-case corpus passes in selected-ID mode;
20-operation transaction states match reference byte-for-byte. Local Java 17
only; null/missing-ID suffix restoration and exceptional callback paths still
need dedicated adversarial qualification before any integration proposal.

Three warmups, five measured invocations per JVM, OFF/ON/ON/OFF order, other
accelerators ON and diagnostic sampling OFF:

| Pair | OFF median seconds | ON median seconds |
| --- | ---: | ---: |
| OFF then ON | 0.934562982 | 0.938623106 |
| ON then OFF | 1.040365554 | 0.971635396 |

One pair is 0.4% slower, the other 6.6% faster. These measurements do not support
a stable acceleration claim. All measured results are 493 solutions/values and
captured logs contain no worker exceptions. Timings include the historical
method's output formatting. No default or develop changes.

Although hydration is avoided for rejected IDs, each ID still incurs selection,
synchronization/version checks, a binding-list construction and pending-suffix
bookkeeping. Their overhead is a hypothesis to profile, not measured attribution.
Next: quantify selector cost before changing callback-boundary invalidation or
attempting traversal of only allowed IDs. The current prototype is retained as
an experimental comparison point, not proposed for integration.

Evidence: selected-boundaries.txt, selected-state.txt, selected-bench-*.csv.

## Selector cost and inclusive-stack correction (2026-09-18)

Added default-OFF profileFrontierCost diagnostics. Each successful rotationAllowed
call records timed intervals for synchronization, outer-binding construction,
cache validation/rebuild, and membership. Counts are main-thread only. Timers
and bookkeeping perturb this very short method; they exclude property lookup,
caller/suffix construction, pending-ID bookkeeping, and counter accumulation.
These are elapsed instrumentation intervals, not exclusive CPU accounting.

Selected-ID mode, TValue and solve-sync ON; two warmups, three measured samples:

| Sample | Calls | Rebuilds | Sync ms | Bindings ms | Validate/build ms | Membership ms | Sum ms |
| --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| 0 | 244035 | 1482 | 8.969 | 18.661 | 10.161 | 6.385 | 44.176 |
| 1 | 244035 | 1482 | 8.642 | 20.402 | 8.329 | 6.373 | 43.746 |
| 2 | 244035 | 1482 | 8.737 | 17.537 | 8.369 | 17.860 | 52.503 |

The measured selector stages are not the majority of whole-test elapsed time.
This weakens the hypothesis that selector overhead alone explains the lack of
a large speedup. Optimizing only those measured stages cannot account for most
of the remaining time. No new end-to-end acceleration claim is made.

The earlier rotateVariables sample counts were inclusive: they include terminal
callbacks and their database/cause work. They must not be read as time spent
solely enumerating candidates. A separate selected-ID stack profile and another
run with leaf counters corroborate the distinction. In the latter run,
rotationAllowed appears in 10 inclusive RUNNABLE observations and only 2 leaf
observations. Leading leaf observations include Argument.isEmpty (84),
ThreadLocalMap.getEntryAfterMiss (57), HashMap.putVal (47), CachedDomain.getCauses
(38), AbstractCollection.addAll (36), HashMap.hash (31), and
ArgumentsList.equalsBase (22). These are approximate sampled observations across
scenario threads, not additive per-stage timings or proof of a library defect.

Next bounded investigation: measure getCauses invocation/memo-hit frequency and
argument conversion/comparison work, and distinguish those paths from database
matching before proposing another optimization. No additional algorithm change
was made in this checkpoint. All nine measured invocations (three each for cost,
inclusive stacks and leaf stacks) end with 493 solutions/values; logs contain no
AssertionError or Exception. Existing safety gates are not rerun for diagnostic
instrumentation alone.

Evidence: selector-cost.{csv,txt}, selector-stacks.{csv,txt}, selector-leaves.{csv,txt}.

## Cause selection cost (2026-09-18)

Default-OFF profileCauseMemo counts successful getCauses paths on each thread:
calls, hits, convert ns, memo-check/hit-copy ns, miss-selection ns, memo-publish ns,
source causes on misses, visited argument pairs, and returned causes. The runner
reports main-thread deltas only. Timed selection includes its diagnostic pair
increments; elapsed intervals are not an uninstrumented CPU profile.

TValue preservation and solve-sync ON; selected-ID OFF then ON, each with two
warmups and three measured runs. Every measured invocation reports exactly:

| Metric | Count |
| --- | ---: |
| getCauses calls | 1,972 |
| Memo hits | 986 |
| Memo misses | 986 |
| Source causes accumulated over misses | 486,098 |
| Argument pairs visited during weighting | 3,393,822 |
| Returned causes accumulated over all calls | 5,908 |

The mean input is 493 causes per miss; that mean is not a measured distribution.
Miss selection costs 220–262 ms with selected IDs OFF and 230–234 ms with them
ON. Argument conversion costs 0.28–0.38 ms, memo check/copy 0.36–0.50 ms and memo
publication 1.46–2.05 ms per invocation. Thus weighting/selection, not memo lookup
or conversion, dominates this particular method. All six results are 493/493;
no captured worker exceptions or assertions. No semantic algorithm change.

Source review explains likely duplicate requests: Linker.logCauses and
updateDatabase each call getCauses once for a null check and again for use. The
50% hit rate is consistent with this pattern, but caller-tagged counts were not
collected. Removing just those second requests is unlikely to remove the measured
miss-selection cost.

Important semantics for the next prototype: weight counts each nonempty own
argument that matches any donor argument by resolved term ID. Duplicate own
arguments each contribute; duplicate donor matches contribute only once per own
argument. If there are multiple distinct weights, the existing algorithm removes
only the minimum-weight group, not every group below the maximum. Argument.isEmpty
itself resolves getValue, so current pair loops repeatedly resolve the same values.
Exceptions and contextual resolution must not be silently given new semantics.

Next bounded experiment: resolve reusable argument IDs within a single selection
call and compare every calculated cause weight and final selected set against the
reference. No cross-call cache is justified by these measurements. Start in
shadow mode and cover empty arguments, duplicates, multiple weight levels and
context-dependent bindings before proposing an actual fast path.

Evidence: causes-selected-{off,on}.{csv,txt}.

## Resolved cause-weight shadow

Default-OFF shadowCauseWeights computes own argument IDs once per miss-selection,
donor argument IDs once per cause, and compares every predicted weight with the
reference nested loop. Own duplicates remain in a list; donor IDs form a set,
preserving one match per own argument. Predicted groups independently remove
only their minimum group when multiple weights exist. The final selected set is
compared before returning the unchanged reference result. No accelerated path
or cross-call cache is introduced.

Local Java 17, TValue and versioned solve sync ON, selected-ID rotation OFF:
123 corpus cases pass; the 20-operation transaction state equals reference.
Three measured set_08_02 invocations after one warmup each compare 486,098 cause
weights across 986 miss-selections, with zero discrepancies. Each shadow run
resolves 1,461,252 arguments and finishes with 493 solutions/values. This count
is not directly comparable to the earlier 3,393,822 visited argument pairs;
the reference resolves values repeatedly through both isEmpty and getValue.
Shadow elapsed times include both algorithms and are not speedup measurements.

CauseWeightShadowSafetyRunner checks empty arguments/lists, own/donor duplicates,
TVariable binding changes and clearing, three distinct weight levels (0,1,3),
retention of the intermediate weight and a memo hit. Its synthetic causes use
identity equality so weight selection is isolated from cause deduplication.
All pass. Corpus, transaction and set_08_02 logs contain no exceptions/assertions.

Limit: direct resolution in this diagnostic can throw where the reference's
isEmpty catches an exception; unusual/custom arguments may also have observable
resolution side effects. No claim is made for those unqualified paths. An actual
fast path must retain fallback or explicitly qualify them before adoption.
Next: design that guard, then measure the new calculation without double work,
keeping per-weight comparison available as verify mode.

Evidence: cause-shadow-counts.txt, cause-shadow.csv, cause-shadow-state.txt,
cause-shadow-boundaries.txt. Experiment only; develop unchanged.

## Guarded resolved-weight execution

Default-OFF resolvedCauseWeights precomputes weights for exact built-in Mind,
CachedDomain, Cause and Argument classes. Only EMPTY, TERM, TVARIABLE and TVALUE
arguments with exact corresponding object classes are admitted. Functions,
FValues, subclasses and failed object/value resolution fall back to the old loop.
Resolved weights use an identity map local to this one selection. The original
weight groups, minimum-only removal, returned copies and memo publication remain.
With shadowCauseWeights also enabled, each resolved weight is compared with the
old loop and the old selection remains authoritative. Qualification does not
establish equivalence for concurrent or custom side-effectful getters; the guard
deliberately excludes extensions rather than making a new contract for them.

OFF/ON/verify each pass the 123-case corpus and focused boundary runner. All
20-operation transaction state projections are byte-identical to reference.
Boundary coverage now includes real built-in Cause instances that prove the fast
guard is entered, and synthetic Cause subclasses that prove fallback. Previous
empty/duplicate/binding-change/multiple-weight checks remain. Local Java 17 only;
Java 8/21 CI and exceptional-resolution adversarial cases remain future gates.

Separate timing JVMs, three warmups and five samples each, OFF/ON/ON/OFF order;
TValue preservation and solve-sync ON, selected-ID experiment and shadow OFF:

| Pair | OFF median seconds | ON median seconds | Reduction |
| --- | ---: | ---: | ---: |
| OFF then ON | 1.005382943 | 0.982651754 | 2.3% |
| ON then OFF | 1.019863975 | 0.954659577 | 6.4% |

All 20 measured results are 493/493; no captured exceptions/assertions. Each ON
sample records 986 eligible fast selections and zero fallbacks on the main
thread. Eligibility counters add minor prototype overhead; stage timers and
sampling are OFF. These modest warm-test gains do not establish cold-console or
general workload improvement and are not grounds for default activation.

A separate instrumented ON run measures 168–179 ms in miss selection, compared
with the preceding diagnostic reference range of 220–262 ms. The runs are not a
new controlled stage-level speed comparison: the reference also increments its
pair counter inside the nested loop. Nevertheless, this supports that some
selection work was removed, rather than the fast guard simply never firing.
The new path still validates/resolves every donor and allocates per-cause ID sets
and a per-selection weight map. No cross-call caching is proposed yet.

Next decision: profile remaining guard/resolution/allocation costs and broaden
qualification only if further benefit justifies keeping this fast path. This
checkpoint remains experimental and is not proposed for merge.

Evidence: cause-fast-{off,on,verify}-state.txt, cause-fast-bench-*.csv/.counts,
cause-fast-profile.csv/.counts, cause-fast-qualification.txt. Profile arrays now
append eligible-fast and fallback counts after the previous four shadow fields.

## Compact per-selection argument buffers

Default-OFF compactCauseWeights modifies only the eligible resolvedCauseWeights
path. Own IDs are stored in one primitive long array per selection; one donor
buffer grows as needed and is reused across causes. Only the populated prefix
is compared. Each own duplicate still contributes independently, and the first
matching donor ID ends its search. Empty arguments are omitted without reserving
a sentinel ID. Guard checks, fallback, identity weight map, memo and minimum-only
weight-group removal remain unchanged. No cross-call buffers or new cache.

Compact ON and verify each pass the 123-case corpus, focused boundaries and the
20-operation transaction comparison against reference. Focused checks add stale
buffer tails, an empty reused donor buffer, duplicate IDs and full-width IDs
(including zero and Long.MAX_VALUE). Verify still compares each weight and final
selection to the original nested argument-resolution loop. Java 17 only.

Three warmups and five measured invocations per JVM, compact OFF/ON/ON/OFF;
TValue preservation, solve-sync and resolvedCauseWeights held ON. Other rotation
experiments, stage timers and stack sampling OFF:

| Pair | Previous resolved mode, seconds | Compact mode, seconds | Reduction |
| --- | ---: | ---: | ---: |
| OFF then ON | 0.953575457 | 0.904310618 | 5.2% |
| ON then OFF | 0.987780523 | 0.913829639 | 7.5% |

These compare against the preceding resolved implementation, NOT against the
original weighting algorithm or an unoptimized product. Gains from separate
experiments must not be added together. Larger-arity donors may have different
performance because primitive membership is a linear scan.

A separate allocation run uses ThreadMXBean thread-allocated bytes, three
warmups and three samples per mode. Main-thread median cumulative allocations:
1,093,624,584 bytes before versus 917,692,192 bytes compact, about 176 MB / 16.1%
less. This includes the complete historical method and output formatting, excludes
worker-thread allocations, and is neither retained memory nor peak heap. All
20 timing and six allocation samples end with 493 solutions/values and logs have
no captured exceptions/assertions. Allocation timings are not speed evidence.

Next: a direct comparison of original versus compact weighting and broader
workload/Java 8/21 qualification before any integration proposal. The experiment
is still default OFF and no develop changes were made.

Evidence: cause-compact-{on,verify}-state.txt, cause-compact-bench-*.csv/.counts,
cause-compact-allocation-{off,on}.csv/.counts, cause-compact-qualification.txt.
