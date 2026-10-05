import subprocess,re
from pathlib import Path
flags=['preserveTValueIndex','versionedSolveSync','resolvedCauseWeights','compactCauseWeights','candidateMembershipFilter','singleTValueLookup','residentBaseComparison','compactFindSnapshots']
base=['java','-Xmx512m']+['-Dkanger.experiment.'+f+'=true' for f in flags]+['-Dbench.samples=6','-Dbench.allocation=true','-cp','../build/lazy-classification-classes:kanger/resources:kanger-udf/src:lib/jline-3.13.0.jar','org.kanger.SonProfileRunner']
for i,mode in enumerate(['reference','false','true','true','false','reference'],1):
    if mode == 'reference':
        cmd=base.copy()
        cmd[cmd.index('../build/lazy-classification-classes:kanger/resources:kanger-udf/src:lib/jline-3.13.0.jar')]='../build/lazy-classification-reference:kanger/resources:kanger-udf/src:lib/jline-3.13.0.jar'
    prefix=Path('docs/lazy-classification-sets')/('binding-%d-%s'%(i,mode))
    if mode != 'reference': cmd=base[:2]+['-Dkanger.experiment.lazyClassificationSets='+mode]+base[2:]
    with open(str(prefix)+'.log','w') as out,open(str(prefix)+'.err','w') as err:
        subprocess.run(cmd,stdout=out,stderr=err,check=True,timeout=300)
    text=Path(str(prefix)+'.log').read_text()
    assert re.findall(r'^SAMPLE (\d+) ',text,re.M)==[str(n) for n in range(6)],prefix
    assert len(re.findall(r'^RAW ',text,re.M))==len(re.findall(r'^OPTIMIZED ',text,re.M))==6,prefix
    assert not Path(str(prefix)+'.err').read_text(),prefix
    print('DONE',prefix,flush=True)
