# Repeated-pass dependency checkpoint

The factory-lifetime stage was published through the GitHub connector because
command-line push lacked credentials. The three remote commits have identical
trees to the local originals:

| Local original | Remote commit |
|---|---|
| e5158b7 | 0e9e65d |
| a59454d | fed09e8 |
| 96bb518 | d2404d8 |

The working experimental branch was aligned to the remote history after an
exact tree comparison; the original history remains on the local
`experiment/latent-substitution-local-original` branch. Develop is unchanged.

## Observation

LinkerStatistics now counts the action flags at the end of each completed pass,
immediately after both rotators. The existing continuation condition is
unchanged. Bits are Rule=1, TValue=2, FValue=4, TempHypothesis=8, Hypothesis=16.
Mask zero is a quiescent pass. Early-exit/aborted passes have no histogram entry;
the counts must not be mistaken for all started passes or individual mutations.
The histogram is copied, reset and aggregated with the existing statistics.

Six fixtures / 16 operations from LatentSubstitutionStateRunner produced:

| End-of-pass actions | Completed passes |
|---|---:|
| None | 9 |
| Rule | 3 |
| TValue | 2 |
| Rule + TValue | 7 |
| TempHypothesis | 2 |
| Rule + TValue + TempHypothesis | 5 |

Of 19 completed passes requesting continuation, four had no Rule action.
TValue-only continuation occurred for `?path(A,C);` and `?male(Tom);`.
TempHypothesis-only continuation occurred in both native male queries.
This rules out using only the Rule action flag as a replacement for the current
continuation condition. It does not establish the minimum work required in the
next pass. No FValue or final Hypothesis action at a completed-pass boundary was
observed in these fixtures; absence here is not proof that they are irrelevant.

Off and factory-verify output, including histograms, matched byte for byte.
Removing the new histogram lines also reproduced the saved pre-instrumentation
logical projections exactly. Raw outputs are in
`latent-substitution-evidence/pass-actions/`.

## Source-level dependency boundary

* RuleFactory raises action on insertion, restoration, promotion and propagation
  from a committed child. A Rule action does not necessarily mean a new topology
  row. Visibility and primary/generated status can change on an existing row.
* TValueFactory raises action on a newly inserted variable/value binding and
  propagates child action. Linker's terminal rotations consume these bindings;
  unchanged predicate adjacency can acquire new work.
* FValueFactory raises action on a new value for a complete Function. Scheduling
  must include function dependencies and existing checkpoint behavior.
* HypothesisStore raises action when adding a nonduplicate hypothesis; Linker
  uses separate temporary and final stores in its continuation condition.
* Used/excluded/calculated Domain state, used Rules, Causes and deferred solves
  also change within a pass. They affect current selection and processing even
  though they are not independent flags in this histogram.

The next safe experiment is an observational dependency trace relating changed
bindings/hypotheses to affected Rule/Domain IDs, with ordering and transaction
context preserved. Do not skip a direction or a pass based on this histogram.
No scheduler behavior has changed in this checkpoint.

## Follow-up: work after a non-Rule continuation

Enable `-Dkanger.experiment.tracePasses=true` before constructing statistics to
record per-completed-pass counter deltas. Incoming mask -1 identifies the first
pass of a link invocation; subsequent incoming masks are the preceding pass's
outgoing flags. Traces preserve invocation order when statistics are aggregated.
They are diagnostic observations, not a work queue.

| Query | Incoming mask | Outgoing mask | Rule visits | Unifications | New-TValue counter |
|---|---:|---:|---:|---:|---:|
| chain `?path(A,C);` | 2 | 0 | 18 | 14 | 0 |
| natives `?$x male(x);` | 8 | 11 | 232 | 555 | 34 |
| natives `?male(Tom);` | 8 | 11 | 236 | 555 | 34 |
| natives `?male(Tom);` | 2 | 0 | 264 | 612 | 28 |

In both native queries the pass following a TempHypothesis-only continuation
produces Rule and TValue actions again. Dropping this continuation would skip
observed later activity. This does **not** prove that the hypothesis insertion
caused that activity: delayed work can depend on other state accumulated during
the preceding traversal. A final-result counterfactual has not been run.

The New-TValue counter is operational and can include work subsequently rolled
back; it is not a count of surviving new bindings. In particular, 28 such events
coexist with a zero outgoing action mask in the last native row. Any future
activation journal must respect checkpoint commit/release, not blindly enqueue
every attempted mutation.

The direct binding dependency in current code is
TValueFactory.forEach(TVariable) -> Linker.rotateVariables -> all branches of the
visited Rule. Rotator gathers variables across the entire Rule, not just the
current Domain. isValidFor additionally consults cross-variable TSolve tuples.
Consequently, a domain-pair adjacency edge alone does not describe the complete
activation dependency, and even a variable-to-owner-Rule map needs qualification
against actual argument occurrences and tuple constraints.

TempHypothesis use inside Linker is duplicate detection when collecting alternate
hypotheses. Mind subsequently transfers eligible temporary hypotheses into the
query result. This differs from the direct variable-enumeration dependency and
must not be presented as an established hypothesis-to-Rule activation edge.

Off and factory-verify traces and logical projections match byte for byte. After
removing trace lines, output also matches the prior histogram checkpoint. Raw
traces are in `latent-substitution-evidence/pass-trace/`. No pass is skipped.
