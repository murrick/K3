# Guarded compact cause weights

This opt-in change reduces repeated argument resolution in CachedDomain cause
selection. It is isolated from experiment/3.8.0-set-08-02-profile at fd84655 onto
develop/3.8.0 at f63063be8110e0b72fd2fe6ac34dd94ed588e11c.
Only CachedDomain changes in production; Linker, TValueFactory, persistence,
cause memo invalidation, and default settings retain their baseline behavior.

## Modes

All properties default to false. Enable the compact path with both:

```text
-Dkanger.experiment.resolvedCauseWeights=true
-Dkanger.experiment.compactCauseWeights=true
```

Add `-Dkanger.experiment.shadowCauseWeights=true` for verification: guarded
predictions are compared with reference weights and the final selected set.
Unsupported inputs use only the reference path, including in verification.
The earlier collection-based resolved calculation remains available when
resolvedCauseWeights is true and compactCauseWeights is false. It is not the
recommended performance configuration. Shadow-only mode is the earlier
unguarded diagnostic and should not be used to qualify unsupported inputs.

## Preserved selection semantics

Each nonempty own argument contributes one point if any donor argument has the
same resolved term ID. Own duplicates count separately; donor duplicates do
not multiply a point. If distinct weight groups exist, only the minimum group
is removed: intermediate groups must remain. Selection and its existing memo
are otherwise unchanged.

The compact calculation uses one primitive own-ID buffer per selection and a
reusable donor-ID buffer local to that call. Explicit lengths exclude unused
tails; IDs 0 and Long.MAX_VALUE are ordinary values. There is no cross-call
resolved-value cache or new stored knowledge.

Exact-class guards accept Mind, CachedDomain, Cause, Argument and the built-in
TERM/TVariable/TValue objects (plus empty arguments). Custom classes, functions,
missing objects and failed resolution fall back to the original loop. The
reference loop remains available with both optimization flags OFF.

## Evidence and qualification

The source experiment's executable commit 8693d1c passed
[Java 8/21 × TValue OFF/ON qualification](https://github.com/murrick/K3/actions/runs/35331977127)
in reference, compact and verification modes. Each matrix cell runs the existing
123-case corpus, weight boundaries, 20-step transaction state comparisons and
six persistent-Mind fault cases. The isolated branch must pass its own matrix;
the source experiment's result alone does not qualify this extraction.

CauseWeightFallbackRunner covers a throwing custom Argument and a missing stored
TERM in OFF/ON/verify. It checks selected causes, exactly one original diagnostic
and actual guard fallback. It reproduces a prior verify-only exception; the
guard now prevents speculative verification after fallback.

The isolated extraction removes only the temporary cause-memo timing/candidate
profiler. Weight verification and eligibility/fallback counters are retained
for qualification. Rotation-frontier experiments and their instrumentation
are excluded. DUMB2 changes in the base are retained unchanged.

Earlier Java 17 experiment measurements, not new measurements of this branch:
set_08_02 whole-method medians improved about 5–6% in both measurement orders;
main-thread cumulative allocation fell about 3.4% versus the original path.
These are warmed local measurements, not retained heap, worker allocations,
cold-start guarantees, or Java 8/21 performance results. Raw data and methodology
remain in docs/set-08-02-profile.md on the source experimental branch.

No merge or default activation is part of this extraction.
