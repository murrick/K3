from pathlib import Path
import subprocess,tempfile,gzip,json
p=Path(__file__).parent
flags=['preserveTValueIndex','versionedSolveSync','resolvedCauseWeights','compactCauseWeights','candidateMembershipFilter','singleTValueLookup','residentBaseComparison','compactFindSnapshots']
runs=[]
for rep in range(3):
 for flag in ('on','off','verify'):
  for mode in ('clean','hooked'):
   runs.append((f'{rep}-{flag}-{mode}',rep,flag,mode,'org.kanger.ConsumerRunner'))
for flag in ('on','off','verify'):
 for mode in ('clean','hooked'):
  runs.append((f'owner-{flag}-{mode}',0,flag,mode,'org.kanger.ObserverSessionRunner'))
for label,rep,flag,mode,driver in runs:
 target=(p/label).resolve();options=['-Dkanger.experiment.'+f+'='+('false' if flag=='off' else 'true') for f in flags]
 if flag=='verify':options+=['-Dkanger.experiment.verifyTValueIndex=true','-Dkanger.experiment.verifySolveSync=true']
 cp='../build/tvalue-observer-fast-disabled-tests:../build/tvalue-observer-fast-disabled.jar:../build/tvalue-observer-fast-disabled-'+mode+':../K3-observer-native/kanger/resources:../K3-observer-native/kanger-udf/src:lib/jline-3.13.0.jar'
 cmd=['java','-Xmx512m','-Duser.home='+tempfile.mkdtemp(prefix='observer-session-'),'-Dresult.path='+str(target),'-Dnative.hooks='+str(mode=='hooked').lower(),'-Djournal.cost.enabled=true',*options,'-cp',cp,driver,str(rep)]
 print('START',label,flush=True);r=subprocess.run(cmd,capture_output=True,timeout=180)
 Path(str(target)+'.log.gz').write_bytes(gzip.compress(r.stdout,mtime=0));Path(str(target)+'.err').write_bytes(r.stderr)
 Path(str(target)+'.result.json').write_text(json.dumps({'repetition':rep,'flag':flag,'mode':mode,'driver':driver,'exit_code':r.returncode,'command':cmd},indent=2)+'\n')
 assert r.returncode==0 and not r.stderr and (b'CONSUMER_GATE_OK' if driver.endswith('ConsumerRunner') else b'OBSERVER_SESSION_OK') in r.stdout,(label,r.stdout.decode(),r.stderr.decode())
 for trace in list(p.glob(label+'.*.trace')):Path(str(trace)+'.gz').write_bytes(gzip.compress(trace.read_bytes(),mtime=0));trace.unlink()
 print('DONE',label,flush=True)
print('OBSERVER_SESSION_MATRIX_OK',len(runs))
