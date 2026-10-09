from pathlib import Path
import subprocess,tempfile,gzip,json,itertools,platform,os
p=Path(__file__).parent/'cost';p.mkdir(exist_ok=True)
flags=['preserveTValueIndex','versionedSolveSync','resolvedCauseWeights','compactCauseWeights','candidateMembershipFilter','singleTValueLookup','residentBaseComparison','compactFindSnapshots']
modes=('clean','slow-disabled','fast-disabled','slow-attached','fast-attached')
orders=[modes[i:]+modes[:i] for i in range(5)]
orders += [tuple(reversed(x)) for x in orders]
plan=[]
for rep,order in enumerate(orders):
 for size in ((32,128) if rep%2==0 else (128,32)):
  for mode in order:plan.append((size,rep,'on',mode))
for flag in ('off','verify'):
 for size in (32,128):
  for mode in modes:plan.append((size,0,flag,mode))
assert len(plan)==120
cpu=next((line.split(':',1)[1].strip() for line in Path('/proc/cpuinfo').read_text().splitlines() if line.startswith('model name')),'unknown')
(p/'environment.json').write_text(json.dumps({'java_version':subprocess.run(['java','-version'],capture_output=True,text=True).stderr,'platform':platform.platform(),'logical_cpus':os.cpu_count(),'cpu_model':cpu,'timing_environment':'shared container; no CPU pinning or exclusive host guarantee','warmup_batches':16,'measured_batches':32,'setter_calls_per_batch':8192,'export_cycles_per_batch':16,'mode_orders':orders,'plan':plan},indent=2)+'\n')
for q in p.glob('pilot.*'):q.unlink()
for size,rep,flag,mode in plan:
 label=f'{size}-{rep}-{flag}-{mode}';target=(p/label).resolve();options=['-Dkanger.experiment.'+f+'='+('false' if flag=='off' else 'true') for f in flags]
 if flag=='verify':options+=['-Dkanger.experiment.verifyTValueIndex=true','-Dkanger.experiment.verifySolveSync=true']
 runtime='clean' if mode=='clean' else ('slow' if mode.startswith('slow-') else 'hooked')
 driver_mode=mode.split('-')[-1]
 cp='../build/tvalue-observer-fast-disabled-cost-tests:../build/tvalue-observer-fast-disabled.jar:../build/tvalue-observer-fast-disabled-'+runtime+':../K3-observer-native/kanger/resources:../K3-observer-native/kanger-udf/src:lib/jline-3.13.0.jar'
 cmd=['java','-Xms128m','-Xmx512m','-Duser.home='+tempfile.mkdtemp(prefix='observer-cost-'),'-Dresult.path='+str(target),'-Djournal.cost.enabled=false',*options,'-cp',cp,'org.kanger.ObserverCostRunner',driver_mode,str(size)]
 print('START',label,flush=True);r=subprocess.run(cmd,capture_output=True,timeout=180)
 Path(str(target)+'.log.gz').write_bytes(gzip.compress(r.stdout,mtime=0));Path(str(target)+'.err').write_bytes(r.stderr)
 Path(str(target)+'.result.json').write_text(json.dumps({'size':size,'repetition':rep,'flag':flag,'mode':mode,'exit_code':r.returncode,'command':cmd},indent=2)+'\n')
 assert r.returncode==0 and not r.stderr and b'OBSERVER_COST_OK' in r.stdout,(label,r.stdout.decode(),r.stderr.decode())
 for trace in list(p.glob(label+'.*.trace')):Path(str(trace)+'.gz').write_bytes(gzip.compress(trace.read_bytes(),mtime=0));trace.unlink()
 print('DONE',label,flush=True)
print('OBSERVER_COST_MATRIX_OK',len(plan))
