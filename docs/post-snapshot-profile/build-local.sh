#!/usr/bin/env bash
set -euo pipefail
# Repository root; ECJ is an external prerequisite, local analysis runs on Java17.
java -jar ../tooling/ecj.jar -1.8 -nowarn -cp lib/jline-3.13.0.jar -d ../build/post-snapshot-classes @docs/resident-base-comparison-evidence/sources.txt
java -jar ../tooling/ecj.jar -17 -nowarn -cp ../build/post-snapshot-classes -d ../build/post-snapshot-tools docs/post-snapshot-profile/WarmSonProfileRunner.java docs/post-snapshot-profile/ReadWarmProfile.java
