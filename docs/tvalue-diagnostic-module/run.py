from pathlib import Path
import subprocess,tempfile,gzip,json
p=Path(__file__).parent
flags=['preserveTValueIndex','versionedSolveSync','resolvedCauseWeights','compactCauseWeights','candidateMembershipFilter','singleTValueLookup','residentBaseComparison','compactFindSnapshots']
i=0
for rep in range(3):
 for flag in ('on','off','verify'):
  for mode in ('clean','hooked'):
   target=(p/f'{rep}-{flag}-{mode}').resolve();options=['-Dkanger.experiment.'+f+'='+('false' if flag=='off' else 'true') for f in flags]
   if flag=='verify':options+=['-Dkanger.experiment.verifyTValueIndex=true','-Dkanger.experiment.verifySolveSync=true']
   cmd=['java','-Xmx512m','-Duser.home='+tempfile.mkdtemp(prefix='native-hooks-'),'-Dresult.path='+str(target),'-Dnative.hooks='+str(mode=='hooked').lower(),'-Djournal.cost.enabled=true',*options,'-cp','../build/tvalue-diagnostic-module-'+mode+':../K3-smart-native/kanger/resources:../K3-smart-native/kanger-udf/src:lib/jline-3.13.0.jar','org.kanger.ConsumerRunner',str(rep)]
   print('START',rep,flag,mode,flush=True);r=subprocess.run(cmd,capture_output=True,timeout=180)
   Path(str(target)+'.log.gz').write_bytes(gzip.compress(r.stdout,mtime=0));Path(str(target)+'.err').write_bytes(r.stderr);Path(str(target)+'.result.json').write_text(json.dumps({'repetition':rep,'selected':rep,'flag':flag,'mode':mode,'exit_code':r.returncode,'command':cmd},indent=2)+'\n')
   assert r.returncode==0 and not r.stderr and b'CONSUMER_GATE_OK' in r.stdout,(rep,flag,mode,r.stdout.decode(),r.stderr.decode())
   if mode!='clean':
    for trace in list(p.glob(target.name+'.*.trace')):
     Path(str(trace)+'.gz').write_bytes(gzip.compress(trace.read_bytes(),mtime=0));trace.unlink()
   print('DONE',rep,flag,mode,flush=True);i+=1
print('CONSUMER_GATE_MATRIX_OK',i)
