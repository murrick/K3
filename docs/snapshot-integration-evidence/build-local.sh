#!/usr/bin/env bash
set -euo pipefail
# Run from repository root; ECJ is an external local prerequisite.
java -jar ../tooling/ecj.jar -1.8 -nowarn -cp lib/jline-3.13.0.jar -d ../build/snapshot-integration-classes @docs/resident-base-comparison-evidence/sources.txt
runners=(CompactFindSnapshotsRunner ResidentBaseComparisonRunner SingleTValueLookupRunner TValueIndexBoundaryRunner TValueIndexReopenRunner LatentSubstitutionCorpusRunner LatentSolveSyncTransactionRunner SonProfileRunner)
sources=()
for runner in "${runners[@]}"; do sources+=("kanger-qualification/src/org/kanger/$runner.java"); done
java -jar ../tooling/ecj.jar -1.8 -nowarn -cp ../build/snapshot-integration-classes -sourcepath kanger-qualification/src -d ../build/snapshot-integration-classes "${sources[@]}"
python docs/optimization-defaults/qualify.py --output docs/snapshot-integration-evidence --classpath ../build/snapshot-integration-classes:kanger/resources:kanger-udf/src:lib/jline-3.13.0.jar
