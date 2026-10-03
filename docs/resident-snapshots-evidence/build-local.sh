#!/usr/bin/env bash
set -euo pipefail
# Run from repository root; ECJ is an external local prerequisite.
java -jar ../tooling/ecj.jar -1.8 -nowarn -cp lib/jline-3.13.0.jar -d ../build/resident-snapshots-classes @docs/resident-base-comparison-evidence/sources.txt
java -jar ../tooling/ecj.jar -1.8 -nowarn -cp ../build/resident-snapshots-classes -sourcepath kanger-qualification/src -d ../build/resident-snapshots-classes kanger-qualification/src/org/kanger/SonProfileRunner.java
# qualify.py compiles the focused/corpus/transaction runners before executing.
