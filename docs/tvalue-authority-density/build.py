from pathlib import Path
import json,hashlib,subprocess
p=Path(__file__).parent;parent='9880a9b1e914ed94ab1d0932d26d0b53f89190ed'
c=json.loads((p/'changes.json').read_text());recovered=(p/'DensityCostRunner.java').read_text().replace('DensityCostRunner','AuthorityCostRunner')
for old,new in reversed(c['replacements']):assert recovered.count(new)==1;recovered=recovered.replace(new,old,1)
assert recovered==Path(c['original']).read_text()
native=Path('../build/tvalue-layer-cost');native_proof=json.loads(Path('docs/tvalue-layer-cost/build-validation.json').read_text());assert all(hashlib.sha256((native/k).read_bytes()).hexdigest()==v for k,v in native_proof['class_sha256'].items())
accepted=Path('../build/tvalue-authority-stream');accepted_proof=json.loads(Path('docs/tvalue-authority-stream/build-validation.json').read_text());assert all(hashlib.sha256((accepted/k).read_bytes()).hexdigest()==v for k,v in accepted_proof['class_sha256'].items());assert all(hashlib.sha256(Path(k).read_bytes()).hexdigest()==v for k,v in accepted_proof['source_sha256'].items())
out=Path('../build/tvalue-authority-density');out.mkdir(parents=True,exist_ok=True);sources=[p/'DensityCostRunner.java'];cmd=['java','-jar','../tooling/ecj.jar','-1.8','-nowarn','-cp',str(accepted)+':'+str(native)+':lib/jline-3.13.0.jar','-d',str(out),*map(str,sources)];subprocess.run(cmd,check=True)
info={'parent':parent,'command':cmd,'native_classes_verified':len(native_proof['class_sha256']),'accepted_diagnostic_classes_verified':len(accepted_proof['class_sha256']),'runner_recovered_except_declared_fixture_changes':True,'implementation_unchanged':True,'source_sha256':{str(q):hashlib.sha256(q.read_bytes()).hexdigest() for q in sources},'class_sha256':{str(q.relative_to(out)):hashlib.sha256(q.read_bytes()).hexdigest() for q in out.rglob('*.class')}};(p/'build-validation.json').write_text(json.dumps(info,indent=2)+'\n');print('DENSITY_BUILD_OK')
