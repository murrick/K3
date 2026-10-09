from pathlib import Path
import subprocess,json,hashlib
p=Path(__file__).parent;sha=lambda q:hashlib.sha256(Path(q).read_bytes()).hexdigest()
b=json.loads(Path('docs/tvalue-observer-session/build-validation.json').read_text())
for mode,record in b['compilations'].items():
 assert all(sha(k)==v for k,v in record['source_sha256'].items())
 assert all(sha(Path('../build/tvalue-observer-session-'+mode)/k)==v for k,v in record['class_sha256'].items())
assert sha('../build/tvalue-observer-session.jar')==b['jar_sha256']
source=Path('kanger-qualification/diagnostics/test/org/kanger/ObserverCostRunner.java');out=Path('../build/tvalue-observer-cost-tests');out.mkdir(parents=True,exist_ok=True)
cmd=['java','-jar','../tooling/ecj.jar','-1.8','-nowarn','-cp','../build/tvalue-observer-session-clean:../build/tvalue-observer-session.jar:lib/jline-3.13.0.jar','-d',str(out),str(source)];subprocess.run(cmd,check=True)
record={'parent':'51b28d5de50822f47aca376664375efda380b7c3','native_head':b['native_head'],'observer_build_manifest_sha256':sha('docs/tvalue-observer-session/build-validation.json'),'observer_runtime_and_module_hashes_verified':True,'command':cmd,'source_sha256':{str(source):sha(source)},'compiler_sha256':sha('../tooling/ecj.jar'),'class_sha256':{str(q.relative_to(out)):sha(q) for q in out.rglob('*.class')}}
(p/'build-validation.json').write_text(json.dumps(record,indent=2)+'\n');print('OBSERVER_COST_BUILD_OK')
