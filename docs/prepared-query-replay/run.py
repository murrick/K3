from pathlib import Path
import gzip,subprocess,sys,os
root=Path(__file__).parent
cases=sys.argv[1]
modes=sys.argv[2:] or ['exact','prepared']
flags=['preserveTValueIndex','versionedSolveSync','resolvedCauseWeights','compactCauseWeights','candidateMembershipFilter','singleTValueLookup','residentBaseComparison','compactFindSnapshots']
for mode in modes:
    tag=os.environ.get('PREPARED_STUDY_TAG','')
    prefix=root/(cases+'-'+mode+('-'+tag if tag else ''))
    command=['java','-Xmx512m']+['-Dkanger.experiment.'+f+'=true' for f in flags]+['-cp','../build/prepared-query-classes:kanger/resources:kanger-udf/src:lib/jline-3.13.0.jar','org.kanger.PreparedQueryReplayStudy',mode,cases]
    props={'PREPARED_STUDY_FOCUS':'study.focusIndex','PREPARED_STUDY_REVERSE':'study.reverse','PREPARED_STUDY_SAMPLES':'study.samples','PREPARED_STUDY_MEASURE':'study.measure'}
    command[2:2]=['-D'+prop+'='+os.environ[env] for env,prop in props.items() if env in os.environ]
    with prefix.with_suffix('.log').open('w') as out,prefix.with_suffix('.err').open('w') as err:
        subprocess.run(command,stdout=out,stderr=err,check=True,timeout=300)
    data=prefix.with_suffix('.log').read_bytes()
    assert not prefix.with_suffix('.err').read_bytes()
    assert b'PREPARED_QUERY_STUDY_OK' in data
    prefix.with_suffix('.log.gz').write_bytes(gzip.compress(data,mtime=0));prefix.with_suffix('.log').unlink()
    print('DONE',cases,mode,flush=True)
