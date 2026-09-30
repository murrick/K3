# son(John,x): phase profile, 2026-09-29

Base: develop/3.8.0 d20bc425a724b2a0881f78e1d3d8a727fe24d999.
Branch: experiment/3.8.0-son-profile. No production changes.
Uploaded natives.k is byte-identical to repository natives.k.

## Method

Fresh full ECJ -1.8 build; OpenJDK 17.0.20, -Xmx512m.
SonProfileRunner initializes a new User and UDF, compiles natives.k once,
then repeats the normal logged Mind.query("?$x son(John,x);") followed by
optimizeHypothesis(). Compilation and both phases are timed separately.
Each OFF/ON mode is a separate JVM, run sequentially. Four iterations each:
first reported separately, remaining three summarized by median. OFF first,
ON second; no reverse-order replication yet, so this is exploratory evidence.
No sampler or verification flags in timing runs. No console rendering included.

ON flags: kanger.experiment.preserveTValueIndex, versionedSolveSync,
resolvedCauseWeights, compactCauseWeights, candidateMembershipFilter = true.
OFF: these properties absent (default false).
Verify adds verifyTValueIndex, verifySolveSync, shadowCauseWeights,
verifyCandidateMembershipFilter = true; one iteration, not a timing result.
Separate sampled ON run: bench.sample=true, bench.samples=2.
Sampler observes the main-thread stack every approximately 5 ms, separately
for query and optimization. These are wall-stack observations, not CPU shares;
inclusive method counts overlap. Callsite lines identify disjoint optimize stages.

## Results (seconds)

| Phase | OFF first | ON first | OFF warm median | ON warm median |
| --- | ---: | ---: | ---: | ---: |
| Compile | 0.414 | 0.418 | n/a | n/a |
| Query | 0.346 | 0.334 | 0.145 | 0.108 |
| Optimize hypotheses | 11.049 | 8.346 | 9.483 | 5.759 |

The exploratory warm optimization reduction is about 39%; this is not an
aggregate benchmark claim and does not attribute benefit to individual flags.
The user's laptop timings differ; this harness establishes the expensive phase.

All 11 iterations (4 OFF, 4 ON, 2 sampled ON, 1 verify) return WHO KNOWS,
zero solutions/values, 18 pre-optimization hypotheses and 6 final hypotheses.
Sorted full textual RAW and OPTIMIZED snapshots match across all iterations;
this checks contents, not only cardinality, but does not check original list order.
OFF, ON and verify stderr are empty. The sampled stderr contains the profile.
Statistics emitted after optimize refer to the LAST Linker invocation, not
aggregate work across all nested hypothesis replays. Initial off.log uses the
older label STATS; subsequent logs label it POST_OPTIMIZE_LAST_LINKER_STATS.

## Where optimization spends time

Second sampled iteration: 1087 observations in optimizeHypothesis.
HypothesisStore.optimize callsites at this base:

- line 222, replay original query under candidate: 711 (65.4%).
- line 216, link candidate: 341 (31.4%).
- line 195, expanded abstractive replay: 17 (1.6%).
- line 217, analyze collision: 3 (0.3%).

First sampled iteration corroborates the split (841/1315 replay, 403/1315 link).
Most sampled time is inference inside per-candidate checks, not list formatting.
Frequent leaf observations in the second iteration include Argument.isEmpty
(165), ArgumentsList.equalsBase (108), Linker.linkDatabase (73), and
Mind.isUnitDeleted (67). These point to the next investigation, not proof that
those methods independently consume exactly those CPU percentages.

The existing algorithm expands abstractive candidates, then for each candidate
creates an isolated Mind, compiles and links the hypothesis, checks collisions,
and replays the original query. Skipping these checks would change semantics
unless independently proven equivalent. Next inspect repeated argument resolution
and domain membership scans within those existing checks; preserve the oracle.

## Reproduction

Compile SonProfileRunner with the normal core/UDF/console sources.
Run from repository root with natives.k available:

```sh
java -Xmx512m -Dbench.samples=4 -cp "$KANGER_PROFILE_CP" org.kanger.SonProfileRunner
```

KANGER_PROFILE_CP must contain compiled classes, kanger/resources,
kanger-udf/src and lib/jline-3.13.0.jar. Add the five fully qualified ON flags
listed above for ON. Raw logs are in son-profile-evidence/. The runner and
sampling code are diagnostic only, not part of production default behavior.

## Rejected resident-Term comparison experiment

A narrow default-OFF prototype bypassed repeated isEmpty/getValue calls only
when both Argument objects already held exact built-in Term instances.
No hydration, binding resolution, or cross-call memoization was added.
The existing C-variable parent comparison and short-circuit order were retained;
all other cases used the original path. Verify compared only eligible pairs,
never double-invoking custom or dynamic fallback arguments.

