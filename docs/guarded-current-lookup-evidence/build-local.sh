#!/usr/bin/env bash
set -euo pipefail
# Run from repository root. ECJ jar is an external local tooling prerequisite.
java -jar ../tooling/ecj.jar -1.8 -nowarn -cp lib/jline-3.13.0.jar -d ../build/current-classes @docs/guarded-current-lookup-evidence/sources.txt
java -jar ../tooling/ecj.jar -1.8 -nowarn -cp ../build/current-classes -sourcepath kanger-qualification/src -d ../build/current-classes kanger-qualification/src/org/kanger/GuardedCurrentLookupRunner.java kanger-qualification/src/org/kanger/LatentSubstitutionCorpusRunner.java kanger-qualification/src/org/kanger/LatentSolveSyncTransactionRunner.java kanger-qualification/src/org/kanger/KangerRuleCandidateConcurrencyRunner.java
