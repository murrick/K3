#!/usr/bin/env bash
set -euo pipefail
java -Xmx512m -XX:FlightRecorderOptions=stackdepth=256 -XX:StartFlightRecording=filename=../build/hypothesis-cpu.jfr,settings=profile,dumponexit=true -Dbench.samples=6 -Dkanger.experiment.preserveTValueIndex=true -Dkanger.experiment.versionedSolveSync=true -Dkanger.experiment.resolvedCauseWeights=true -Dkanger.experiment.compactCauseWeights=true -Dkanger.experiment.candidateMembershipFilter=true -Dkanger.experiment.singleTValueLookup=true -cp ../build/cpu-classes:kanger/resources:kanger-udf/src:lib/jline-3.13.0.jar org.kanger.SonProfileRunner > docs/cpu-forensics-evidence/profile.log 2> docs/cpu-forensics-evidence/profile.err
java -cp ../build/cpu-jfr-tools ReadExecutionProfile ../build/hypothesis-cpu.jfr > docs/cpu-forensics-evidence/execution.tsv
