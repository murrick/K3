from pathlib import Path
import json,hashlib,subprocess,shutil,zipfile
p=Path('docs/tvalue-observer-session');m=Path('kanger-qualification/diagnostics');native=Path('../K3-smart-native');head='5d5f6aff271f1abf371fbde55d637744c53f0bc3'
sha=lambda path:hashlib.sha256(Path(path).read_bytes()).hexdigest()
assert subprocess.check_output(['git','-C',str(native),'rev-parse','HEAD'],text=True).strip()==head
assert not subprocess.check_output(['git','-C',str(native),'status','--porcelain'])
prior=json.loads(Path('docs/tvalue-smart-base/build-validation.json').read_text())
assert all(sha(k)==v for k,v in prior['source_sha256'].items())
changes=json.loads(Path('docs/tvalue-consumer-gate/changes.json').read_text());mapping={};proof={}
for name,edits in changes.items():
 original=(native/'kanger/src/org/kanger'/name).read_text();body=original
 for edit in edits:
  assert body.count(edit['old'])==1
  edit['new']=edit['new'].replace('ConsumerHooks','TValueObservation');body=body.replace(edit['old'],edit['new'],1)
 declarations={'Mind.java':'public class Mind implements IMind {','factory/TValueFactory.java':'public class TValueFactory implements IFactory<TValue> {','units/TValue.java':'public class TValue implements Comparable<TValue>, IUnit<TValue> {'}
 old=declarations[name];new=old+'\n    public static String tvalueObserverProtocol() { return "tvalue-observer-v1"; }'
 assert body.count(old)==1;body=body.replace(old,new,1);edits.append({'old':old,'new':new})
 recovered=body
 for edit in reversed(edits):assert recovered.count(edit['new'])==1;recovered=recovered.replace(edit['new'],edit['old'],1)
 assert recovered==original
 target=p/'source'/name;target.parent.mkdir(parents=True,exist_ok=True);target.write_text(body);mapping[str(native/'kanger/src/org/kanger'/name)]=str(target)
 proof[name]={'original_sha256':hashlib.sha256(original.encode()).hexdigest(),'instrumented_sha256':sha(target),'original_code_recovered_byte_for_byte':True}
(p/'changes.json').write_text(json.dumps(changes,indent=2)+'\n');(p/'structural-check.json').write_text(json.dumps(proof,indent=2)+'\n')
native_sources=[x for x in Path('docs/tvalue-smart-base/sources.txt').read_text().splitlines() if not x.startswith('docs/')];runtime=m.joinpath('runtime-sources.txt').read_text().splitlines();module=m.joinpath('sources.txt').read_text().splitlines();tests=m.joinpath('test-sources.txt').read_text().splitlines()+[str(m/'test/org/kanger/ObserverSessionRunner.java'),str(m/'test/org/kanger/VanillaConsumerRunner.java')]
records={}
for mode,paths,cp in [('clean',native_sources+runtime,'lib/jline-3.13.0.jar'),('hooked',[mapping.get(x,x) for x in native_sources]+runtime,'lib/jline-3.13.0.jar'),('module',module,'../build/tvalue-observer-session-clean:lib/jline-3.13.0.jar'),('tests',tests,'../build/tvalue-observer-session-clean:../build/tvalue-observer-session-module:lib/jline-3.13.0.jar')]:
 out=Path('../build/tvalue-observer-session-'+mode)
 if out.exists():shutil.rmtree(out)
 out.mkdir(parents=True);listing=p/(mode+'-sources.txt');listing.write_text('\n'.join(paths)+'\n')
 cmd=['java','-jar','../tooling/ecj.jar','-1.8','-nowarn','-cp',cp,'-d',str(out),'@'+str(listing)];subprocess.run(cmd,check=True)
 records[mode]={'command':cmd,'source_sha256':{x:sha(x) for x in paths},'class_sha256':{str(q.relative_to(out)):sha(q) for q in out.rglob('*.class')}}
clean=records['clean']['class_sha256'];hooked=records['hooked']['class_sha256'];assert clean.keys()==hooked.keys();assert all(clean[k]==v for k,v in json.loads(Path('docs/tvalue-diagnostic-module/build-validation.json').read_text())['independent_compilations']['native-only']['class_sha256'].items())
changed=[k for k in clean if clean[k]!=hooked[k]];assert all(k.startswith(('org/kanger/Mind','org/kanger/factory/TValueFactory','org/kanger/units/TValue')) for k in changed)
assert not set(clean)&set(records['module']['class_sha256'])
jar=Path('../build/tvalue-observer-session.jar')
with zipfile.ZipFile(jar,'w') as z:
 for name in sorted(records['module']['class_sha256']):z.writestr(zipfile.ZipInfo(name,(1980,1,1,0,0,0)),(Path('../build/tvalue-observer-session-module')/name).read_bytes())
(p/'build-validation.json').write_text(json.dumps({'native_head':head,'compilations':records,'changed_native_classes':changed,'prior_clean_classes_verified':len(json.loads(Path('docs/tvalue-diagnostic-module/build-validation.json').read_text())['independent_compilations']['native-only']['class_sha256']),'jar_sha256':sha(jar)},indent=2)+'\n')
print('OBSERVER_BUILD_OK',changed,flush=True)
