#!/usr/bin/env bash
set -euo pipefail
# Run from repository root; external ECJ prerequisite. Java8 bytecode, Java17 local execution.
java -jar ../tooling/ecj.jar -1.8 -nowarn -cp lib/jline-3.13.0.jar -d ../build/deletion-forensics-production @docs/resident-base-comparison-evidence/sources.txt
java -jar ../tooling/ecj.jar -1.8 -nowarn -cp ../build/deletion-forensics-production -d ../build/deletion-forensics-classes docs/duplicate-deletion-forensics/DuplicateDeletionForensics.java
java -Xmx512m -cp ../build/deletion-forensics-classes:../build/deletion-forensics-production:kanger/resources:kanger-udf/src:lib/jline-3.13.0.jar DuplicateDeletionForensics > docs/duplicate-deletion-forensics/result.log 2> docs/duplicate-deletion-forensics/result.err
