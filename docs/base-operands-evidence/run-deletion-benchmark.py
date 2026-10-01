import subprocess
from pathlib import Path
flags=['preserveTValueIndex','versionedSolveSync','resolvedCauseWeights','compactCauseWeights','candidateMembershipFilter','singleTValueLookup']
base=['java','-Xmx512m']+['-Dkanger.experiment.'+f+'=true' for f in flags]+['-Dbench.samples=3','-cp','../build/operands-classes:kanger/resources:kanger-udf/src:lib/jline-3.13.0.jar','org.kanger.SonProfileRunner']
for i,mode in enumerate(['false','true','true','false'],1):
    prefix=Path('docs/base-operands-evidence')/('deletion-bench-%d-%s'%(i,mode))
    cmd=base[:2]+['-Dkanger.experiment.emptyDeletionLayerShortcut='+mode]+base[2:]
    with open(str(prefix)+'.log','w') as out,open(str(prefix)+'.err','w') as err:
        subprocess.run(cmd,stdout=out,stderr=err,check=True)
    print('DONE',prefix,flush=True)
