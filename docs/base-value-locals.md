# Pair-local base comparison experiment

Base: `develop/3.8.0` at `1828d7164159cb04df00dedd5c27e18d556af4ea`.
Separate branch; no changes to develop. Java 17.0.20, ECJ Java 8 target,
`-Xmx512m`, isolated user home in each JVM. All six integrated optimizations ON.

## Candidate boundary

`kanger.experiment.baseValueLocals`, default OFF, attempts to resolve the
left/right value once after the historical left-to-right emptiness checks.
Only exact ArgumentsList/Argument and resident built-in Term/TValue/TVariable
are eligible. Mind hierarchy, term factories, active variable context and
current TValue are guarded. Functions, lazy references, custom arguments,
custom values, custom terms and custom contexts retain the historical path.
Custom CVariable parents also fall back. No values survive the current pair;
the original identity/parent relation is unchanged.

The guards cost time: preflighting a TVariable itself reads its current binding,
and parent checks traverse context maps. This experiment tests whether fewer
subsequent resolutions justify that overhead, not an unguarded semantic change.

## Sequential timings

Four fresh JVMs, OFF/ON/ON/OFF, three samples per JVM, first excluded from warm
median; no concurrent benchmark or sampling. Only two warm samples per JVM.

| Order | Mode | First optimize (s) | Warm optimize median (s) |
|---|---|---:|---:|
| 1 | OFF | 16.148 | 10.859 |
| 2 | ON | 15.721 | 12.446 |
| 3 | ON | 12.816 | 10.666 |
| 4 | OFF | 15.626 | 11.752 |

ON is 14.6% slower in the first pairing and 9.2% faster in the reverse pairing.
Environment drift and inconsistent direction do not establish an improvement.
Do not promote or default-enable this candidate. After timing, the eligibility
helper was made final to prevent same-package subclass overrides; boundary
and regression checks used the rebuilt final version.
Production Argument/ArgumentsList changes and the boundary runner were then
removed and archived in `base-operands-evidence/base-value-locals-inconclusive.patch`.
Apply that patch to reproduce the baseValueLocals tests; the branch's active
production delta is solely the separate empty-deletion-layer experiment.

All 12 sorted raw/optimized hypothesis snapshots match (18 raw, six final,
unknown result, zero solutions/values). Last nested Linker statistics match
as well; these are not aggregate candidate-pair traces.

## Qualification

- 25 boundary assertions per OFF/ON mode: empty/equal/mismatched lists;
  CVariable parent/child, siblings and removed parent; lazy hydration;
  rebinding/clearing, resident TValue, active child/parent context;
  custom Argument and custom TValue resolution counts and short circuit.
- Existing regression corpus completes in both modes with LATENT_CORPUS_PASS.
- All 20 transaction operations pass in both modes; full textual state
  projections are byte-identical.

No cross-JDK CI or full substitution trace qualification has been run for
this candidate. Raw logs and reusable diagnostic runner are retained here;
build/run scripts are in `base-operands-evidence/`.

## Next boundary

Mind.isUnitDeleted looks up restored and deleted sets in every context layer,
under that layer's existing lock. Even a layer without either kind of marker
currently performs both keyed map reads. Measure actual empty-layer frequency
before introducing a shortcut. Restoration precedence, nearest layer order,
locking, nested rollback and external map mutation must remain observable.
