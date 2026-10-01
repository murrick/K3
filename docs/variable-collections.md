# Variable collection allocation investigation

Base: develop/3.8.0 at 1828d7164159cb04df00dedd5c27e18d556af4ea.
All six integrated optimization flags ON. Java 17.0.20, ECJ Java 8 target,
512 MB heap, independent JVM and isolated home per run. No develop changes.

## Collection frequency

Single-thread diagnostic counters cover compilation and two complete son
queries/hypothesis optimizations. They are not benchmark timing data.

- getTVariables: 14,126,070 calls, 26,576,616 input arguments.
- Output sizes: zero 5,818,197; one 1,174,257; two 7,133,616; no larger outputs.
- TVariableSet constructions: one member 92,866; two members 832,868.
- TVariableSet.hashCode: 5,987,880 calls, 10,900,441 visited members.

## Compact result list prototype — archived

Flag compactVariableLists initially OFF. Exact ArgumentsList only: retain
mutable ArrayList results but defer backing allocation and reserve 2–10 slots
on first insertion instead of the default ten. Ordered list.contains dedup,
recursive functions, hydration, both deletion reads and callback order remain.
No cached projection or topology. Prototype and 23-assertion boundary runner
are archived in compact-variable-lists-inconclusive.patch; no production change
remains in the branch. Apply the patch to reproduce its qualification script.

Six fresh JVMs, four samples each, warm median of three after excluding first.
Main-thread allocation is measured with ThreadMXBean, not retained heap or
all-thread allocation. Both measurements include the optimization phase only.

| Order | Mode | Warm optimize seconds | Warm allocated bytes, MB |
|---|---|---:|---:|
| 1 | OFF | 13.708 | 5352.7 |
| 2 | ON | 16.895 | 5183.2 |
| 3 | ON | 15.198 | 5264.0 |
| 4 | OFF | 12.738 | 5250.9 |
| 5 | OFF | 14.183 | 5251.2 |
| 6 | ON | 11.145 | 5102.9 |

Paired time reductions: -23.2%, -19.3%, +21.4%; allocation reductions:
+3.17%, -0.25%, +2.82%. Environment/JIT variation is substantial. Neither a
repeatable speedup nor a consistently measured whole-phase allocation reduction
has been demonstrated. This variant does not justify integration.

All 24 raw/final snapshots match: 18 raw, six final, unknown result, zero
solutions/values. Both modes pass 23 boundaries, the regression corpus with
mandatory LATENT_CORPUS_PASS, and 20 transaction operations with byte-identical
full state projections. No cross-JDK CI was requested for this rejected variant.

## Whole optimization work

Separate single-thread diagnostic global counters aggregate all nested Linker
calls during optimizeHypothesis, unlike the runner's last nested call snapshot.
Two samples in OFF and two in ON have identical six counters each:

| Counter | Per optimization |
|---|---:|
| Passes | 750 |
| Rule visits | 156374 |
| Candidate rule visits | 319967 |
| Terminal rotations | 858101 |
| Domain pairs | 1599204 |
| Unification attempts | 367807 |

These counters do not trace every accepted substitution or collision. They
show equal work volume in these diagnostic runs, not the cause of timing noise.
Instrumentation was restored immediately after compiling diagnostic classes.

## Allocation profile and next candidate

JFR profile settings, baseline compact flag OFF, fresh JVM, compilation and
two complete son query/optimization runs. Reader filters main-thread
jdk.ObjectAllocationSample events: 10,167 samples, 11,297,545,608 weighted bytes.
Weights are allocation sampling estimates, not exact class byte counts, object
counts, retained memory or CPU percentages. Startup/query allocations are
included; the phase-specific ThreadMXBean measurement above is separate.

Leading first Kanger stack locations:

| Location | Weighted sampled bytes | Share |
|---|---:|---:|
| Predicate.getName | 1192994304 | 10.6% |
| Escalera.find, result HashSet copy | 1125488000 | 10.0% |
| RuleFactory.find | 444820640 | 3.9% |
| getTVariables result growth | 396391408 | 3.5% |
| getTVariables result list construction | 367921664 | 3.3% |

Predicate.getName currently returns name.getValue() + "". A future narrow
prototype can return the value directly when it is already a String, keeping
the existing conversion for all other types. Read the value afresh each call;
do not cache names across setName/setPersistentNameId or contexts. Preserve
custom getValue/toString callbacks, nulls, non-string values, lazy hydration
and exceptions. This candidate has not yet been implemented or benchmarked.

Text logs, diagnostic patches, archived prototype, JVM benchmark scripts,
summary.json, allocation-profile.tsv and standalone Java 17 JFR reader are in
variable-collections-evidence. The transient recording is in the build area.
To reproduce: compile the reader with Java 17, run SonProfileRunner with
StartFlightRecording using profile settings, then pass the .jfr path to
ReadAllocationProfile. Instrumentation and JFR timings are not speed evidence.
