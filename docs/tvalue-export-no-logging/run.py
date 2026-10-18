from pathlib import Path
import subprocess,tempfile,gzip,json,shutil,platform,os
p=Path(__file__).parent
flags=['preserveTValueIndex','versionedSolveSync','resolvedCauseWeights','compactCauseWeights','candidateMembershipFilter','singleTValueLookup','residentBaseComparison','compactFindSnapshots']
plans=[]
for rep in range(6):
 for size in ((32,128) if rep%2==0 else (128,32)):
  for profile in (('default','batch') if rep%2==0 else ('batch','default')):
   for module in (('baseline','candidate') if (rep+(profile=='batch'))%2==0 else ('candidate','baseline')):
    plans.append((f'{profile}-{size}-{rep}-{module}','on',module,profile,rep,size))
(p/'environment.json').write_text(json.dumps({'java':subprocess.run(['java','-version'],capture_output=True,text=True).stderr,'platform':platform.platform(),'cpu_count':os.cpu_count(),'shared_host':True,'plan':plans},indent=2)+'\n')
for label,flag,module,driver,rep,size in plans:
 target=(p/label).resolve();options=['-Dkanger.experiment.'+f+'='+('false' if flag=='off' else 'true') for f in flags]
 if flag=='verify':options+=['-Dkanger.experiment.verifyTValueIndex=true','-Dkanger.experiment.verifySolveSync=true']
 jar='../build/tvalue-journal-context-snapshots.jar' if module=='baseline' else '../build/tvalue-journal-touch-strings.jar'
 cp='../build/tvalue-export-phase-tests:../build/tvalue-journal-touch-strings-tests:../build/tvalue-journal-three-routes-tests:../build/tvalue-observer-fast-disabled-tests:../build/tvalue-observer-fast-disabled-cost-tests:'+jar+':../build/tvalue-observer-fast-disabled-hooked:../K3-observer-native/kanger/resources:../K3-observer-native/kanger-udf/src:lib/jline-3.13.0.jar'
 main='ExportPhaseRunner'
 home=tempfile.mkdtemp(prefix='export-no-logging-');cmd=['java','-Xms128m','-Xmx512m','-Duser.home='+home,'-Dresult.path='+str(target),'-Dnative.hooks=true','-Djournal.cost.enabled=false',*options,'-cp',cp,'org.kanger.'+main]
 cmd+=['attached',str(size)]
 if driver=='batch':cmd.insert(1,'-Xbatch')
 print('START',label,flush=True)
 try:r=subprocess.run(cmd,capture_output=True,timeout=180)
 finally:shutil.rmtree(home)
 Path(str(target)+'.log.gz').write_bytes(gzip.compress(r.stdout,mtime=0));Path(str(target)+'.err').write_bytes(r.stderr)
 Path(str(target)+'.result.json').write_text(json.dumps({'flag':flag,'module':module,'driver':driver,'repetition':rep,'size':size,'command':cmd,'exit_code':r.returncode},indent=2)+'\n')
 marker=b'EXPORT_PHASE_OK'
 assert r.returncode==0 and not r.stderr and marker in r.stdout,(label,r.stdout.decode(),r.stderr.decode())
 for trace in p.glob(label+'.*.trace'):Path(str(trace)+'.gz').write_bytes(gzip.compress(trace.read_bytes(),mtime=0));trace.unlink()
 print('DONE',label,flush=True)
print('NO_LOGGING_MATRIX_OK',len(plans))
