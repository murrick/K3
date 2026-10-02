# Runtime optimization defaults

The seven integrated optimizations are enabled when their properties are absent.
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

For example, `-Dkanger.experiment.residentBaseComparison=false` disables only
the resident operand prefilter. Remove the seven explicit `=true` options from
launch.json to use the same enabled configuration automatically. Any explicitly
false property remains disabled; explicit non-true strings retain Boolean parsing
behavior. Verification, profiling and shadow properties still default to false.
No unmerged experimental optimization is enabled.

Base: develop/3.8.0 at 0dcec0c4e86ccb013af4e75b88d8336d4b1f1693.
Rick accepted enabled defaults after the local resident comparison qualification
on 2026-10-02. Source changes only select defaults; algorithms/guards/fallbacks,
storage and knowledge representation are unchanged. Earlier experiment reports
describe their historical default-OFF checkpoints.

Qualification compares three fresh-process modes: properties unset, all seven
explicitly true, all seven explicitly false. It exercises resident/callback and
single-lookup boundaries, index preservation/reopen, latent corpus, 20-operation
transaction saved-state equality, and six complete raw/optimized hypothesis
snapshots for son(John,x). CI runs this on Java 8/21/26. General CI continues to
exercise explicit TValue OFF/ON/verify mixed with the new enabled defaults.

The algorithms were previously qualified independently, in combination and
locally. Defaults qualification verifies that the parameter-free configuration
retains the results of both explicit configurations; it is not a timing benchmark.
