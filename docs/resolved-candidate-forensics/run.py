from pathlib import Path
import gzip, json, re, subprocess
root=Path(__file__).parent
expected=json.loads((root/'expected.json').read_text())
flags=['preserveTValueIndex','versionedSolveSync','resolvedCauseWeights','compactCauseWeights','candidateMembershipFilter','singleTValueLookup','residentBaseComparison','compactFindSnapshots']
for label,mode,runner in [('clean','reference','SonProfileRunner'),('profile-1','classes','ResolvedCandidateProfileRunner'),('profile-2','classes','ResolvedCandidateProfileRunner')]:
    prefix=root/label
    command=['java','-Xmx512m']+['-Dkanger.experiment.'+f+'=true' for f in flags]+['-Dbench.samples=6','-cp','../build/resolved-candidate-'+mode+':kanger/resources:kanger-udf/src:lib/jline-3.13.0.jar','org.kanger.'+runner]
    with prefix.with_suffix('.log').open('w') as out,prefix.with_suffix('.err').open('w') as err:
        subprocess.run(command,stdout=out,stderr=err,check=True,timeout=300)
    data=prefix.with_suffix('.log').read_bytes();text=data.decode()
    assert not prefix.with_suffix('.err').read_bytes()
    assert re.findall(r'^SAMPLE (\d+) .* raw=18 optimized=6 solutions=0 values=0$',text,re.M)==list(map(str,range(6)))
    for key,value in expected.items():assert re.findall(r'^'+key+r' (.*)$',text,re.M)==[value]*6
    prefix.with_suffix('.log.gz').write_bytes(gzip.compress(data,mtime=0));prefix.with_suffix('.log').unlink()
    print('DONE',label,'oracle_snapshots=6',flush=True)
