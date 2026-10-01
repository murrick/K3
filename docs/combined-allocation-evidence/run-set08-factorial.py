import subprocess
from pathlib import Path
flags=['preserveTValueIndex','versionedSolveSync','resolvedCauseWeights','compactCauseWeights','candidateMembershipFilter','singleTValueLookup']
base=['java','-Xmx512m','-Dbench.samples=12']+['-Dkanger.experiment.'+f+'=true' for f in flags]
for i,mode in enumerate(['00','10','01','11','11','01','10','00'],1):
    prefix=Path('docs/combined-allocation-evidence')/('set08-factorial-%d-%s'%(i,mode))
    cmd=base+['-Dkanger.experiment.directPredicateName='+str(mode[0]=='1').lower(),'-Dkanger.experiment.compactFindSnapshots='+str(mode[1]=='1').lower(),'-cp','../build/combined-classes:kanger/resources:kanger-udf/src:lib/jline-3.13.0.jar','org.kanger.Set08ProfileRunner']
    with open(str(prefix)+'.log','w') as out,open(str(prefix)+'.err','w') as err,open('../build/set08-factorial-%d.full.log'%i,'w') as raw:
        proc=subprocess.Popen(cmd,stdout=subprocess.PIPE,stderr=err,text=True)
        for line in proc.stdout:
            raw.write(line)
            if line.startswith(('Testing:', 'Timing:', 'set_08_02\t', 'Success:', '  Fails:', 'SET08_')): out.write(line)
        assert proc.wait()==0,str(prefix)
    assert 'SET08_PROFILE_OK samples=12' in Path(str(prefix)+'.log').read_text()
    print('DONE',prefix,flush=True)
