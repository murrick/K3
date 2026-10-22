from pathlib import Path
import subprocess,tempfile,shutil,json,gzip,platform,os,tarfile,hashlib
p=Path(__file__).parent;plan=json.loads((p/'plan.json').read_text());assert plan['cycles_per_JVM']==256
flags=['preserveTValueIndex','versionedSolveSync','resolvedCauseWeights','compactCauseWeights','candidateMembershipFilter','singleTValueLookup','residentBaseComparison','compactFindSnapshots'];plans=[]
for rep in range(3):
 for profile in (('default','batch') if rep%2==0 else ('batch','default')):
  for mode in (('clean','disabled','baseline','candidate') if rep%2==0 else ('candidate','baseline','disabled','clean')):plans.append((profile,rep,mode,False))
for profile in ('default','batch'):
 for mode in ('baseline','candidate'):plans.append((profile,0,mode,True))
(p/'environment.json').write_text(json.dumps({'java':subprocess.run(['java','-version'],capture_output=True,text=True).stderr,'platform':platform.platform(),'cpu_count':os.cpu_count(),'shared_host':True,'plan_sha256':hashlib.sha256((p/'plan.json').read_bytes()).hexdigest(),'plans':plans},indent=2)+'\n')
for profile,rep,mode,logging in plans:
 label=f'{profile}-off-{rep}-{mode}'+('-jit' if logging else '');target=(p/label).resolve();assert not Path(str(target)+'.result.json').exists(),label
 home=tempfile.mkdtemp(prefix='resident-stability-');jar='../build/tvalue-journal-'+('context-snapshots' if mode in ('clean','baseline') else 'touch-strings')+'.jar';runtime='../build/tvalue-observer-fast-disabled-'+('clean' if mode=='clean' else 'hooked')
 cp='../build/tvalue-resident-stability-tests:'+jar+':'+runtime+':../K3-observer-native/kanger/resources:../K3-observer-native/kanger-udf/src:lib/jline-3.13.0.jar';options=['-Dkanger.experiment.'+f+'=false' for f in flags]
 if profile=='batch':options+=['-Xbatch']
 if logging:options+=['-XX:+UnlockDiagnosticVMOptions','-XX:+LogCompilation','-XX:LogFile='+str(target)+'.jit.xml']
 cmd=['java','-Xms128m','-Xmx512m','-Duser.home='+home,'-Dresult.path='+str(target),*options,'-cp',cp,'org.kanger.ResidentStabilityRunner','attached' if mode in ('baseline','candidate') else 'disabled',str(plan['cycles_per_JVM'])]
 print('START',label,flush=True)
 try:r=subprocess.run(cmd,capture_output=True,timeout=300)
 finally:shutil.rmtree(home)
 Path(str(target)+'.log.gz').write_bytes(gzip.compress(r.stdout,mtime=0));Path(str(target)+'.err').write_bytes(r.stderr);Path(str(target)+'.result.json').write_text(json.dumps({'profile':profile,'flag':'off','rep':rep,'mode':mode,'logging':logging,'command':cmd,'exit_code':r.returncode},indent=2)+'\n')
 assert r.returncode==0 and not r.stderr and b'RESIDENT_INFERENCE_OK' in r.stdout,(label,r.stdout[-1000:],r.stderr)
 traces=sorted(p.glob(label+'.*.trace'))
 if traces:
  assert len(traces)==plan['cycles_per_JVM']
  with tarfile.open(str(target)+'.evidence.tar.gz','w:gz') as tar:
   for trace in traces:
    for f in (trace,trace.with_suffix('.events.txt')):tar.add(f,arcname=f.name)
  for trace in traces:trace.unlink();trace.with_suffix('.events.txt').unlink()
 if logging:
  f=Path(str(target)+'.jit.xml');Path(str(f)+'.gz').write_bytes(gzip.compress(f.read_bytes(),mtime=0));f.unlink()
 print('DONE',label,flush=True)
print('STABILITY_MATRIX_OK',len(plans))
