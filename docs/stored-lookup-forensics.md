# Stored-domain lookup callers and repeated checks

Base `develop/3.8.0 @ 1c943d02d39bd33ebd91fabb7c4190112ab49459`, verified live 2026-10-06. Branch `experiment/3.8.0-stored-lookup-forensics`. Production, qualification and workflow sources remain identical to base. This is diagnostic evidence, not a new optimization or flag.

The earlier [Rule-read census](https://github.com/murrick/K3/blob/experiment/3.8.0-rule-read-source-forensics/docs/rule-read-forensics.md) measured 1.306–1.312 million Rule reads from Domain.isStored, but did not separate its callers, empty lookups or repeated checks within one linkDatabase invocation. This checkpoint measures that smaller boundary before proposing a caller-local replacement.

## Warm counts

Two fresh sequential Java 17.0.20 JVMs, six ordinary logged natives.k / son(John,x) samples each, 512 MB, ECJ -1.8, all eight integrated flags explicitly ON. Counters operate on the optimization thread only during optimizeHypothesis. Exclude the first two samples of each JVM from warm ranges. Every corresponding sample index has identical site counters, histograms, transitions and scope counts across JVMs; candidate-visit totals vary with sample index rather than remaining stationary. No cause is assigned to that variation.

Per optimization:

| Quantity | Warm count |
|---|---:|
| Domain.isStored base-method calls | 3,203,929 |
| Calls from the six Linker sites | 3,203,796 |
| Arguments-overload calls outside linkDatabase | 133 |
| Rule candidate hydrations inside those lookups | 1,306,551–1,311,842 |
| Empty candidate lookups | 1,915,613–1,920,835 |
| Maximum candidate visits per lookup | 2 |
| Repeated same-Domain/same-Mind checks within one linkDatabase | 1,292,948 |
| Repeated checks whose boolean changed | 0 |
| Candidate hydrations in repeated checks | 638,340–640,993 |

Repeats account for 40.355% of counted base-method calls and 48.857–48.862% of candidate visits. These are count shares, not removable CPU shares or speedup estimates. The ordinary hash index already narrows every observed lookup to at most two visited candidates; the issue is frequency and binding effects, rather than a broad collision scan. Excluding the 88 candidate reads in the arguments overload reproduces the earlier 1,306,463–1,311,754 Rule-read range exactly.

Representative sample 2 (same values in both JVMs):

| Tag / original Linker line | Role | Calls | True | Candidate reads | Empty lookup |
|---|---|---:|---:|---:|---:|
| 1 / 1057 | Initial tree classification | 1,910,848 | 646,451 | 670,761 | 1,251,987 |
| 2 / 1065 | Single candidate | 401,575 | 379,473 | 388,476 | 21,744 |
| 3 / 1090 | All excluded | 713 | 0 | 5 | 708 |
| 4 / 1105 | Calculated candidates | 744 | 508 | 597 | 204 |
| 5 / 1133 | Assumed single candidate | 186 | 72 | 72 | 114 |
| 6 / 1148 | Alternate hypotheses | 889,730 | 244,159 | 251,843 | 640,811 |

All repeated checks pair an initial tag-1 check with a later tag 2–6 check on the same Domain reference and the same supplied Mind within that invocation. Reentrant linkDatabase scopes are distinct. No results are reused by the diagnostic. There are no counted exceptions, nested find(Solve) calls, null candidates or changed supplied-Mind identities. Every counted Mind, RuleFactory and hydrated Rule is an exact built-in class. **No counted receiver is exact Domain.class**; a future exact-Domain-only guard would have zero observed coverage. Hydrated CachedDomain exists in the production model; this census records the exact-Domain test, not a complete runtime receiver-class histogram.

## Why equal booleans are insufficient

`StoredLookupWitness` uses exact native Mind, RuleFactory, Rule, Domain, Predicate, ArgumentsList, Argument, TVariable, TValue and Term objects. A controlled cache fixture seeds a native stored Rule with one bound-variable argument while the variable selects root value a. Its canonical hash is captured in that state. A literal source Domain asks whether p(a) is stored. The fixture uses reflection solely to seed the private ICache; it does not claim to qualify the public publication pipeline.

First ordinary isStored returns true and selects the Rule's variable into root. Then compare an ordinary second call with a diagnostic replacement that simply reuses the first true:

| Intervening action | Ordinary second call | Reused boolean |
|---|---|---|
| Select shared variable into child directly | true; root a visible | true; child b visible |
| Another Domain selects that variable into child | true; root a visible | true; child b visible |
| Logically delete the found Rule | false | true |

The first two cases preserve the returned boolean while changing the observable binding. They use no custom subclass, callback or concurrent mutation. The third confirms deletion-state invalidation. These witnesses do not assert that those particular transitions occur between the measured Linker sites; they disprove equality of answers or outer object/context identity as a sufficient general qualification boundary.

All three scenarios / 33 checks pass independently on freshly compiled clean base and on the diagnostic build, with byte-identical witness output and empty stderr. Witness class hashes are identical too. Custom callbacks, argument mutation, promotion and exceptions would need additional proof for any real candidate.

## Instrumentation and validation

Temporary copies instrument three source files: Linker tags the six existing virtual calls and brackets each linkDatabase; Domain brackets both original isStored bodies; RuleFactory brackets find(Solve), candidate hydration and the already-evaluated equality result. Original method dispatch, hash computation, candidate order, hydration, promotion, deletion checks, short circuits and exception propagation remain in place. Extra class checks use getClass only. No extra semantic getters or map probes are used to inspect results.

IdentityHashMap retains each observed Domain only within a linkDatabase scope. Nested scopes restore in finally and all frames must balance before phase completion. Site/histogram aggregates retain primitive counts, not semantic objects. Instrumentation allocates frames, maps and counters and can alter GC/lifetimes/scheduling; **all instrumented elapsed, CPU and allocation measurements are excluded from performance conclusions**. It is not a concurrency qualification.

All twelve full raw and optimized text sets match the independently retained oracle: 18 raw, six final hypotheses, unknown logical result, zero solutions/values. All last-Linker statistics match: passes=6 rules=1292 rotations=6824 pairs=13281 unifications=3080. These are last-Linker records, not global work counts. Calls/results, comparisons, histogram length/weight, scope coverage and repeated-transition totals reconcile independently in analyze.py.

Clean/diagnostic shared-class differences are confined to Linker, Domain, RuleFactory and its anonymous nested class; the recorded source patch touches only the three outer source files. Compiled diagnostic hashes, class-difference list, source manifest, helper/driver, completed compressed logs and witness evidence are retained alongside this report. Tracked production/qualification/workflow code is unchanged, so no new compatibility CI matrix is requested.

## Decision and reproduction

Reject simple boolean reuse at the Linker caller. Repetition is substantial, but preserving the lookup's binding/deletion effects needs a proved boundary; zero changed booleans in one workload does not supply it. Do not introduce a cache, skip hydration or bypass callbacks from these counts. This closes the straightforward reuse proposal, rather than declaring all caller-level improvements impossible.

See [the remaining investigation directions](remaining-optimization-directions.md). The next two bounded directions are resolved-domain candidate hydration and the overall hypothesis-validation/replay cycle. They require new profiling or qualification before implementation; neither is a promised acceleration.

From repository root, with external ECJ at ../tooling/ecj.jar: `python docs/stored-lookup-forensics/build.py`, then `run.py`, `witness.py`, and `analyze.py` from that directory, all invoked from the repository root. Production sources remain base-identical. Timing is not compared against this instrumented build.
