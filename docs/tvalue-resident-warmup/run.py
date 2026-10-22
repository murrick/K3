from pathlib import Path
import subprocess,tempfile,shutil,json,gzip,platform,os
p=Path(__file__).parent
flags=['preserveTValueIndex','versionedSolveSync','resolvedCauseWeights','compactCauseWeights','candidateMembershipFilter','singleTValueLookup','residentBaseComparison','compactFindSnapshots']
plans=[]
for rep in range(3):
 for profile in (('default','batch') if rep%2==0 else ('batch','default')):
  for flag in ('on','off','verify'):
   for mode in (('clean','disabled','baseline','candidate') if rep%2==0 else ('candidate','baseline','disabled','clean')):plans.append((profile,flag,rep,mode,False))
for profile in ('default','batch'):
 for mode in ('baseline','candidate'):plans.append((profile,'off',0,mode,True))
(p/'environment.json').write_text(json.dumps({'java':subprocess.run(['java','-version'],capture_output=True,text=True).stderr,'platform':platform.platform(),'cpu_count':os.cpu_count(),'shared_host':True,'cycles':60,'windows':[[0,3],[3,9],[12,24],[24,36],[36,48],[48,60]],'plans':plans},indent=2)+'\n')
for profile,flag,rep,mode,logging in plans:
 label=f'{profile}-{flag}-{rep}-{mode}'+('-jit' if logging else '');target=(p/label).resolve();home=tempfile.mkdtemp(prefix='resident-warmup-')
 jar='../build/tvalue-journal-'+('context-snapshots' if mode in ('clean','baseline') else 'touch-strings')+'.jar'
 runtime='../build/tvalue-observer-fast-disabled-'+('clean' if mode=='clean' else 'hooked')
 cp='../build/tvalue-resident-warmup-tests:'+jar+':'+runtime+':../K3-observer-native/kanger/resources:../K3-observer-native/kanger-udf/src:lib/jline-3.13.0.jar'
 options=['-Dkanger.experiment.'+f+'='+('false' if flag=='off' else 'true') for f in flags]
 if flag=='verify':options+=['-Dkanger.experiment.verifyTValueIndex=true','-Dkanger.experiment.verifySolveSync=true']
 if profile=='batch':options+=['-Xbatch']
 if logging:options+=['-XX:+UnlockDiagnosticVMOptions','-XX:+LogCompilation','-XX:LogFile='+str(target)+'.jit.xml']
 cmd=['java','-Xms128m','-Xmx512m','-Duser.home='+home,'-Dresult.path='+str(target),*options,'-cp',cp,'org.kanger.ResidentWarmupRunner','attached' if mode in ('baseline','candidate') else 'disabled','60']
 print('START',label,flush=True)
 try:r=subprocess.run(cmd,capture_output=True,timeout=180)
 finally:shutil.rmtree(home)
 Path(str(target)+'.log.gz').write_bytes(gzip.compress(r.stdout,mtime=0));Path(str(target)+'.err').write_bytes(r.stderr)
 Path(str(target)+'.result.json').write_text(json.dumps({'profile':profile,'flag':flag,'rep':rep,'mode':mode,'logging':logging,'command':cmd,'exit_code':r.returncode},indent=2)+'\n')
 assert r.returncode==0 and not r.stderr and b'RESIDENT_INFERENCE_OK' in r.stdout,(label,r.stdout[-1000:],r.stderr)
 for trace in p.glob(label+'.*.trace'):
  Path(str(trace)+'.gz').write_bytes(gzip.compress(trace.read_bytes(),mtime=0));trace.unlink()
 if logging:
  f=Path(str(target)+'.jit.xml');Path(str(f)+'.gz').write_bytes(gzip.compress(f.read_bytes(),mtime=0));f.unlink()
 print('DONE',label,flush=True)
print('WARMUP_MATRIX_OK',len(plans))
