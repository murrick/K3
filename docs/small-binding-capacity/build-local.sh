#!/usr/bin/env bash
set -euo pipefail
java -jar ../tooling/ecj.jar -1.8 -nowarn -cp lib/jline-3.13.0.jar -d ../build/small-binding-classes @docs/resident-base-comparison-evidence/sources.txt
java -jar ../tooling/ecj.jar -1.8 -nowarn -cp ../build/small-binding-classes -sourcepath kanger-qualification/src -d ../build/small-binding-classes kanger-qualification/src/org/kanger/{SmallBindingCapacityRunner,CompactFindSnapshotsRunner,ResidentBaseComparisonRunner,SingleTValueLookupRunner,TValueIndexReopenRunner,LatentSubstitutionCorpusRunner,LatentSolveSyncTransactionRunner,KangerRuleCandidateConcurrencyRunner,SonProfileRunner}.java
