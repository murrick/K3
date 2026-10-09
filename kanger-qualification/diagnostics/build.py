from pathlib import Path
import json,hashlib,subprocess,shutil
p=Path('docs/tvalue-diagnostic-module');module=Path('kanger-qualification/diagnostics');native=Path('../K3-smart-native');head='5d5f6aff271f1abf371fbde55d637744c53f0bc3'
assert subprocess.check_output(['git','-C',str(native),'rev-parse','HEAD'],text=True).strip()==head
assert not subprocess.check_output(['git','-C',str(native),'status','--porcelain'])
prior=json.loads(Path('docs/tvalue-smart-base/build-validation.json').read_text());assert all(hashlib.sha256(Path(k).read_bytes()).hexdigest()==v for k,v in prior['source_sha256'].items())
oldChanges=json.loads(Path('docs/tvalue-consumer-gate/changes.json').read_text());proof={};changes={};mapping={}
for name,oldEdits in oldChanges.items():
 original=(native/'kanger/src/org/kanger'/name).read_text();body=original;edits=[]
 for e in oldEdits:
  new=e['new'].replace('ConsumerHooks','ConsumerHooks');assert body.count(e['old'])==1;body=body.replace(e['old'],new,1);edits.append({'old':e['old'],'new':new})
 recovered=body
 for e in reversed(edits):assert recovered.count(e['new'])==1;recovered=recovered.replace(e['new'],e['old'],1)
 assert recovered==original,name
 target=p/'source'/name;target.parent.mkdir(parents=True,exist_ok=True);target.write_text(body);mapping[str(native/'kanger/src/org/kanger'/name)]=str(target);changes[name]=edits;proof[name]={'original_sha256':hashlib.sha256(original.encode()).hexdigest(),'instrumented_sha256':hashlib.sha256(body.encode()).hexdigest(),'original_code_recovered_byte_for_byte':True}
(p/'changes.json').write_text(json.dumps(changes,indent=2)+'\n');(p/'structural-check.json').write_text(json.dumps(proof,indent=2)+'\n')
origins=json.loads((p/'source-origins.json').read_text());assert all(Path(k).read_bytes()==Path(v).read_bytes() for k,v in origins.items())
original=Path('docs/tvalue-smart-base/sources.txt').read_text().splitlines();native_sources=[x for x in original if not x.startswith('docs/')]
listing=native_sources+module.joinpath('sources.txt').read_text().splitlines()+module.joinpath('test-sources.txt').read_text().splitlines();classes={};commands={};sources={}
for mode in ['clean','hooked']:
 out=Path('../build/tvalue-diagnostic-module-'+mode)
 if out.exists():shutil.rmtree(out)
 out.mkdir(parents=True);paths=[mapping.get(x,x) if mode=='hooked' else x for x in listing];source_list=p/(mode+'-sources.txt');source_list.write_text('\n'.join(paths)+'\n');cmd=['java','-jar','../tooling/ecj.jar','-1.8','-nowarn','-cp','lib/jline-3.13.0.jar','-d',str(out),'@'+str(source_list)];subprocess.run(cmd,check=True);commands[mode]=cmd;sources[mode]={x:hashlib.sha256(Path(x).read_bytes()).hexdigest() for x in paths};classes[mode]={str(q.relative_to(out)):hashlib.sha256(q.read_bytes()).hexdigest() for q in out.rglob('*.class')}
assert all(classes['clean'][k]==v for k,v in prior['class_sha256'].items());assert classes['clean'].keys()==classes['hooked'].keys();changed=[k for k in classes['clean'] if classes['clean'][k]!=classes['hooked'][k]];assert changed and all(k.startswith(('org/kanger/Mind','org/kanger/factory/TValueFactory','org/kanger/units/TValue')) for k in changed)
b={'parent':'86ba48b40e8bfffd3ae33949210c649be883f7cb','develop_head':head,'commands':commands,'source_sha256':sources,'class_sha256':classes,'changed_native_classes':changed,'unchanged_prior_clean_classes_verified':len(prior['class_sha256']),'diagnostic_implementation_unchanged':True,'all_original_native_code_recovered_byte_for_byte':True,'clear_descendant_fanout_enabled':True};(p/'build-validation.json').write_text(json.dumps(b,indent=2)+'\n');print('DIAGNOSTIC_MODULE_BUILD_OK',changed)

# Independently compile the source set against a classpath containing no prior diagnostic classes.
independent={}
for mode,paths,cp in [('native-only',native_sources,'lib/jline-3.13.0.jar'),('module',module.joinpath('sources.txt').read_text().splitlines(),'../build/tvalue-diagnostic-module-native-only:lib/jline-3.13.0.jar'),('tests',module.joinpath('test-sources.txt').read_text().splitlines(),'../build/tvalue-diagnostic-module-native-only:../build/tvalue-diagnostic-module-module:lib/jline-3.13.0.jar')]:
 out=Path('../build/tvalue-diagnostic-module-'+mode)
 if out.exists():shutil.rmtree(out)
 out.mkdir(parents=True);source_list=p/(mode+'-sources.txt');source_list.write_text('\n'.join(paths)+'\n');cmd=['java','-jar','../tooling/ecj.jar','-1.8','-nowarn','-cp',cp,'-d',str(out),'@'+str(source_list)];subprocess.run(cmd,check=True)
 hashes={str(q.relative_to(out)):hashlib.sha256(q.read_bytes()).hexdigest() for q in out.rglob('*.class')};assert all(classes['clean'][k]==v for k,v in hashes.items());independent[mode]={'command':cmd,'class_sha256':hashes}
previous=json.loads(Path('docs/tvalue-consumer-gate/build-validation.json').read_text())
assert all(classes[mode]==previous['class_sha256'][mode] for mode in ['clean','hooked'])
assert not (set(independent['native-only']['class_sha256'])&set(independent['module']['class_sha256']))
import zipfile
jar=Path('../build/tvalue-diagnostic-module.jar')
with zipfile.ZipFile(jar,'w',compression=zipfile.ZIP_DEFLATED) as z:
 for name in sorted(independent['module']['class_sha256']):
  info=zipfile.ZipInfo(name,(1980,1,1,0,0,0));info.compress_type=zipfile.ZIP_DEFLATED;z.writestr(info,(Path('../build/tvalue-diagnostic-module-module')/name).read_bytes())
b['all_clean_and_hooked_class_hashes_equal_previous_consumer_stage']=True;b['independent_compilations']=independent;b['standalone_module_jar']={'path':str(jar),'sha256':hashlib.sha256(jar.read_bytes()).hexdigest(),'entries':len(independent['module']['class_sha256'])};b['module_source_files']=len(module.joinpath('sources.txt').read_text().splitlines());b['module_test_source_files']=len(module.joinpath('test-sources.txt').read_text().splitlines());(p/'build-validation.json').write_text(json.dumps(b,indent=2)+'\n');print('INDEPENDENT_DIAGNOSTIC_MODULE_OK sources='+str(b['module_source_files'])+' test_sources='+str(b['module_test_source_files']))
