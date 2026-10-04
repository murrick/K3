# Domain binding caller and context forensics

Live base `develop/3.8.0 @ 1c943d02d39bd33ebd91fabb7c4190112ab49459`, checked 2026-10-04. Branch `experiment/3.8.0-domain-binding-forensics`. Production is unchanged. All eight integrated optimizations retain ON defaults; no rejected prototype is included.

## Measurement boundary

Two fresh sequential JVMs × six son(John,x) samples; discard first two per JVM for warm counters. Count only the main thread while optimizeHypothesis runs. A diagnostic copy instruments setter callsites in Step/Sapato, Rule and factories; nested tags retain the source chain. Domain entry compares the existing private Mind field with the requested reference. TVariable base-setter entry observes private activeMind before assignment, avoiding overridable public getMind callbacks. Callsites and full patch are recorded beside the report.

Original setters, enumeration/deletion checks, owner qualification and cause-memo handling still execute. IdentityHashMaps retain observed Domain objects until phase completion; the diagnostic weak-reference observation and retention can affect GC/lifetime. This build cannot qualify timing, concurrency scheduling or an optimization. Slight variation in empty-domain call counts is reported as a range without assigning a cause. Unexpected nested Domain.setMind would fail the diagnostic; none occurred.

All 12 full raw and optimized hypothesis texts match one another and the earlier clean-base reference (18 raw, six optimized, no solutions/values). Stderr is empty. Warm binding counts are internally reconciled: same+changed equals total for both Domain and variable observations.

## Warm counters per optimization

| Route | Domain.setMind calls | Same Domain Mind | Changed Domain Mind | Variable selections |
|---|---:|---:|---:|---:|
| Step.getData → Rule.setMind | 4,857,608–4,862,899 | 4,831,759–4,837,050 | 25,849 | 3,849,861 |
| Step.getData → Domain.setMind | 30,574 | 0 | 30,574 | 13,803 |
| DomainFactory commit | 133 | 133 | 0 | 45 |
| RuleFactory commit → Rule.setMind | 133 | 133 | 0 | 45 |
| RuleFactory domain publication | 133 | 133 | 0 | 45 |

Total 4,888,581–4,893,872 Domain selections. The Rule cache cascade accounts for 99.3664–99.3671%. Across routes, 98.8458–98.8471% of Domain calls request the existing Mind reference. The main cascade observes 2,257 distinct Domain objects; route identity sets overlap and must not be summed.

Of 3,863,799 variable base-setter selections, 3,860,206 already select the requested Mind (99.9070%) and 3,593 change it. This is not a count of safely removable calls: callbacks and owner qualification still matter. In this workload, zero variable selections combine an already-matching Domain Mind with a differing variable Mind. That absence does not qualify an identity shortcut for other inputs.

Sapato and other instrumented setter routes were not observed during this phase. Tags identify immediate binding callers, not yet the higher-level reason a Rule is being read (point lookup versus cache iteration or repeated factory lookup).

## Three exact-built-in counterexamples

Diagnostic DomainBindingWitness uses only exact Mind, Domain, Rule, Argument and TVariable objects. Root binds a variable to term a; child binds the same variable to b. Compare ordinary reselection with a diagnostic guard that skips setMind if the outer object's getMind already matches.

1. Bind Domain to root, then select its variable into child directly. Domain Mind remains root. Ordinary Domain.setMind(root) restores a; the guard leaves b.
2. A second Domain selects the shared variable into child. The first Domain still reports root. Ordinary reselection restores a; the guard leaves b.
3. Rule and its Domain already report root while their variable was selected into child. Ordinary Rule.setMind(root) restores a; a same-Rule-Mind guard leaves b.

Result: `DOMAIN_BINDING_WITNESS_OK scenarios=3 checks=21`. Each scenario uses separate fresh reference/naive fixtures and checks the precondition, final visible binding and unchanged outer Domain context. This is logical-result divergence, not merely a callback-count difference. It does not depend on custom subclasses, deletion callbacks or concurrent mutation.

## Decision and next boundary

Reject unconditional same-Mind early return at Domain or Rule level. Existing outer context does not certify inner variable selection. The live workload explains why such a shortcut could appear promising, while the witnesses establish why the naive implementation is incorrect.

A valid reduction needs a proved binding/observation boundary covering every propagated variable, mutable arguments, deletion state, owner qualification and custom callbacks. No such boundary is established by these counts. No production optimization, flag or default enable is introduced.

Next bounded investigation: separate Rule reads leading into Step.getData by point lookup and iterator/source caller. Avoiding a proven redundant read at its caller may remove an entire binding cascade, but candidate enumeration/result/callback equivalence must be established before changing it. This report does not authorize raw unbound cache reads or a persistent binding cache.

## Reproduction

From repository root with external ECJ and Java 17:

```sh
python docs/domain-binding-forensics/instrument.py
python docs/domain-binding-forensics/run.py
python docs/domain-binding-forensics/analyze.py
```

ECJ targets Java 8. Patched production copies and classes exist only under ../build/domain-binding-source and ../build/domain-binding-classes. Tracked production/qualification/workflow source remains base-identical. Full helper/runner, patch, callsites, schemas, logs and summary are beside this report. General CI is not requested for this diagnostic-only checkpoint.
