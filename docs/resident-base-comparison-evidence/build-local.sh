#!/usr/bin/env bash
set -euo pipefail
# Run from repository root; ../tooling/ecj.jar is an external local prerequisite.
java -jar ../tooling/ecj.jar -1.8 -nowarn -cp lib/jline-3.13.0.jar -d ../build/base-classes @docs/resident-base-comparison-evidence/sources.txt
java -jar ../tooling/ecj.jar -1.8 -nowarn -cp ../build/base-classes -sourcepath kanger-qualification/src -d ../build/base-classes kanger-qualification/src/org/kanger/SonProfileRunner.java kanger-qualification/src/org/kanger/ResidentBaseComparisonRunner.java kanger-qualification/src/org/kanger/LatentSubstitutionCorpusRunner.java kanger-qualification/src/org/kanger/LatentSolveSyncTransactionRunner.java kanger-qualification/src/org/kanger/KangerRuleCandidateConcurrencyRunner.java kanger-qualification/src/org/kanger/SingleTValueLookupRunner.java kanger-qualification/src/org/kanger/TValueIndexReopenRunner.java
