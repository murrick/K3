import subprocess
from pathlib import Path
flags=['preserveTValueIndex','versionedSolveSync','resolvedCauseWeights','compactCauseWeights','candidateMembershipFilter','singleTValueLookup']
for i,mode in enumerate(['reference','false','true','true','false','reference'],1):
 classes='../build/base-reference-classes' if mode=='reference' else '../build/base-classes'
 cmd=['java','-Xmx512m','-Dbench.samples=6','-Dbench.allocation=true','-Dkanger.experiment.residentBaseComparison='+('false' if mode=='reference' else mode)]+['-Dkanger.experiment.'+f+'=true' for f in flags]+['-cp',classes+':kanger/resources:kanger-udf/src:lib/jline-3.13.0.jar','org.kanger.SonProfileRunner']
 prefix=Path(__file__).parent/('three-way-%d-%s'%(i,mode))
 with prefix.with_suffix('.log').open('w') as out,prefix.with_suffix('.err').open('w') as err:
  subprocess.run(cmd,stdout=out,stderr=err,check=True)
 print('DONE',prefix,flush=True)
