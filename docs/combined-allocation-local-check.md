# Local combined allocation comparison

Use experimental branch `experiment/3.8.0-combined-allocation` (qualified production checkpoint b5ae6ab; evidence checkpoint 741236f). This code is not in develop/3.8.0. Both new flags default OFF. No merge required to test the experimental branch.

Fetch the branch and switch to it in your existing clone, then run `mvn clean verify`. Use the usual org.kanger.Kanger launcher (`-S`) and the same Java/heap settings for both runs.

Keep these six previously integrated flags ON in launch.json vmArgs throughout:

```
-Dkanger.experiment.preserveTValueIndex=true -Dkanger.experiment.versionedSolveSync=true -Dkanger.experiment.resolvedCauseWeights=true -Dkanger.experiment.compactCauseWeights=true -Dkanger.experiment.candidateMembershipFilter=true -Dkanger.experiment.singleTValueLookup=true
```

Reference: append
```
-Dkanger.experiment.directPredicateName=false -Dkanger.experiment.compactFindSnapshots=false
```

Combined: append
```
-Dkanger.experiment.directPredicateName=true -Dkanger.experiment.compactFindSnapshots=true
```

Restart the entire JVM after changing flags. Keep verification/shadow flags absent during timing. Use the same natives.k input in both modes.

In each JVM repeat `options test 08_02` six times and the query `?$x son(John,x);` with the same hypothesis-processing operation six times. Keep the first two as warm-up; compare medians of the last four. If practical, repeat in reverse order (combined then reference). Compare the full returned hypotheses/results as well as time, and check ordinary DB reopen, transactions and collision workflows.

This compares the incremental effect of the two new paths, not all optimization flags OFF against all flags ON. Cloud Java17 evidence is promising for the hypothesis scenario; local Mac Java26 performance has not yet been established. No need to run all four combinations initially: both-OFF versus both-ON is the first local comparison.
