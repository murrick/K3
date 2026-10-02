import subprocess
from pathlib import Path
flags=['preserveTValueIndex','versionedSolveSync','resolvedCauseWeights','compactCauseWeights','candidateMembershipFilter','singleTValueLookup','residentBaseComparison']
base=['java','-Xmx512m']+['-Dkanger.experiment.'+f+'=true' for f in flags]+['-Dbench.samples=6','-Dbench.allocation=true','-cp','../build/resident-allocation-classes:kanger/resources:kanger-udf/src:lib/jline-3.13.0.jar','org.kanger.SonProfileRunner']
for i,mode in enumerate(['reference','00','10','01','11','11','01','10','00','reference'],1):
    if mode == 'reference':
        cmd=base.copy()
        cmd[cmd.index('../build/resident-allocation-classes:kanger/resources:kanger-udf/src:lib/jline-3.13.0.jar')]='../build/defaults-classes:kanger/resources:kanger-udf/src:lib/jline-3.13.0.jar'
    prefix=Path('docs/resident-allocation-evidence')/('combined-%d-%s'%(i,mode))
    if mode != 'reference': cmd=base[:2]+['-Dkanger.experiment.compactFindSnapshots='+str(mode[1]=='1').lower(),'-Dkanger.experiment.directPredicateName='+str(mode[0]=='1').lower()]+base[2:]
    with open(str(prefix)+'.log','w') as out,open(str(prefix)+'.err','w') as err:
        subprocess.run(cmd,stdout=out,stderr=err,check=True)
    print('DONE',prefix,flush=True)
