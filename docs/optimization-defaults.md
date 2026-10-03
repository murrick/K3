# Runtime optimization defaults

The eight integrated optimizations are enabled when their properties are absent.
An explicit `-Dkanger.experiment.<name>=false` selects the existing reference path.
The historical property names remain compatible with existing launch configurations.
Restart the JVM after changing properties (some selections are class-initialized).

| Property suffix | Default |
| --- | --- |
| preserveTValueIndex | true |
| versionedSolveSync | true |
| resolvedCauseWeights | true |
| compactCauseWeights | true |
| candidateMembershipFilter | true |
| singleTValueLookup | true |
| residentBaseComparison | true |
| compactFindSnapshots | true |

For example, `-Dkanger.experiment.residentBaseComparison=false` disables only
the resident operand prefilter. Remove the eight explicit `=true` options from
launch.json to use the same enabled configuration automatically. Any explicitly
false property remains disabled; explicit non-true strings retain Boolean parsing
behavior. Verification, profiling and shadow properties still default to false.
Only the paths listed above are enabled by default.

The original seven defaults were integrated on base develop/3.8.0 at
0dcec0c4e86ccb013af4e75b88d8336d4b1f1693, following Rick's local resident
comparison acceptance on 2026-10-02. That default selection change retained the
previously qualified algorithms/guards/fallbacks. Compact snapshots are described
below. Earlier experiment reports describe their historical default-OFF checkpoints.

Qualification compares three fresh-process modes: properties unset, all eight
explicitly true, all eight explicitly false. It exercises candidate snapshot ownership/order/callback boundaries,
resident/callback and
single-lookup boundaries, index preservation/reopen, latent corpus, 20-operation
transaction saved-state equality, and six complete raw/optimized hypothesis
snapshots for son(John,x). CI runs this on Java 8/21/26. General CI continues to
exercise explicit TValue OFF/ON/verify mixed with the new enabled defaults.

The algorithms were previously qualified independently, in combination and
locally. Defaults qualification verifies that the parameter-free configuration
retains the results of both explicit configurations; it is not a timing benchmark.

## Compact candidate snapshots integration

Prepared on base develop/3.8.0 at 3a2a6f0bccac11ac0d646178c4384ba73d7a5260.
Rick accepted retaining compactFindSnapshots on 2026-10-03 after laptop OFF/ON
timing checks. The proposed integration enables it when the property is absent;
explicit false retains the original cache.find copy path. The selection is
class-initialized, so restart the JVM when changing it. Exact Escalera instances
use empty/singleton snapshots internally; custom caches and multi-candidate
buckets preserve the reference path. Public find still returns a mutable owned
set. Storage formats and index persistence are unchanged.

Measured warm processing reduction was 6.3–7.1% against a clean base and 3.9–5.2%
against OFF in the same cloud build, with 10–11% less main-thread allocation.
Laptop warm medians improved 6.5% for 08_02 and 4.5% for son(John,x). These are
small-corpus observations, not a general speed guarantee. Historical evidence
and raw timings are in docs/resident-snapshots-evidence. Integration qualification
adds the snapshot runner to defaults / explicit ON / explicit OFF comparisons
and retains the Java 8/21/26 matrix with seven prior optimizations OFF/ON.
