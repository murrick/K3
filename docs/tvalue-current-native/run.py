from pathlib import Path
import subprocess,tempfile,shutil,json,gzip,platform,os,tarfile,hashlib
p=Path(__file__).parent;sha=lambda f:hashlib.sha256(Path(f).read_bytes()).hexdigest();flags=['preserveTValueIndex','versionedSolveSync','resolvedCauseWeights','compactCauseWeights','candidateMembershipFilter','singleTValueLookup','residentBaseComparison','compactFindSnapshots'];plans=[]
for rep in range(2):
 for base in (('old','current') if rep==0 else ('current','old')):
  for flag in ('on','off','verify'):
   for mode in (('clean','disabled','baseline','candidate') if rep==0 else ('candidate','baseline','disabled','clean')):plans.append((base,flag,rep,mode,24,False,False))
for base,mode in [('old','clean'),('current','clean'),('current','disabled'),('current','baseline'),('current','candidate')]:plans.append((base,'off',0,mode,512,True,False))
for rep,override in [(1,False),(2,True)]:
 for mode in ('clean','candidate'):plans.append(('current','off',rep,mode,512,True,override))
(p/'environment.json').write_text(json.dumps({'java':subprocess.run(['java','-version'],capture_output=True,text=True).stderr,'platform':platform.platform(),'cpu_count':os.cpu_count(),'shared_host':True,'plans':plans,'profile_configuration_sha256':sha(p/'profile.jfc'),'timing_claim':False,'recovery_note':'Earlier temporary runs lost after interruption; these 57 runs and their raw evidence are the retained final dataset.'},indent=2)+'\n')
for base,flag,rep,mode,n,profile,override in plans:
 label=f'{base}-{flag}-{rep}-{mode}'+('-profile' if profile else '');target=(p/label).resolve();assert not Path(str(target)+'.result.json').exists(),label;home=tempfile.mkdtemp(prefix='current-native-')
 cp='../build/current-native/fixture:../build/current-native/'+('baseline' if mode in ('clean','baseline') else 'candidate')+'.jar:../build/current-native/'+base+'-'+('clean' if mode=='clean' else 'hooked')+':'+('../K3-observer-native' if base=='old' else '../K3-current-native')+'/kanger/resources:'+('../K3-observer-native' if base=='old' else '../K3-current-native')+'/kanger-udf/src:lib/jline-3.13.0.jar';options=['-Dkanger.experiment.'+f+'='+('false' if flag=='off' else 'true') for f in flags]
 if flag=='verify':options+=['-Dkanger.experiment.verifyTValueIndex=true','-Dkanger.experiment.verifySolveSync=true']
 if profile:options+=['-Dresident.profile=true','-Dresident.override='+str(override).lower(),'-XX:FlightRecorderOptions=stackdepth=128','-XX:StartFlightRecording=settings='+str((p/'profile.jfc').resolve())+',filename='+str(target)+'.jfr,dumponexit=true']
 cmd=['java','-Xms128m','-Xmx512m','-Duser.home='+home,'-Dresult.path='+str(target),*options,'-cp',cp,'org.kanger.ResidentCurrentRunner','attached' if mode in ('baseline','candidate') else 'disabled',str(n)];print('START',label,flush=True)
 try:r=subprocess.run(cmd,capture_output=True,timeout=300)
 finally:shutil.rmtree(home)
 (p/(label+'.log.gz')).write_bytes(gzip.compress(r.stdout,mtime=0));(p/(label+'.err')).write_bytes(r.stderr);(p/(label+'.result.json')).write_text(json.dumps({'base':base,'flag':flag,'rep':rep,'mode':mode,'profile':profile,'override':override,'repeats':n,'command':cmd,'exit_code':r.returncode},indent=2)+'\n');assert r.returncode==0 and not r.stderr and b'RESIDENT_INFERENCE_OK' in r.stdout,(label,r.stdout[-1500:],r.stderr)
 traces=sorted(p.glob(label+'.*.trace'))
 if traces:
  assert len(traces)==n
  with tarfile.open(str(target)+'.evidence.tar.gz','w:gz') as tar:
   for trace in traces:
    for f in (trace,trace.with_suffix('.events.txt')):tar.add(f,arcname=f.name)
  for trace in traces:trace.unlink();trace.with_suffix('.events.txt').unlink()
 print('DONE',label,flush=True)
print('CURRENT_MATRIX_OK',len(plans))
