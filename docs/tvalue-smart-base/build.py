from pathlib import Path
import subprocess,json,hashlib,shutil
p=Path(__file__).parent;native=Path('../K3-smart-native');head='5d5f6aff271f1abf371fbde55d637744c53f0bc3'
assert subprocess.check_output(['git','-C',str(native),'rev-parse','HEAD'],text=True).strip()==head
assert not subprocess.check_output(['git','-C',str(native),'status','--porcelain'])
sources=(p/'sources.txt').read_text().splitlines();assert all(Path(x).is_file() for x in sources)
out=Path('../build/tvalue-smart-base')
if out.exists():shutil.rmtree(out)
out.mkdir(parents=True)
cmd=['java','-jar','../tooling/ecj.jar','-1.8','-nowarn','-cp','lib/jline-3.13.0.jar','-d',str(out),'@'+str(p/'sources.txt')];subprocess.run(cmd,check=True)
b={'parent':'a6d5599f7fafd996be45cfbb914d92100dd1f0c4','develop_head':head,'develop_tree':subprocess.check_output(['git','-C',str(native),'rev-parse','HEAD^{tree}'],text=True).strip(),'command':cmd,'source_sha256':{x:hashlib.sha256(Path(x).read_bytes()).hexdigest() for x in sources},'class_sha256':{str(x.relative_to(out)):hashlib.sha256(x.read_bytes()).hexdigest() for x in out.rglob('*.class')},'compiler_sha256':hashlib.sha256(Path('../tooling/ecj.jar').read_bytes()).hexdigest(),'java_version':subprocess.check_output(['java','-version'],stderr=subprocess.STDOUT,text=True),'scope':'fresh exact-develop native core and legacy DUMB memory fixture; unchanged diagnostic sources; no SMART backend persistence qualification'}
(p/'build-validation.json').write_text(json.dumps(b,indent=2)+'\n');print('SMART_BASE_BUILD_OK',len(b['class_sha256']))
