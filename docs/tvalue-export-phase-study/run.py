from pathlib import Path
import subprocess,tempfile,gzip,json,shutil,platform,os
p=Path(__file__).parent
flags=['preserveTValueIndex','versionedSolveSync','resolvedCauseWeights','compactCauseWeights','candidateMembershipFilter','singleTValueLookup','residentBaseComparison','compactFindSnapshots']
plans=[]
for rep in range(6):
 for size in ((32,128) if rep%2==0 else (128,32)):
  for module in (('baseline','candidate') if rep%2==0 else ('candidate','baseline')):
   plans.append((f'phase-{size}-{rep}-{module}','on',module,'cost',rep,size))
(p/'environment.json').write_text(json.dumps({'java':subprocess.run(['java','-version'],capture_output=True,text=True).stderr,'platform':platform.platform(),'cpu_count':os.cpu_count(),'shared_host':True,'plan':plans},indent=2)+'\n')
for label,flag,module,driver,rep,size in plans:
 target=(p/label).resolve();options=['-Dkanger.experiment.'+f+'='+('false' if flag=='off' else 'true') for f in flags]
 if flag=='verify':options+=['-Dkanger.experiment.verifyTValueIndex=true','-Dkanger.experiment.verifySolveSync=true']
 jar='../build/tvalue-journal-context-snapshots.jar' if module=='baseline' else '../build/tvalue-journal-touch-strings.jar'
 cp='../build/tvalue-export-phase-tests:../build/tvalue-journal-touch-strings-tests:../build/tvalue-journal-three-routes-tests:../build/tvalue-observer-fast-disabled-tests:../build/tvalue-observer-fast-disabled-cost-tests:'+jar+':../build/tvalue-observer-fast-disabled-hooked:../K3-observer-native/kanger/resources:../K3-observer-native/kanger-udf/src:lib/jline-3.13.0.jar'
 main={'consumer':'ConsumerRunner','owner':'ObserverSessionRunner','routes':'RouteOrderRunner','cost':'ExportPhaseRunner','contexts':'ContextSnapshotRunner'}[driver]
 home=tempfile.mkdtemp(prefix='three-routes-');cmd=['java','-Xms128m','-Xmx512m','-Duser.home='+home,'-Dresult.path='+str(target),'-Dnative.hooks=true','-Djournal.cost.enabled=false',*options,'-cp',cp,'org.kanger.'+main]
 cmd+=['attached',str(size)] if driver=='cost' else [str(rep)]
 print('START',label,flush=True)
 try:r=subprocess.run(cmd,capture_output=True,timeout=180)
 finally:shutil.rmtree(home)
 Path(str(target)+'.log.gz').write_bytes(gzip.compress(r.stdout,mtime=0));Path(str(target)+'.err').write_bytes(r.stderr)
 Path(str(target)+'.result.json').write_text(json.dumps({'flag':flag,'module':module,'driver':driver,'repetition':rep,'size':size,'command':cmd,'exit_code':r.returncode},indent=2)+'\n')
 marker={'consumer':b'CONSUMER_GATE_OK','owner':b'OBSERVER_SESSION_OK','routes':b'ROUTE_ORDER_OK','cost':b'EXPORT_PHASE_OK','contexts':b'CONTEXT_SNAPSHOT_OK'}[driver]
 assert r.returncode==0 and not r.stderr and marker in r.stdout,(label,r.stdout.decode(),r.stderr.decode())
 for trace in p.glob(label+'.*.trace'):Path(str(trace)+'.gz').write_bytes(gzip.compress(trace.read_bytes(),mtime=0));trace.unlink()
 print('DONE',label,flush=True)
print('CONTEXT_SNAPSHOT_MATRIX_OK',len(plans))
