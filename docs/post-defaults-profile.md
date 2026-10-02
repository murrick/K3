# Hypothesis profile after enabled defaults

Pinned live base: develop/3.8.0 at 3a2a6f0bccac11ac0d646178c4384ba73d7a5260.
Branch: experiment/3.8.0-post-defaults-profile. Production code is unchanged.
All seven integrated optimizations use their enabled defaults; no optimization
property or unmerged experiment is selected.

## Protocol

Two fresh JVMs, Java 17.0.20, 512 MB heap, six son(John,x) samples each.
WarmSonProfileRunner is a diagnostic copy of the existing SonProfileRunner,
with one JFR duration event bracketing optimizeHypothesis per sample. It and the
JFR reader live under docs, outside the Java8 reactor. CPU and allocation events
are filtered to the main thread and the last four recorded optimization phases.
Compilation, query execution, the first two samples and result rendering are
excluded. Stack depth 256; neither warm recording has truncated sampled stacks.

ExecutionSample is a sampled observation, not an exact call count. Inclusive
rows overlap and must not be added as independent CPU costs. ObjectAllocationSample
weights estimate allocation attribution, not retained heap or exact allocation
counts. These recordings are for attribution, not performance comparison: JFR
and diagnostic phase events perturb the run. No elapsed speedup is inferred.

Local build/run: docs/post-defaults-profile/build-local.sh, then run-profile.py,
then analyze.py. The scripts require the external ECJ jar and Java17. The raw
local JFR paths, sizes, hashes, input hash and base are in recording-manifest.json;
raw recordings are intermediate local inputs, not committed repository binaries.
Both derived TSVs and original runner output are committed.

## Repeatable observations

| Observation | Profile 1 | Profile 2 |
| --- | ---: | ---: |
| Warm main-thread execution samples | 2757 | 2561 |
| isUnitDeleted inclusive sample share | 18.06% | 18.31% |
| equalsBase inclusive sample share | 8.02% | 8.24% |
| get(TVariable) current lookup sampled share | 7.76% | 6.25% |
| activeMind inclusive sample share | 7.62% | 9.22% |
| Predicate.getName allocation weight share | 10.76% | 11.15% |
| Escalera.find allocation weight share | 11.72% | 12.20% |
| TVariable.setMind allocation weight share | 3.13% | 1.88% |

Current-lookup routes distinguish the TVariable overload from get(long).
equalsBase appears in 56/214 and 77/160 current-lookup routes; the former
dominance cannot simply be assumed for the new baseline. Significant remaining
routes include argument hash computation, stamps, Rule equality, domain
completion and Linker validity. activeMind remains partly within the resident
prefilter, because its context guard intentionally reads the thread-local view.
Removing that guard would require new semantic proof, not just a profile lead.

The largest leaf is HashMap.getNode (586/2757 and 520/2561 observations), followed
by deletion checks, thread-local lookup, string builder construction and hash-map
iteration. The top allocation sites repeat across the two recordings: predicate
name conversion and Escalera candidate snapshot copies account jointly for
22.48% and 23.35% of sampled allocation weight. This is not an achievable wall-time
speedup estimate and does not imply those allocations are all removable.

All twelve raw/final hypothesis snapshots and result counts match, stderr empty.
They also match the unprofiled enabled-defaults qualification control.

## Next experiment

Requalify the existing default-OFF directPredicateName and compactFindSnapshots
combined prototype on this base, with residentBaseComparison enabled. The old
combined-allocation measurements used the six-flag baseline and do not prove
the same gain or callback/state equivalence after the resident prefilter.

Name conversion must resolve its donor on each call and retain non-String/null
toString behavior. Candidate snapshots must retain public find's mutable ownership,
custom cache callbacks, multi-candidate order and callback isolation. Reuse only
the minimal guarded implementation, not the entire old experiment branch.

Then compare all four new-flag combinations, logical texts, transactions/reopen
and callbacks before measuring clean wall/CPU/allocated bytes in reversed order.
No merge/default enable of either prototype is authorized by this profiling work.

isUnitDeleted is a larger remaining CPU lead, but the prior empty-layer shortcut
did not establish a timing gain. Its restored-before-deleted precedence, parent
context traversal, locking and custom unit callbacks must be retained. Do not
revive that shortcut solely because this method still appears in samples.
