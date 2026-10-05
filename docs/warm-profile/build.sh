#!/usr/bin/env bash
set -euo pipefail
java -jar ../tooling/ecj.jar -1.8 -nowarn -cp lib/jline-3.13.0.jar -d ../build/warm-profile-classes @docs/warm-profile/sources.txt
java -jar ../tooling/ecj.jar -17 -nowarn -cp ../build/warm-profile-classes -d ../build/warm-profile-classes docs/warm-profile/WarmProfileRunner.java
java -jar ../tooling/ecj.jar -17 -nowarn -d ../build/warm-profile-tools docs/warm-profile/ReadWarmProfile.java
