import subprocess
from pathlib import Path
flags=['preserveTValueIndex','versionedSolveSync','resolvedCauseWeights','compactCauseWeights','candidateMembershipFilter','singleTValueLookup']
base=['java','-Xmx512m','-Dbench.samples=8']+['-Dkanger.experiment.'+f+'=true' for f in flags]
for i,mode in enumerate(['false','true','true','false'],1):
    prefix=Path('docs/combined-allocation-evidence')/('set08-%d-%s'%(i,mode))
    cmd=base+['-Dkanger.experiment.directPredicateName='+mode,'-Dkanger.experiment.compactFindSnapshots='+mode,'-cp','../build/combined-classes:kanger/resources:kanger-udf/src:lib/jline-3.13.0.jar','org.kanger.Set08ProfileRunner']
    with open(str(prefix)+'.log','w') as out,open(str(prefix)+'.err','w') as err: subprocess.run(cmd,stdout=out,stderr=err,check=True)
    assert 'SET08_PROFILE_OK samples=8' in Path(str(prefix)+'.log').read_text()
    print('DONE',prefix,flush=True)
