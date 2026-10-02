# Active Mind weak-reference reuse experiment

Base: live develop/3.8.0 checked at 1828d7164159cb04df00dedd5c27e18d556af4ea.
Branch: experiment/3.8.0-active-mind-reuse. Default OFF:
`-Dkanger.experiment.reuseActiveMind=true` (restart JVM).
No changes to develop, persistence, current binding maps or strong ownership.
No other unmerged experiment is included.

## Forensics before the change

From the prior CPU recordings, splitting get(TVariable) from get(long),
251/368 baseline current-lookup stack samples (68.2%) include equalsBase.
For the archived guarded-current-lookup recording it is 182/349 (52.1%).
Active-Mind stack sample routes are also mostly equalsBase (71.7%, 58.9%).
These are sampled routes, not exact call counts or CPU percentages. Raw JFRs
come from CPU forensics and guarded-current-lookup checkpoints, not this prototype.

A separate temporary entry-counter diagnostic ran two normal logged son queries
and their hypothesis optimization with all six integrated flags ON. The counters
were reset after query and before each optimizeHypothesis. Production source was
restored in finally; only the script, reviewable patch and evidence are persisted.
No sampler/profiler runs in that diagnostic. It is a single-thread workload:
plain counters are not a concurrency-safe production telemetry facility. Ignore
its recorded timings; counters and extra ThreadLocal reads perturb performance.
Raw and final hypothesis text match the uninstrumented baseline, stderr is empty.

| First optimization entry counts | Calls |
| --- | ---: |
| ArgumentsList.equalsBase | 14,437,010 |
| Operand positions examined | 18,160,054 |
| Argument.getValue, all | 149,118,394 |
| Argument.getValue inside equalsBase | 100,178,508 |
| TVariable.getValue, all | 75,747,028 |
| TVariable.getValue inside equalsBase | 43,648,177 |
| TVariable.activeMind | 87,555,216 |
| TVariable.setMind | 3,869,968 |
| Same selected referent on entry | 3,861,441 |
| Different selected referent | 8,527 |
| No ThreadLocal reference on entry | 0 |

Both iterations find 99.7797% repeated same-context selections. The second has
slightly different value-read counts; full raw/final logical texts still match.
EqualsBase performs about 5.52 Argument value reads per operand position. This
is a lead for a future value-resolution change; the old heavy guard prototype
is not reinstated and no expression rewrite is included here.

## Minimal prototype and semantic boundary

For an exact built-in TVariable, setMind reads its existing thread-local weak
reference. If it already refers to the requested Mind, preserve it; otherwise
perform the original new WeakReference/set. Custom TVariable subclasses use the
original path. All owner-update conditions still execute on every call, in the
original order, including custom Mind.getNext/getId callbacks and exceptions.
No early return skips owner qualification or reentrant callback actions.

This only avoids redundant weak-reference allocation and ThreadLocal writes.
The active context remains weak and thread confined. Cleared references are
replaced on reselection; historical fallback to the default owner remains.
Private WeakReference object identity can differ; it is not a public context
API contract. There is no memoized value, new manager, shared Mind cache or
change to factory hydration. Null selection keeps historical owner/failure behavior.

## Qualification at code checkpoint

Local Java 17.0.20 OFF/ON with all six integrated flags ON:
- 23 focused checks: root/child switching, owner identity, repeated selection,
  manually cleared weak reference, unset/null/failure cases, callback counts,
  callback exceptions/order and reentrant selection, custom variable, persistent
  owner qualification on repeated selection, concurrent siblings/main-thread view.
- 15 existing single-TValue-lookup boundaries.
- TValue DB reopen/hydration, rollback/commit and collision runner.
- Latent substitution corpus, 20 transaction operations, identical saved states.
- Candidate concurrency passes OFF/ON, three iterations each.

Dedicated CI: Java 8/21/26 x six integrated flags OFF/ON; new flag OFF/ON in each
job, plus cross-context projection and candidate concurrency. Pending at first push.

## Measurement protocol

Four fresh JVMs in ON/OFF/OFF/ON order, six son optimization samples each;
discard first two, report last-four medians. All six integrated flags stay ON.
512 MB heap, Java 17.0.20. Wall and main-thread CPU plus main-thread allocated
bytes, without counter patch, profiler or verification flags. Full raw/final
hypothesis text and result/solution/value counts are compared across all runs.
Single local workload; no universal speedup or retained-heap claim.

See active-mind-reuse-evidence and value-read-forensics-evidence for scripts/logs.
No merge, release, deploy or default enable is authorized by this checkpoint.