17 boundary assertions passed in each of OFF/ON/verify (51 total): empty lists,
size mismatch, positional comparison, empty arguments, C-variable parent/child
in both directions, non-equivalent siblings, removed parent relation, lazy then
resident resolution, changed/cleared TVariable binding, and exact custom getter
call count. Fixture corrections before these passing runs: C-variable children
need valid distinct target Rule IDs; one child per parent/Rule displaces the old
child. Lazy-Argument setup clears its resident field rather than feeding a marked
serialization envelope directly into apply().

Timing with all five existing optimizations ON, four repetitions per fresh JVM:

| New resident path | Warm optimize median |
| --- | ---: |
| ON, first run | 5.846 s |
| OFF, subsequent control | 5.792 s |

The prior baseline was 5.759 s. No demonstrated benefit: the new path was about
0.9% slower than its adjacent control, within unquantified run variability.
The prototype is rejected for integration; production files were restored.
The exact prototype plus focused runner is preserved in
son-profile-evidence/resident-comparison-rejected.patch, applicable to c4e9e7f.

All 9 scenario iterations (4 ON, 4 control, 1 verify) have matching sorted RAW
and OPTIMIZED hypothesis texts against the original baseline, 18 -> 6,
WHO KNOWS and zero solutions/values. Stderr is empty. No broader qualification
or CI matrix was run because this prototype is not retained for production.
These timings do not qualify an optimization of dynamic argument resolution.

Next candidate for measurement: repeated per-domain scans in isExcluded/isUsed,
including how often the same left argument is resolved across stored tuples.
Any index would need to respect C-variable parent equivalence, binding changes,
and Mind lifecycle; ordinary hash equality is not interchangeable with equalsBase.

## Per-domain membership scan measurements

Temporary single-thread diagnostic instrumentation counted calls, bucket sizes,
comparisons, matches and misses separately for compilation, query and hypothesis
optimization. Two iterations with all five existing optimizations ON, followed
by one additional shape-counting iteration. No new optimization enabled.
Instrumentation performs map/histogram updates; its timings are NOT benchmark
results. It reads the bucket with one get instead of containsKey followed by get;
this diagnostic assumes the normal stable single-threaded Mind maps in this runner.
Production code and runner were restored after measurement; the reproducible
instrumentation is archived in domain-scan-instrumentation.patch.

Per optimizeHypothesis, first iteration:

| Metric | isUsed | isExcluded |
| --- | ---: | ---: |
| Calls | 2,126,237 | 2,221,036 |
| Comparisons | 3,215,033 | 11,202,902 |
| Hits | 1,483,818 | 706,261 |
| Misses | 642,419 | 1,514,775 |
| Comparisons in misses | 1,011,600 | 7,285,668 |
| Bucket size avg | 1.74 | 6.61 |
| Bucket size p50 / p95 / p99 / max | 1 / 4 / 7 / 8 | 5 / 19 / 22 / 25 |
| Scan length avg | 1.51 | 5.04 |
| Scan length p50 / p95 / p99 / max | 1 / 3 / 5 / 8 | 3 / 17 / 20 / 25 |

Second iteration has identical call/hit/miss/bucket counts. Comparison totals
are 3,208,555 and 11,200,127; HashSet traversal order affects where a hit occurs.
Miss comparisons are identical. isExcluded misses are 68.2% of calls and consume
65.0% of its comparisons. These are repeated dynamic scans, not counts of distinct
domains or compile-time latent substitution pairs.

The extra shape run records the left argument types without resolving values.
Of 11,202,902 excluded comparisons, 9,397,483 (83.9%) have at least one TVARIABLE
in the left tuple; 8,833,077 (78.8%) have two TVARIABLE positions. Plain TERM-only
left tuples account for 1,804,434 (16.1%). Shape counts describe whole comparisons,
not the number of positions actually evaluated after short-circuiting, and do not
count getValue invocations. This explains the limited coverage of the rejected
resident-Term-only shortcut, without proving why its timing lacked a benefit.

All three diagnostic iterations preserve full sorted RAW/OPTIMIZED snapshots,
WHO KNOWS, zero solutions/values and 18 -> 6 hypotheses; stderr is empty.

Decision: bucket sizes alone do not establish that an index will pay off.
Next experiment should assess resolving stable built-in arguments once within
one membership call, retaining reference fallback for unsupported/dynamic effects.
Do not cache across calls: TVariable binding and C-variable parent relationships
are contextual and mutable. Do not substitute ordinary hash equality for the
non-transitive parent/child equivalence used by equalsBase.

