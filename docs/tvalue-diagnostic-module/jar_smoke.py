from pathlib import Path
import json,subprocess,shutil,tempfile,gzip
p=Path(__file__).parent;b=json.loads((p/'build-validation.json').read_text());native=Path('../build/tvalue-diagnostic-module-hooked-native')
if native.exists():shutil.rmtree(native)
native.mkdir(parents=True)
for name in b['independent_compilations']['native-only']['class_sha256']:
 target=native/name;target.parent.mkdir(parents=True,exist_ok=True);shutil.copyfile(Path('../build/tvalue-diagnostic-module-hooked')/name,target)
flags=['preserveTValueIndex','versionedSolveSync','resolvedCauseWeights','compactCauseWeights','candidateMembershipFilter','singleTValueLookup','residentBaseComparison','compactFindSnapshots']
for flag in ['on','off','verify']:
 target=(p/f'jar-{flag}').resolve();options=['-Dkanger.experiment.'+f+'='+('false' if flag=='off' else 'true') for f in flags]
 if flag=='verify':options+=['-Dkanger.experiment.verifyTValueIndex=true','-Dkanger.experiment.verifySolveSync=true']
 cmd=['java','-Xmx512m','-Duser.home='+tempfile.mkdtemp(prefix='diagnostic-jar-'),'-Dresult.path='+str(target),'-Dnative.hooks=true','-Djournal.cost.enabled=true',*options,'-cp','../build/tvalue-diagnostic-module-tests:../build/tvalue-diagnostic-module.jar:'+str(native)+':../K3-smart-native/kanger/resources:../K3-smart-native/kanger-udf/src:lib/jline-3.13.0.jar','org.kanger.ConsumerRunner','0']
 r=subprocess.run(cmd,capture_output=True,timeout=180);Path(str(target)+'.log.gz').write_bytes(gzip.compress(r.stdout,mtime=0));Path(str(target)+'.err').write_bytes(r.stderr);Path(str(target)+'.smoke.json').write_text(json.dumps({'flag':flag,'exit_code':r.returncode,'command':cmd,'classpath_has_no_fallback_diagnostic_classes':True},indent=2)+'\n');assert r.returncode==0 and not r.stderr and b'CONSUMER_GATE_OK' in r.stdout
 prior=p/f'0-{flag}-hooked'
 assert Path(str(target)+'.native-rows.txt').read_bytes()==Path(str(prior)+'.native-rows.txt').read_bytes()
 assert Path(str(target)+'.diagnostics.txt').read_bytes()==Path(str(prior)+'.diagnostics.txt').read_bytes()
 for trace in list(p.glob(target.name+'.*.trace')):
  compressed=gzip.compress(trace.read_bytes(),mtime=0);Path(str(trace)+'.gz').write_bytes(compressed);trace.unlink();suffix=trace.name[len(target.name):]+'.gz';assert compressed==Path(str(prior)+suffix).read_bytes()
 print('STANDALONE_JAR_SMOKE_OK',flag,flush=True)
