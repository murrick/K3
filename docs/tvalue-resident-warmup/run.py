from pathlib import Path
import subprocess,tempfile,shutil,json,gzip,platform,os,hashlib,xml.etree.ElementTree as ET
p=Path(__file__).parent
flags=['preserveTValueIndex','versionedSolveSync','resolvedCauseWeights','compactCauseWeights','candidateMembershipFilter','singleTValueLookup','residentBaseComparison','compactFindSnapshots']
plans=[]
for flag in ('on','off','verify'):
 for rep in range(3):
  for mode in (('baseline','candidate') if rep%2==0 else ('candidate','baseline')):plans.append((flag,rep,mode,'timing'))
for mode in ('baseline','candidate'):plans.append(('off',0,mode,'jit-probe'))
(p/'environment.json').write_text(json.dumps({'java':subprocess.run(['java','-version'],capture_output=True,text=True).stderr,'platform':platform.platform(),'cpu_count':os.cpu_count(),'shared_host':True,'cycles':48,'windows':{'early':[3,9],'middle':[18,30],'late':[36,48]},'plan':plans},indent=2)+'\n')
for flag,rep,mode,profile in plans:
 label=f'{profile}-{flag}-{rep}-{mode}';target=(p/label).resolve();home=tempfile.mkdtemp(prefix='resident-warmup-')
 jar='../build/tvalue-journal-context-snapshots.jar' if mode=='baseline' else '../build/tvalue-journal-touch-strings.jar'
 cp='../build/tvalue-resident-inference-tests:'+jar+':../build/tvalue-observer-fast-disabled-hooked:../K3-observer-native/kanger/resources:../K3-observer-native/kanger-udf/src:lib/jline-3.13.0.jar'
 options=['-Dkanger.experiment.'+f+'='+('false' if flag=='off' else 'true') for f in flags]
 if flag=='verify':options+=['-Dkanger.experiment.verifyTValueIndex=true','-Dkanger.experiment.verifySolveSync=true']
 if profile=='jit-probe':options+=['-XX:+UnlockDiagnosticVMOptions','-XX:+LogCompilation','-XX:LogFile='+str(target)+'.jit.xml']
 cmd=['java','-Xms128m','-Xmx512m','-Duser.home='+home,'-Dresult.path='+str(target),*options,'-cp',cp,'org.kanger.ResidentInferenceRunner','attached','48']
 print('START',label,flush=True)
 try:r=subprocess.run(cmd,capture_output=True,timeout=180)
 finally:shutil.rmtree(home)
 Path(str(target)+'.log.gz').write_bytes(gzip.compress(r.stdout,mtime=0));Path(str(target)+'.err').write_bytes(r.stderr)
 Path(str(target)+'.result.json').write_text(json.dumps({'flag':flag,'rep':rep,'mode':mode,'profile':profile,'command':cmd,'exit_code':r.returncode},indent=2)+'\n')
 assert r.returncode==0 and not r.stderr and b'RESIDENT_INFERENCE_OK' in r.stdout,(label,r.stdout,r.stderr)
 for trace in p.glob(label+'.*.trace'):Path(str(trace)+'.gz').write_bytes(gzip.compress(trace.read_bytes(),mtime=0));trace.unlink()
 if profile=='jit-probe':
  raw=Path(str(target)+'.jit.xml');data=raw.read_bytes();root=ET.fromstring(data)
  selected=ET.Element('selected_jit',{'raw_sha256':hashlib.sha256(data).hexdigest(),'raw_bytes':str(len(data))})
  names=('org.kanger.Mind','org.kanger.BeforeAuthorityJournal','org.kanger.StreamAuthorityJournal','org.kanger.QualifiedJournalConsumer','org.kanger.ResidentInferenceRunner')
  for node in root.iter():
   if node.tag in ('task_queued','nmethod') and node.attrib.get('method','').startswith(names):selected.append(node)
  Path(str(target)+'.jit-selected.xml.gz').write_bytes(gzip.compress(ET.tostring(selected),mtime=0));raw.unlink()
 print('DONE',label,flush=True)
print('RESIDENT_WARMUP_MATRIX_OK',len(plans))
