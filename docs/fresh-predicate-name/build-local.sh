#!/usr/bin/env bash
set -euo pipefail
java -jar ../tooling/ecj.jar -1.8 -nowarn -cp lib/jline-3.13.0.jar -d ../build/fresh-name-classes @docs/resident-base-comparison-evidence/sources.txt
java -jar ../tooling/ecj.jar -1.8 -nowarn -cp ../build/fresh-name-classes -sourcepath kanger-qualification/src -d ../build/fresh-name-classes kanger-qualification/src/org/kanger/{FreshPredicateNameRunner,CompactFindSnapshotsRunner,ResidentBaseComparisonRunner,SingleTValueLookupRunner,TValueIndexReopenRunner,LatentSubstitutionCorpusRunner,LatentSolveSyncTransactionRunner,KangerRuleCandidateConcurrencyRunner,SonProfileRunner}.java
