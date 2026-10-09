from pathlib import Path
import subprocess,tempfile,gzip,json
p=Path(__file__).parent
flags=['preserveTValueIndex','versionedSolveSync','resolvedCauseWeights','compactCauseWeights','candidateMembershipFilter','singleTValueLookup','residentBaseComparison','compactFindSnapshots']
i=0
for fixture,runner,number,marker in [('contexts','AuthorityContextRunner',3,'AUTHORITY_CONTEXTS_OK'),('parent-add','ParentAddRunner',3,'PARENT_ADD_OK'),('scope','AuthorityScopeRunner',1,'REPORT_SCOPE_OK')]:
 d=p/fixture;d.mkdir(exist_ok=True)
 for rep in range(number):
  for flag in ('on','off','verify'):
   target=(d/f'{rep}-{flag}').resolve();options=['-Dkanger.experiment.'+f+'='+('false' if flag=='off' else 'true') for f in flags]
   if flag=='verify':options+=['-Dkanger.experiment.verifyTValueIndex=true','-Dkanger.experiment.verifySolveSync=true']
   args=[str(24301+rep)] if fixture=='contexts' else [str(rep)] if fixture=='parent-add' else []
   cmd=['java','-Xmx512m','-Duser.home='+tempfile.mkdtemp(prefix='smart-base-'),'-Dresult.path='+str(target),'-Djournal.cost.enabled=true',*options,'-cp','../build/tvalue-smart-base:../K3-smart-native/kanger/resources:../K3-smart-native/kanger-udf/src:lib/jline-3.13.0.jar','org.kanger.'+runner,*args]
   print('START',fixture,rep,flag,flush=True);r=subprocess.run(cmd,capture_output=True,timeout=180)
   Path(str(target)+'.log.gz').write_bytes(gzip.compress(r.stdout,mtime=0));Path(str(target)+'.err').write_bytes(r.stderr);Path(str(target)+'.result.json').write_text(json.dumps({'fixture':fixture,'repetition':rep,'seed':24301+rep,'selected':rep,'flag':flag,'exit_code':r.returncode,'command':cmd},indent=2)+'\n')
   assert r.returncode==0 and not r.stderr and marker.encode() in r.stdout,(fixture,rep,flag,r.stdout.decode(),r.stderr.decode())
   for mode in ('old','memo'):
    trace=Path(str(target)+'.'+mode+'.trace');Path(str(trace)+'.gz').write_bytes(gzip.compress(trace.read_bytes(),mtime=0));trace.unlink()
   print('DONE',fixture,rep,flag,flush=True);i+=1
print('SMART_BASE_MATRIX_OK',i)
