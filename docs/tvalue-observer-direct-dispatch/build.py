from pathlib import Path
import json,hashlib,subprocess,shutil,zipfile
p=Path('docs/tvalue-observer-direct-dispatch');m=Path('kanger-qualification/diagnostics');native=Path('../K3-observer-native');head='2422f7f6d9e1af7608203b1566df64b5f29fe344'
sha=lambda path:hashlib.sha256(Path(path).read_bytes()).hexdigest()
assert sha('../tooling/ecj.jar')==json.loads(Path('docs/tvalue-observer-cost/build-validation.json').read_text())['compiler_sha256']
assert subprocess.check_output(['git','-C',str(native),'rev-parse','HEAD'],text=True).strip()==head
assert not subprocess.check_output(['git','-C',str(native),'status','--porcelain'])
prior=json.loads(Path('docs/tvalue-observer-session/build-validation.json').read_text())
assert all(sha(k)==v for mode in prior['compilations'].values() for k,v in mode['source_sha256'].items() if not k.endswith('/TValueObservation.java'))
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
native_sources=p.joinpath('native-sources.txt').read_text().splitlines();runtime=m.joinpath('runtime-sources.txt').read_text().splitlines();module=m.joinpath('sources.txt').read_text().splitlines();tests=m.joinpath('test-sources.txt').read_text().splitlines()+[str(m/'test/org/kanger/ObserverSessionRunner.java'),str(m/'test/org/kanger/VanillaConsumerRunner.java')]
records={}
for mode,paths,cp in [('clean',native_sources+runtime,'lib/jline-3.13.0.jar'),('hooked',[mapping.get(x,x) for x in native_sources]+runtime,'lib/jline-3.13.0.jar'),('module',module,'../build/tvalue-observer-direct-dispatch-clean:lib/jline-3.13.0.jar'),('tests',tests,'../build/tvalue-observer-direct-dispatch-clean:../build/tvalue-observer-direct-dispatch-module:lib/jline-3.13.0.jar')]:
 out=Path('../build/tvalue-observer-direct-dispatch-'+mode)
 if out.exists():shutil.rmtree(out)
 out.mkdir(parents=True);listing=p/(mode+'-sources.txt');listing.write_text('\n'.join(paths)+'\n')
 cmd=['java','-jar','../tooling/ecj.jar','-1.8','-nowarn','-cp',cp,'-d',str(out),'@'+str(listing)];subprocess.run(cmd,check=True)
 records[mode]={'command':cmd,'source_sha256':{x:sha(x) for x in paths},'class_sha256':{str(q.relative_to(out)):sha(q) for q in out.rglob('*.class')}}
clean=records['clean']['class_sha256'];hooked=records['hooked']['class_sha256'];assert clean.keys()==hooked.keys();assert records['module']['class_sha256']==prior['compilations']['module']['class_sha256'];assert records['tests']['class_sha256']==prior['compilations']['tests']['class_sha256']
changed=[k for k in clean if clean[k]!=hooked[k]];assert all(k.startswith(('org/kanger/Mind','org/kanger/factory/TValueFactory','org/kanger/units/TValue')) for k in changed)
assert not set(clean)&set(records['module']['class_sha256'])
jar=Path('../build/tvalue-observer-direct-dispatch.jar')
with zipfile.ZipFile(jar,'w') as z:
 for name in sorted(records['module']['class_sha256']):z.writestr(zipfile.ZipInfo(name,(1980,1,1,0,0,0)),(Path('../build/tvalue-observer-direct-dispatch-module')/name).read_bytes())
(p/'build-validation.json').write_text(json.dumps({'native_head':head,'compilations':records,'changed_native_classes':changed,'prior_observer_and_fixture_classes_byte_identical':True,'clean_class_changes_from_old_base':{k:{'old':prior['compilations']['clean']['class_sha256'].get(k),'new':v} for k,v in clean.items() if prior['compilations']['clean']['class_sha256'].get(k)!=v},'clean_classes_added':sorted(set(clean)-set(prior['compilations']['clean']['class_sha256'])),'clean_classes_removed':sorted(set(prior['compilations']['clean']['class_sha256'])-set(clean)),'jar_sha256':sha(jar)},indent=2)+'\n')
assert sha(jar)==prior['jar_sha256']
print('DIRECT_DISPATCH_BUILD_OK',changed,flush=True)

# Rebuild both frozen controls on identical native bytecode.
controls={}
for name,expected_path in [('slow','docs/tvalue-hygiene-base/build-validation.json'),('guarded','docs/tvalue-observer-fast-disabled/build-validation.json')]:
 out=Path('../build/tvalue-observer-direct-dispatch-'+name)
 if out.exists():shutil.rmtree(out)
 shutil.copytree(Path('../build/tvalue-observer-direct-dispatch-hooked'),out,dirs_exist_ok=True)
 for q in out.glob('org/kanger/TValueObservation*.class'):q.unlink()
 source=str(p/name/'org/kanger/TValueObservation.java')
 cmd=['java','-jar','../tooling/ecj.jar','-1.8','-nowarn','-cp',str(out)+':lib/jline-3.13.0.jar','-d',str(out),source]
 subprocess.run(cmd,check=True)
 control={str(q.relative_to(out)):sha(q) for q in out.rglob('*.class')}
 assert control==json.loads(Path(expected_path).read_text())['compilations']['hooked']['class_sha256']
 fast=records['hooked']['class_sha256']
 different=sorted(k for k in set(fast)|set(control) if fast.get(k)!=control.get(k))
 assert different and all(k.startswith('org/kanger/TValueObservation') for k in different)
 controls[name]={'command':cmd,'source_sha256':{source:sha(source)},'class_sha256':control,'different_bridge_classes':different}
(p/'control-validation.json').write_text(json.dumps(controls,indent=2)+'\n')
print('DIRECT_DISPATCH_CONTROL_BUILD_OK',flush=True)

# Compile the unchanged cost fixture against this base, not a stale build directory.
cost=Path('../build/tvalue-observer-direct-dispatch-cost-tests');cost.mkdir(exist_ok=True)
source=str(m/'test/org/kanger/ObserverCostRunner.java')
cmd=['java','-jar','../tooling/ecj.jar','-1.8','-nowarn','-cp','../build/tvalue-observer-direct-dispatch-clean:../build/tvalue-observer-direct-dispatch.jar:lib/jline-3.13.0.jar','-d',str(cost),source]
subprocess.run(cmd,check=True)
fixture={str(q.relative_to(cost)):sha(q) for q in cost.rglob('*.class')}
old_cost=json.loads(Path('docs/tvalue-observer-cost/build-validation.json').read_text());assert fixture==old_cost['class_sha256'];assert sha(source)==old_cost['source_sha256'][source]
(p/'cost-build-validation.json').write_text(json.dumps({'command':cmd,'source_sha256':{source:sha(source)},'class_sha256':fixture},indent=2)+'\n')

contract=Path('../build/tvalue-observer-direct-dispatch-contract');contract.mkdir(exist_ok=True)
source=str(p/'test/org/kanger/ObserverDispatchRunner.java')
cmd=['java','-jar','../tooling/ecj.jar','-1.8','-nowarn','-cp','../build/tvalue-observer-direct-dispatch-hooked:lib/jline-3.13.0.jar','-d',str(contract),source]
subprocess.run(cmd,check=True)
(p/'contract-build-validation.json').write_text(json.dumps({'command':cmd,'source_sha256':{source:sha(source)},'class_sha256':{str(q.relative_to(contract)):sha(q) for q in contract.rglob('*.class')}},indent=2)+'\n')
