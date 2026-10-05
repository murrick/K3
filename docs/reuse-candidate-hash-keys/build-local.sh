#!/usr/bin/env bash
set -euo pipefail
java -jar ../tooling/ecj.jar -1.8 -nowarn -cp lib/jline-3.13.0.jar -d ../build/key-reuse-classes @docs/resident-base-comparison-evidence/sources.txt
java -jar ../tooling/ecj.jar -1.8 -nowarn -cp ../build/key-reuse-classes -sourcepath kanger-qualification/src -d ../build/key-reuse-classes kanger-qualification/src/org/kanger/{ReuseCandidateHashKeysRunner,CompactFindSnapshotsRunner,ResidentBaseComparisonRunner,SingleTValueLookupRunner,TValueIndexReopenRunner,LatentSubstitutionCorpusRunner,LatentSolveSyncTransactionRunner,KangerRuleCandidateConcurrencyRunner,SonProfileRunner}.java