## Rejected per-membership resolved-left snapshot prototype

Live develop rechecked: still d20bc42. The prototype (archived as
resolved-scan-prototype.patch) preflighted resident built-in right-hand Term
arguments, then resolved the left tuple into a local Term array once per call.
Exact built-in TERM/TVARIABLE/TVALUE left arguments were eligible; unsupported
cases fell back. Existing C-variable parent equality was preserved. Default OFF
flag: kanger.experiment.resolvedDomainScan; verifyResolvedDomainScan additionally
repeated the original membership scan for eligible results.

Fresh sequential ON then OFF JVMs, 3 iterations each, five existing optimization
flags ON, Java 17, Xmx512m. Median of the two repeats after the first:

| Prototype | optimizeHypothesis seconds |
| --- | ---: |
| ON | 9.876 |
| OFF | 9.733 |

No demonstrated benefit (ON about 1.5% slower). Both current modes are slower than
earlier daytime results; these data do not identify the cause. Compare adjacent
runs only. Two measured repeats per mode are exploratory, not a statistical study.
Preflight traversal/allocation are plausible costs, not separately measured causes.

All 7 iterations (3 ON, 3 OFF, 1 verify) have matching sorted full RAW and optimized
hypothesis texts, WHO KNOWS, zero solutions/values and 18 -> 6; stderr is empty.
This is scenario-level equivalence evidence only. No complete qualification was
run: eager resolution, fault ordering, custom lifecycle behavior and persistence
would require further guards/tests before integration. The prototype is rejected
for promotion on performance grounds and production changes were restored.

Next direction: assess duplicate resolution inside a single argument comparison
or TVariable.getValue itself before adding a per-scan preflight and array. The
existing getValue calls the active TValue factory lookup twice on a bound value;
any local reuse must preserve custom getter/factory and missing-value behavior.

## Single TValue lookup prototype (retained experimentally)

Default-OFF JVM-startup flag: kanger.experiment.singleTValueLookup.
TVariable.getValue and getCurrent reuse one factory lookup within their call,
only for exact TVariable, Mind and TValueFactory classes. Subclass paths retain
the original repeated virtual getter behavior. No binding is cached across calls.
Production delta is limited to TVariable.java; not merged into develop.

Sequential fresh JVMs, Java 17, Xmx512m, all five earlier optimizations ON,
three iterations per mode. Median of the two post-first repeats:

| Order | OFF seconds | ON seconds | Reduction |
| --- | ---: | ---: | ---: |
| OFF then ON | 9.620 | 8.820 | 8.3% |
| ON then OFF | 11.445 | 10.206 | 10.8% |

Absolute runtimes drift substantially. Two repeats per mode are exploratory;
these paired observations support further qualification, not a universal speedup.
All 12 scenario runs preserve sorted full RAW/OPTIMIZED texts, WHO KNOWS,
zero solutions/values and 18 -> 6. Stderr empty.
SingleTValueLookupRunner passes 15 checks in each OFF/ON JVM: unbound value,
identity of current TValue, rebinding, clearing, child/parent active context,
and preservation of two virtual factory getter calls on a custom Mind.

Qualification limitation: the ON LatentSubstitutionCorpusRunner invocation
returned process exit 0 but its log stops during set_c3_05_binding_persistence_reopen,
without LATENT_CORPUS_PASS. This is NOT accepted as a complete corpus pass.
Investigate the missing completion before any integration. Dedicated concurrent,
transaction, persistent reopen and Java 8/21 qualification remain pending.
Raw corpus log is retained, including empty stderr. No CI or merge performed.

## Follow-up qualification, 2026-09-30

The C3 persistence-reopen test passes standalone with the flag OFF and ON.
A repeat of the full corpus with single lookup ON completed with LATENT_CORPUS_PASS
and C3 Success: 6 / Fails: 0. No production change was needed. The cause of the
previous incomplete log remains undetermined; the original run is still invalid
as full-pass evidence and its log is retained.

The 20-operation nested transaction corpus passes in independent OFF/ON JVMs;
full textual state files are byte-identical. Three persistent DUMB four-thread
set_08_02 iterations pass with single lookup ON and the final concurrency marker.
All new stderr files are empty. These checks used the same Java 17 local build.

Added single-tvalue-lookup.yml for Java 8/21 qualification: OFF/ON full corpora,
boundaries, transaction state comparison and ON persistent concurrency. Each
runner requires an explicit success marker in addition to process exit status.
The workflow has been prepared locally, not executed remotely. Integration remains
pending CI and explicit approval; default remains OFF.
