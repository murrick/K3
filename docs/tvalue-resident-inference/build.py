from pathlib import Path
import subprocess,hashlib,json
p=Path(__file__).parent;out=Path('../build/tvalue-resident-inference-tests');out.mkdir(exist_ok=True)
cp='../build/tvalue-journal-touch-strings.jar:../build/tvalue-observer-fast-disabled-hooked:lib/jline-3.13.0.jar'
cmd=['java','-jar','../tooling/ecj.jar','-1.8','-nowarn','-cp',cp,'-d',str(out),str(p/'ResidentInferenceRunner.java')];subprocess.run(cmd,check=True)
sha=lambda f:hashlib.sha256(Path(f).read_bytes()).hexdigest()
(p/'build-validation.json').write_text(json.dumps({'command':cmd,'source_sha256':sha(p/'ResidentInferenceRunner.java'),'compiler_sha256':sha('../tooling/ecj.jar'),'classes':{str(f):sha(f) for f in out.rglob('*.class')},'jars':{str(f):sha(f) for f in map(Path,['../build/tvalue-journal-context-snapshots.jar','../build/tvalue-journal-touch-strings.jar'])}},indent=2)+'\n');print('RESIDENT_BUILD_OK')
