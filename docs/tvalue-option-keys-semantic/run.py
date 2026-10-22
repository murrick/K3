from pathlib import Path
import subprocess,tempfile,shutil,json,gzip,platform,os,sys
p=Path(__file__).parent;b=Path('../build/option-keys-semantic');native=Path('../K3-current-native');smoke='--smoke' in sys.argv
flags=['preserveTValueIndex','versionedSolveSync','resolvedCauseWeights','compactCauseWeights','candidateMembershipFilter','singleTValueLookup','residentBaseComparison','compactFindSnapshots']
plans=[]
if smoke:
 for impl in ('current','keys'):plans.append((impl,'off','SingleTValueLookupRunner','smoke',[]))
else:
 for state in ('defaults','off','on','verify','mixed'):
  for impl in ('current','keys'):plans.append((impl,state,'LatentSubstitutionCorpusRunner','corpus',[]))
 for args in ([],['smart']):
  for impl in ('keys','current'):plans.append((impl,'defaults','KangerOptionsRunner','options-'+('smart' if args else 'offline'),args))
 gates=['LatentSolveSyncSafetyRunner','CauseWeightFallbackRunner','CauseWeightShadowSafetyRunner','SingleTValueLookupRunner','ResidentBaseComparisonRunner','CompactFindSnapshotsRunner','TValueIndexPreservationRunner','TValueIndexBoundaryRunner','TValueIndexReopenRunner']
 selected=[(g,'defaults') for g in gates]+[(g,'off') for g in ['ResidentBaseComparisonRunner','SingleTValueLookupRunner','CompactFindSnapshotsRunner','TValueIndexBoundaryRunner','TValueIndexReopenRunner']]+[(g,'verify') for g in ['TValueIndexBoundaryRunner','TValueIndexPreservationRunner','TValueIndexReopenRunner']]+[('LatentSolveSyncTransactionRunner',v) for v in ('off','on','verify')]
 for gate,state in selected:
  for impl in ('current','keys'):plans.append((impl,state,gate,'gate',[]))
(p/('smoke-environment.json' if smoke else 'environment.json')).write_text(json.dumps({'java':subprocess.run(['java','-version'],capture_output=True,text=True).stderr,'platform':platform.platform(),'plans':plans,'timing_claim':False,'native':'60fdc34736c51fa4e5659982630347fb1d1b59fc','safety':'Only disposable per-process homes/tmp/database directories; no shared database.'},indent=2)+'\n')
for impl,state,gate,kind,args in plans:
 label=f'{kind}-{state}-{impl}-{gate}';target=(p/label).resolve();assert not Path(str(target)+'.result.json').exists(),label
 home=Path(tempfile.mkdtemp(prefix='option-keys-semantic-'));(home/'tmp').mkdir()
 options=[]
 if state!='defaults':options=['-Dkanger.experiment.'+name+'='+str(state!='off' and (state!='mixed' or i%2==0)).lower() for i,name in enumerate(flags)]
 if state=='verify':options+=['-Dkanger.experiment.verifyTValueIndex=true','-Dkanger.experiment.verifySolveSync=true','-Dkanger.experiment.shadowCauseWeights=true']
 cp=str(b/'launcher')+':'+str(b/'classes')+':../build/option-keys/'+impl+'-clean:'+str(native/'kanger/resources')+':'+str(native/'kanger-udf/src')+':'+str(native/'kanger-data-dumb2/resources')+':lib/jline-3.13.0.jar'
 gateargs=args if gate!='LatentSolveSyncTransactionRunner' else [str(target)+'.states.txt',str(target)+'.work.txt']
 cmd=['java','-Xms128m','-Xmx768m','-Duser.home='+str(home),'-Djava.io.tmpdir='+str(home/'tmp'),*options,'-cp',cp,'org.kanger.SemanticLaunch',gate,*gateargs];print('START',label,flush=True)
 timedout=False
 try:
  try:r=subprocess.run(cmd,capture_output=True,timeout=300);stdout,stderr,code=r.stdout,r.stderr,r.returncode
  except subprocess.TimeoutExpired as e:stdout,stderr,code=e.stdout or b'',e.stderr or b'',None;timedout=True
 finally:shutil.rmtree(home)
 (p/(label+'.log.gz')).write_bytes(gzip.compress(stdout,mtime=0));(p/(label+'.err.gz')).write_bytes(gzip.compress(stderr,mtime=0));(p/(label+'.result.json')).write_text(json.dumps({'impl':impl,'state':state,'gate':gate,'kind':kind,'args':args,'command':cmd,'exit_code':code,'timeout':timedout,'disposable_home_removed':not home.exists()},indent=2)+'\n')
 print('DONE',label,'exit='+str(code),'stderr_bytes='+str(len(stderr)),flush=True)
 if smoke:assert code==0 and b'SINGLE_LOOKUP_BOUNDARIES_OK' in stdout and not stderr,(stdout,stderr)
print('SEMANTIC_RUNS_FINISHED',len(plans))
