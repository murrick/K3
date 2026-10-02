#!/usr/bin/env bash
set -euo pipefail
# Repository root; ECJ is an external prerequisite, local analysis runs on Java17.
java -jar ../tooling/ecj.jar -1.8 -nowarn -cp lib/jline-3.13.0.jar -d ../build/defaults-classes @docs/resident-base-comparison-evidence/sources.txt
java -jar ../tooling/ecj.jar -17 -nowarn -cp ../build/defaults-classes -d ../build/post-defaults-tools docs/post-defaults-profile/WarmSonProfileRunner.java docs/post-defaults-profile/ReadWarmProfile.java
