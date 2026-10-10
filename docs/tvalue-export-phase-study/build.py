from pathlib import Path
import subprocess,json,hashlib
p=Path(__file__).parent;out=Path('../build/tvalue-export-phase-tests');out.mkdir(exist_ok=True)
cp='../build/tvalue-journal-touch-strings.jar:../build/tvalue-observer-fast-disabled-hooked:../build/tvalue-observer-fast-disabled-tests:lib/jline-3.13.0.jar'
cmd=['java','-jar','../tooling/ecj.jar','-1.8','-nowarn','-cp',cp,'-d',str(out),str(p/'ExportPhaseRunner.java')];subprocess.run(cmd,check=True)
sha=lambda f:hashlib.sha256(Path(f).read_bytes()).hexdigest()
(p/'build-validation.json').write_text(json.dumps({'command':cmd,'source_sha256':sha(p/'ExportPhaseRunner.java'),'compiler_sha256':sha('../tooling/ecj.jar'),'classes':{str(f):sha(f) for f in out.rglob('*.class')},'jars':{str(f):sha(f) for f in map(Path,['../build/tvalue-journal-context-snapshots.jar','../build/tvalue-journal-touch-strings.jar'])}},indent=2)+'\n')
print('EXPORT_PHASE_BUILD_OK')
