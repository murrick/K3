from pathlib import Path
import subprocess,json,hashlib
p=Path(__file__).parent;target=Path('../build/tvalue-observer-dispatch-jit');target.mkdir(exist_ok=True)
cp='../build/tvalue-observer-fast-disabled-clean:../build/tvalue-observer-fast-disabled.jar:../build/tvalue-observer-fast-disabled-cost-tests:lib/jline-3.13.0.jar'
cmd=['java','-jar','../tooling/ecj.jar','-1.8','-nowarn','-cp',cp,'-d',str(target),str(p/'MinimalObserverRunner.java')];subprocess.run(cmd,check=True)
sha=lambda f:hashlib.sha256(f.read_bytes()).hexdigest()
b={'command':cmd,'source_sha256':sha(p/'MinimalObserverRunner.java'),'compiler_sha256':sha(Path('../tooling/ecj.jar')),'class_sha256':{str(f.relative_to(target)):sha(f) for f in target.rglob('*.class')}}
(p/'build-validation.json').write_text(json.dumps(b,indent=2)+'\n');print('DISPATCH_BUILD_OK')
