from pathlib import Path
import subprocess,tempfile,gzip,json,itertools,platform,os
p=Path(__file__).parent
flags=['preserveTValueIndex','versionedSolveSync','resolvedCauseWeights','compactCauseWeights','candidateMembershipFilter','singleTValueLookup','residentBaseComparison','compactFindSnapshots']
runtimes={'slow':'tvalue-observer-fast-disabled-slow','fast':'tvalue-observer-fast-disabled-hooked','direct':'tvalue-observer-attached-direct'}
orders=list(itertools.permutations(runtimes))
plan=[(size,rep,'on',mode,'cost') for rep,order in enumerate(orders) for size in ((32,128) if rep%2==0 else (128,32)) for mode in order]
plan += [(size,0,flag,'direct','cost') for flag in ('off','verify') for size in (32,128)]
plan += [(0,0,flag,'direct',driver) for flag in ('on','off','verify') for driver in ('consumer','owner')]
(p/'environment.json').write_text(json.dumps({'java':subprocess.run(['java','-version'],capture_output=True,text=True).stderr,'platform':platform.platform(),'cpu_count':os.cpu_count(),'orders':orders,'plan':plan,'shared_host':True},indent=2)+'\n')
for size,rep,flag,mode,driver in plan:
 label=f'{driver}-{size}-{rep}-{flag}-{mode}';target=(p/label).resolve()
 options=['-Dkanger.experiment.'+f+'='+('false' if flag=='off' else 'true') for f in flags]
 if flag=='verify':options+=['-Dkanger.experiment.verifyTValueIndex=true','-Dkanger.experiment.verifySolveSync=true']
 fixture='cost-tests' if driver=='cost' else 'tests'
 cp='../build/tvalue-observer-fast-disabled-'+fixture+':../build/tvalue-observer-fast-disabled.jar:../build/'+runtimes[mode]+':../K3-observer-native/kanger/resources:../K3-observer-native/kanger-udf/src:lib/jline-3.13.0.jar'
 main={'cost':'ObserverCostRunner','consumer':'ConsumerRunner','owner':'ObserverSessionRunner'}[driver]
 cmd=['java','-Xms128m','-Xmx512m','-Duser.home='+tempfile.mkdtemp(prefix='attached-study-'),'-Dresult.path='+str(target),'-Dnative.hooks=true','-Djournal.cost.enabled=false',*options,'-cp',cp,'org.kanger.'+main]
 cmd+=['attached',str(size)] if driver=='cost' else ['0']
 print('START',label,flush=True);r=subprocess.run(cmd,capture_output=True,timeout=180)
 Path(str(target)+'.log.gz').write_bytes(gzip.compress(r.stdout,mtime=0));Path(str(target)+'.err').write_bytes(r.stderr)
 Path(str(target)+'.result.json').write_text(json.dumps({'size':size,'repetition':rep,'flag':flag,'mode':mode,'driver':driver,'exit_code':r.returncode,'command':cmd},indent=2)+'\n')
 marker={'cost':b'OBSERVER_COST_OK','consumer':b'CONSUMER_GATE_OK','owner':b'OBSERVER_SESSION_OK'}[driver]
 assert r.returncode==0 and not r.stderr and marker in r.stdout,(label,r.stdout.decode(),r.stderr.decode())
 for trace in p.glob(label+'.*.trace'):
  Path(str(trace)+'.gz').write_bytes(gzip.compress(trace.read_bytes(),mtime=0));trace.unlink()
 print('DONE',label,flush=True)
print('ATTACHED_STUDY_OK',len(plan))
