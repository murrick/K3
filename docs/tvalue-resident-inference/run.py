from pathlib import Path
import subprocess,tempfile,shutil,json,gzip,platform,os
p=Path(__file__).parent
flags=['preserveTValueIndex','versionedSolveSync','resolvedCauseWeights','compactCauseWeights','candidateMembershipFilter','singleTValueLookup','residentBaseComparison','compactFindSnapshots']
plans=[]
for flag in ('on','off','verify'):
 for rep in range(3):
  for mode in (('clean','disabled','baseline','candidate') if rep%2==0 else ('candidate','baseline','disabled','clean')):plans.append((flag,rep,mode))
(p/'environment.json').write_text(json.dumps({'java':subprocess.run(['java','-version'],capture_output=True,text=True).stderr,'platform':platform.platform(),'cpu_count':os.cpu_count(),'shared_host':True,'warmup_cycles':3,'measured_cycles':6,'plan':plans},indent=2)+'\n')
for flag,rep,mode in plans:
 label=f'{flag}-{rep}-{mode}';target=(p/label).resolve();home=tempfile.mkdtemp(prefix='resident-inference-')
 jar='../build/tvalue-journal-context-snapshots.jar' if mode in ('clean','baseline') else '../build/tvalue-journal-touch-strings.jar'
 runtime='../build/tvalue-observer-fast-disabled-'+('clean' if mode=='clean' else 'hooked')
 cp='../build/tvalue-resident-inference-tests:'+jar+':'+runtime+':../K3-observer-native/kanger/resources:../K3-observer-native/kanger-udf/src:lib/jline-3.13.0.jar'
 options=['-Dkanger.experiment.'+f+'='+('false' if flag=='off' else 'true') for f in flags]
 if flag=='verify':options+=['-Dkanger.experiment.verifyTValueIndex=true','-Dkanger.experiment.verifySolveSync=true']
 cmd=['java','-Xms128m','-Xmx512m','-Duser.home='+home,'-Dresult.path='+str(target),*options,'-cp',cp,'org.kanger.ResidentInferenceRunner','attached' if mode in ('baseline','candidate') else 'disabled','9']
 print('START',label,flush=True)
 try:r=subprocess.run(cmd,capture_output=True,timeout=180)
 finally:shutil.rmtree(home)
 Path(str(target)+'.log.gz').write_bytes(gzip.compress(r.stdout,mtime=0));Path(str(target)+'.err').write_bytes(r.stderr)
 Path(str(target)+'.result.json').write_text(json.dumps({'flag':flag,'rep':rep,'mode':mode,'command':cmd,'exit_code':r.returncode},indent=2)+'\n')
 assert r.returncode==0 and not r.stderr and b'RESIDENT_INFERENCE_OK' in r.stdout,(label,r.stdout,r.stderr)
 for trace in p.glob(label+'.*.trace'):Path(str(trace)+'.gz').write_bytes(gzip.compress(trace.read_bytes(),mtime=0));trace.unlink()
 print('DONE',label,flush=True)
print('RESIDENT_MATRIX_OK',len(plans))
