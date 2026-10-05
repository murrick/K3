from pathlib import Path
import subprocess,re
flags=['preserveTValueIndex','versionedSolveSync','resolvedCauseWeights','compactCauseWeights','candidateMembershipFilter','singleTValueLookup','residentBaseComparison','compactFindSnapshots']
subprocess.run(['java','-jar','../tooling/ecj.jar','-17','-nowarn','-d','../build/warm-profile-tools','docs/warm-profile/ReadWarmProfile.java'],check=True)
for i in [2,3]:
    prefix=Path('docs/warm-profile')/f'profile-{i}'
    jfr=f'../build/warm-profile-{i}.jfr'
    cmd=['java','-Xmx512m','-XX:FlightRecorderOptions=stackdepth=256',f'-XX:StartFlightRecording=filename={jfr},settings=profile,dumponexit=true','-Dbench.samples=6','-Dbench.allocation=true']+['-Dkanger.experiment.'+f+'=true' for f in flags]+['-cp','../build/warm-profile-classes:kanger/resources:kanger-udf/src:lib/jline-3.13.0.jar','org.kanger.WarmProfileRunner']
    with open(str(prefix)+'.log','w') as out,open(str(prefix)+'.err','w') as err: subprocess.run(cmd,stdout=out,stderr=err,check=True,timeout=300)
    with open(str(prefix)+'.tsv','w') as out:subprocess.run(['java','-cp','../build/warm-profile-tools','ReadWarmProfile',jfr],stdout=out,check=True,timeout=120)
    assert not Path(str(prefix)+'.err').read_text()
    log=Path(str(prefix)+'.log').read_text()
    assert re.findall(r'^SAMPLE (\d+) ',log,re.M)==list(map(str,range(6)))
    assert len(re.findall(r'^RAW ',log,re.M))==len(re.findall(r'^OPTIMIZED ',log,re.M))==6
    print('PROFILE_COMPLETE',i,flush=True)
