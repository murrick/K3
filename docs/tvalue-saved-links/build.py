from pathlib import Path
import subprocess,json,hashlib
p=Path(__file__).parent;parent='5bd5d7416d79d87a3a7203f3b18f9f4e0b78672e'
assert not subprocess.check_output(['git','diff',parent,'--','kanger/src','kanger-data-dumb/src','kanger-qualification/src','.github'])
source=Path('kanger-data-dumb/src/org/kanger/storage/Data.java').read_text()
needle='step.setSize(Math.max(1L, dataSize));'
hook='\n                org.kanger.RecordedLinks.decoded(this, step, buffer);'
assert source.count(needle)==1
instrumented=source.replace(needle,needle+hook);assert instrumented.replace(hook,'')==source
folder=Path('../build/tvalue-saved-links-source');folder.mkdir(exist_ok=True);(folder/'Data.java').write_text(instrumented)
info={'parent':parent,'native_Data_body_recovered':True,'native_Data_source_sha256':hashlib.sha256(source.encode()).hexdigest(),'hook':hook.strip(),'production_changed':False,'builds':{}}
for mode in ('clean','shadow'):
 prior=Path('../build/tvalue-owner-pure-observation-'+mode);expected=json.loads(Path('docs/tvalue-owner-pure-observation',mode+'-class-sha256.json').read_text())
 assert all(hashlib.sha256((prior/k).read_bytes()).hexdigest()==v for k,v in expected.items())
 old=json.loads(Path('docs/tvalue-update-boundary/build-validation.json').read_text())['builds'][mode]['overlay_class_sha256']
 assert all(hashlib.sha256((Path('../build/tvalue-update-boundary-'+mode)/k).read_bytes()).hexdigest()==v for k,v in old.items())
 cp=':'.join(['../build/tvalue-update-boundary-'+mode,'../build/tvalue-materialization-routing-classes','../build/tvalue-persistent-lookup-classes','../build/tvalue-resident-persistent-classes',str(prior),'lib/jline-3.13.0.jar'])
 out=Path('../build/tvalue-saved-links-'+mode);out.mkdir(exist_ok=True)
 sources=[folder/'Data.java',p/'RecordedLinks.java',p/'ResidentPersistentRead.java',p/'PersistentSettlementRunner.java',p/'SavedLinksRunner.java']
 cmd=['java','-jar','../tooling/ecj.jar','-1.8','-nowarn','-cp',cp,'-d',str(out),*map(str,sources)];subprocess.run(cmd,check=True)
 info['builds'][mode]={'command':cmd,'prior_native_classes_verified':len(expected),'overlay_class_sha256':{str(x.relative_to(out)):hashlib.sha256(x.read_bytes()).hexdigest() for x in out.rglob('*.class')}}
(p/'build-validation.json').write_text(json.dumps(info,indent=2)+'\n');print('SAVED_LINKS_BUILD_OK')
