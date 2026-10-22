from pathlib import Path
import subprocess,tempfile,shutil,json,gzip,platform,os,tarfile,sys
p=Path(__file__).parent;b=Path('../build/option-keys');flags=['preserveTValueIndex','versionedSolveSync','resolvedCauseWeights','compactCauseWeights','candidateMembershipFilter','singleTValueLookup','residentBaseComparison','compactFindSnapshots']
smoke='--smoke' in sys.argv
plans=[]
if smoke:
 for impl in ('old','current','keys'):plans.append((impl,'off',0,'candidate',2,False))
else:
 for rep in range(2):
  for impl in (('current','keys') if rep==0 else ('keys','current')):
   for flag in ('off','on','verify'):
    for mode in (('clean','baseline','candidate') if rep==0 else ('candidate','baseline','clean')):plans.append((impl,flag,rep,mode,24,False))
 for impl in ('current','keys'):
  for mode in ('clean','candidate'):plans.append((impl,'off',0,mode,512,True))
(p/('smoke-environment.json' if smoke else 'environment.json')).write_text(json.dumps({'java':subprocess.run(['java','-version'],capture_output=True,text=True).stderr,'jfr':subprocess.check_output([str(Path(os.environ['JAVA_HOME'])/'bin/jfr'),'--version'],text=True),'platform':platform.platform(),'shared_host':True,'plans':plans,'performance_qualified':False},indent=2)+'\n')
if smoke:
 subprocess.run(['java','-jar','../tooling/ecj.jar','-1.8','-nowarn','-cp',str(b/'current-clean'),'-d',str(b/'contract'),str(p/'OptionsContract.java')],check=True)
 for impl in ('current','keys'):
  home=tempfile.mkdtemp(prefix='keys-contract-')
  try:r=subprocess.run(['java','-Duser.home='+home,'-cp',str(b/'contract')+':'+str(b/(impl+'-clean'))+':../K3-current-native/kanger/resources:../K3-current-native/kanger-udf/src:lib/jline-3.13.0.jar','org.kanger.OptionsContract'],capture_output=True,timeout=60)
  finally:shutil.rmtree(home)
  (p/(impl+'-contract.log')).write_bytes(r.stdout);(p/(impl+'-contract.err')).write_bytes(r.stderr);assert r.returncode==0,(r.stdout,r.stderr)
for impl,flag,rep,mode,n,profile in plans:
 label=('smoke-' if smoke else '')+f'{impl}-{flag}-{rep}-{mode}'+('-profile' if profile else '');target=(p/label).resolve();assert not Path(str(target)+'.result.json').exists(),label
 home=tempfile.mkdtemp(prefix='option-keys-');native='../K3-observer-native' if impl=='old' else '../K3-current-native'
 cp=str(b/'fixture')+':'+str(b/('baseline' if mode in ('clean','baseline') else 'candidate'))+'.jar:'+str(b/(impl+'-'+('clean' if mode=='clean' else 'hooked')))+':'+native+'/kanger/resources:'+native+'/kanger-udf/src:lib/jline-3.13.0.jar'
 options=['-Dkanger.experiment.'+f+'='+('false' if flag=='off' else 'true') for f in flags]
 if flag=='verify':options+=['-Dkanger.experiment.verifyTValueIndex=true','-Dkanger.experiment.verifySolveSync=true']
 if profile:options+=['-Dresident.profile=true','-XX:FlightRecorderOptions=stackdepth=128','-XX:StartFlightRecording=settings='+str((p/'profile.jfc').resolve())+',filename='+str(target)+'.jfr,dumponexit=true']
 cmd=['java','-Xms128m','-Xmx512m','-Duser.home='+home,'-Dresult.path='+str(target),*options,'-cp',cp,'org.kanger.ResidentCurrentRunner','disabled' if mode=='clean' else 'attached',str(n)];print('START',label,flush=True)
 try:r=subprocess.run(cmd,capture_output=True,timeout=300)
 finally:shutil.rmtree(home)
 (p/(label+'.log.gz')).write_bytes(gzip.compress(r.stdout,mtime=0));(p/(label+'.err')).write_bytes(r.stderr);(p/(label+'.result.json')).write_text(json.dumps({'impl':impl,'flag':flag,'rep':rep,'mode':mode,'profile':profile,'repeats':n,'command':cmd,'exit_code':r.returncode},indent=2)+'\n');assert r.returncode==0 and not r.stderr and b'RESIDENT_INFERENCE_OK' in r.stdout,(label,r.stdout[-1500:],r.stderr)
 traces=sorted(p.glob(label+'.*.trace'))
 if traces:
  assert len(traces)==n
  with tarfile.open(str(target)+'.evidence.tar.gz','w:gz') as tar:
   for trace in traces:
    for f in (trace,trace.with_suffix('.events.txt')):tar.add(f,arcname=f.name)
  for trace in traces:trace.unlink();trace.with_suffix('.events.txt').unlink()
 print('DONE',label,flush=True)
print('OPTION_KEYS_RUN_OK',len(plans))
