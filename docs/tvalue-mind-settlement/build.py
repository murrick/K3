from pathlib import Path
import json,hashlib,subprocess,shutil
p=Path(__file__).parent;prior=json.loads(Path('docs/tvalue-smart-base/build-validation.json').read_text());native=Path('../build/tvalue-smart-base');assert all(hashlib.sha256((native/k).read_bytes()).hexdigest()==v for k,v in prior['class_sha256'].items());assert all(hashlib.sha256(Path(k).read_bytes()).hexdigest()==v for k,v in prior['source_sha256'].items())
out=Path('../build/tvalue-mind-settlement')
if out.exists():shutil.rmtree(out)
out.mkdir(parents=True);source=p/'MindSettlementRunner.java';cmd=['java','-jar','../tooling/ecj.jar','-1.8','-nowarn','-cp',str(native)+':lib/jline-3.13.0.jar','-d',str(out),str(source)];subprocess.run(cmd,check=True)
b={'parent':'f2d16aa77fd151cfdc6c73c676eb603f4888f433','develop_head':prior['develop_head'],'command':cmd,'native_dependency_classes_verified':len(prior['class_sha256']),'source_sha256':{str(source):hashlib.sha256(source.read_bytes()).hexdigest()},'class_sha256':{str(q.relative_to(out)):hashlib.sha256(q.read_bytes()).hexdigest() for q in out.rglob('*.class')},'diagnostic_implementation_unchanged':True};(p/'build-validation.json').write_text(json.dumps(b,indent=2)+'\n');print('MIND_SETTLEMENT_BUILD_OK')
