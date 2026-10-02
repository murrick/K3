# Allocation prototypes with resident comparison enabled

Base: live develop/3.8.0 verified at 3a2a6f0bccac11ac0d646178c4384ba73d7a5260.
Branch: experiment/3.8.0-resident-allocation.
All seven integrated optimizations are enabled by default. The two additional
prototype properties remain default OFF:
`-Dkanger.experiment.directPredicateName=true`
`-Dkanger.experiment.compactFindSnapshots=true`
Restart the JVM when changing either property.

The recent warm profile attributes about 22–23% of allocation sample weight to
Predicate.getName and Escalera.find. That is an attribution lead, not a claimed
time or allocation gain. The prior combined-allocation branch used the old
six-flag baseline. Only its four-file minimal production slice is carried here;
no old experimental branch is merged wholesale.

## Preserved boundaries

Predicate.getName resolves the name donor and its value on every call. Only an
already String value is returned directly. All other values retain the original
`value + ""` conversion, including null and custom toString behavior. No name
cache, value identity or persistence semantics is added.

Escalera.findCandidates is an internal iteration snapshot for three factory
loops. Exact Escalera uses empty/singleton immutable snapshots for cardinality
zero/one; larger sets retain the same HashSet copy and order as reference find.
Custom cache subclasses retain their find callback. Public find remains a fresh
mutable caller-owned set; no internal live collection escapes.

The seven integrated defaults, guards and fallbacks are unchanged. Existing
resident comparison still falls back for different CVar IDs and custom/context
cases, including parent key mutation/failure callbacks.

## Qualification and measurement

Local Java17 checks all four combinations 00/10/01/11 with the seven integrated
flags ON: 24 name boundaries, 23 snapshot boundaries, 48 resident boundaries,
15 single-lookup boundaries, latent corpus, DB reopen/hydration/rollback/collision,
20 transaction operations and byte-equal saved states. Dedicated CI runs these
combinations on Java 8/21/26 with all seven prior flags explicitly OFF/ON, plus
cross-context projection and candidate concurrency in combined mode.

Timing uses ten sequential fresh JVMs: clean pinned reference, 00/10/01/11,
11/01/10/00, clean reference. Six samples each, discard first two, compare
last-four medians; seven integrated flags ON, 512 MB heap, Java17.0.20. No JFR,
counter patch, verify or shadow options run during timing. Compare complete raw
and final hypothesis texts and logical result/solution/value counts.
Clean reference classes and the identical SonProfileRunner come from the enabled
defaults qualification build. Sources and input are the pinned base; no new
optimization code is present in that control. Allocation is measured as actual
main-thread allocated bytes, not retained heap. Do not add these gains to earlier
experiments or assume the same effect on other workloads.

## Completed results

Code checkpoint: accc82156306f2c8593e1dc4949420c1e3e4833a.
All five triggered workflows passed, including all six Java8/21/26 x seven-prior-
flags-OFF/ON jobs and general KANGER CI. Dedicated run:
https://github.com/murrick/K3/actions/runs/37044274199.
Final metadata is in resident-allocation-evidence/ci-status.json.

All four combinations pass local qualification, with identical saved states.
Public snapshot ownership, multi-candidate order, callback mutation isolation,
custom cache exceptions, mutable/lazy names and resident parent-key callbacks
are retained by the focused fixtures. Timing matches all 60 raw/final texts;
logical counts match (raw18/optimized6/solutions0/values0), stderr empty.
The control classes contain neither new prototype flag, no counter fields,
and the runner bytecode matches exactly; see build-manifest.json.

| JVM | Mode (name/snapshot) | Wall median s | Main CPU median s | Allocated MB |
| --- | --- | ---: | ---: | ---: |
| 1 | Clean reference | 7.306 | 7.137 | 5423.3 |
| 2 | 00 | 7.184 | 7.020 | 5443.0 |
| 3 | 10 | 7.650 | 7.483 | 4835.8 |
| 4 | 01 | 7.233 | 7.087 | 4855.9 |
| 5 | 11 | 7.786 | 7.564 | 4167.8 |
| 6 | 11 | 7.472 | 7.344 | 4290.5 |
| 7 | 01 | 7.075 | 6.926 | 4832.7 |
| 8 | 10 | 7.720 | 7.588 | 4918.1 |
| 9 | 00 | 8.185 | 7.967 | 5386.1 |
| 10 | Clean reference | 7.428 | 7.271 | 5313.3 |

Against experimental 00, combined allocation reductions are 23.43% and 20.34%,
but wall reductions change sign (-8.37%, +8.72%). Against clean reference,
combined allocation reductions are 23.15% and 19.25%, while wall time increases
6.57% and 0.58% (CPU increases 5.99%, 1.00%). This does not confirm an elapsed
or CPU gain for the combination on this workload and baseline.

Name alone reduces allocated bytes by 11.15% and 8.69% against 00, but compared
with clean reference runs slower in both orders. Adding it to snapshot-only
further reduces allocated bytes 14.17% and 11.22%, while increasing wall time
7.63% and 5.61% (CPU 6.74%, 6.03%). Keep name conversion deferred for the latency
objective; no causal JIT explanation has been established by these measurements.

Snapshot-only reduces allocated bytes by 10.79% and 10.28% against 00.
Its wall gain against clean reference is 0.99% and 4.76%, with CPU
0.70% and 4.74%. This is a modest paired signal, not a confidence interval or
general speedup claim. The final 00 control is substantially slower than clean
reference, so its apparent 13.57% snapshot wall gain must not be used as the
main performance conclusion. Snapshot-only in this build also retains the
disabled name prototype's bytecode; isolate the snapshot slice before deciding
on latency, rather than infer a pure snapshot gain from this combined build.

Decision: preserve the qualified default-OFF combination as evidence, do not
merge or enable it. Next test only compactFindSnapshots on the current base,
with the name production patch absent; retain callback/transaction equivalence
and compare against clean base in both orders. Extra allocation saving alone
does not justify adding the slower name path to the inference workload.

No develop change, merge or default enable of these two prototypes in this checkpoint.
