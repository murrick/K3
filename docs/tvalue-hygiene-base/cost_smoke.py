from pathlib import Path
import subprocess,tempfile,gzip,json
p=Path(__file__).parent
flags=['preserveTValueIndex','versionedSolveSync','resolvedCauseWeights','compactCauseWeights','candidateMembershipFilter','singleTValueLookup','residentBaseComparison','compactFindSnapshots']
b=json.loads(Path('docs/tvalue-observer-cost/build-validation.json').read_text())
import hashlib
sha=lambda q:hashlib.sha256(Path(q).read_bytes()).hexdigest()
assert all(sha(k)==v for k,v in b['source_sha256'].items());assert all(sha(Path('../build/tvalue-observer-cost-tests')/k)==v for k,v in b['class_sha256'].items())
for size in (32,128):
 for mode in ('clean','disabled','attached'):
  target=(p/f'cost-{size}-{mode}').resolve();runtime='clean' if mode=='clean' else 'hooked'
  cp='../build/tvalue-observer-cost-tests:../build/tvalue-hygiene-base.jar:../build/tvalue-hygiene-base-'+runtime+':../K3-observer-native/kanger/resources:../K3-observer-native/kanger-udf/src:lib/jline-3.13.0.jar'
  cmd=['java','-Xms128m','-Xmx512m','-Duser.home='+tempfile.mkdtemp(prefix='hygiene-cost-'),'-Dresult.path='+str(target),'-Djournal.cost.enabled=false',*['-Dkanger.experiment.'+f+'=true' for f in flags],'-Dkanger.experiment.verifyTValueIndex=true','-Dkanger.experiment.verifySolveSync=true','-cp',cp,'org.kanger.ObserverCostRunner',mode,str(size)]
  print('START',target.name,flush=True);r=subprocess.run(cmd,capture_output=True,timeout=180)
  Path(str(target)+'.log.gz').write_bytes(gzip.compress(r.stdout,mtime=0));Path(str(target)+'.err').write_bytes(r.stderr);Path(str(target)+'.smoke.json').write_text(json.dumps({'size':size,'mode':mode,'flag':'verify','purpose':'cost-workload compatibility; not a timing qualification','exit_code':r.returncode,'command':cmd},indent=2)+'\n')
  assert r.returncode==0 and not r.stderr and b'OBSERVER_COST_OK' in r.stdout,(size,mode,r.stdout,r.stderr)
  for trace in list(p.glob(target.name+'.*.trace')):Path(str(trace)+'.gz').write_bytes(gzip.compress(trace.read_bytes(),mtime=0));trace.unlink()
  print('DONE',target.name,flush=True)
print('HYGIENE_COST_COMPATIBILITY_OK JVMs=6')
