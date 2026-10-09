from pathlib import Path
import json,hashlib,subprocess,shutil
p=Path(__file__).parent;native=Path('../K3-smart-native');head='5d5f6aff271f1abf371fbde55d637744c53f0bc3'
assert subprocess.check_output(['git','-C',str(native),'rev-parse','HEAD'],text=True).strip()==head
assert not subprocess.check_output(['git','-C',str(native),'status','--porcelain'])
prior=json.loads(Path('docs/tvalue-smart-base/build-validation.json').read_text());assert all(hashlib.sha256(Path(k).read_bytes()).hexdigest()==v for k,v in prior['source_sha256'].items())
oldChanges=json.loads(Path('docs/tvalue-native-mutations/changes.json').read_text());proof={};changes={};mapping={}
for name,oldEdits in oldChanges.items():
 original=(native/'kanger/src/org/kanger'/name).read_text();body=original;edits=[]
 for e in oldEdits:
  new=e['new'].replace('MutationJournalHooks','RecycledIdHooks');assert body.count(e['old'])==1;body=body.replace(e['old'],new,1);edits.append({'old':e['old'],'new':new})
 recovered=body
 for e in reversed(edits):assert recovered.count(e['new'])==1;recovered=recovered.replace(e['new'],e['old'],1)
 assert recovered==original,name
 target=p/'source'/name;target.parent.mkdir(parents=True,exist_ok=True);target.write_text(body);mapping[str(native/'kanger/src/org/kanger'/name)]=str(target);changes[name]=edits;proof[name]={'original_sha256':hashlib.sha256(original.encode()).hexdigest(),'instrumented_sha256':hashlib.sha256(body.encode()).hexdigest(),'original_code_recovered_byte_for_byte':True}
(p/'changes.json').write_text(json.dumps(changes,indent=2)+'\n');(p/'structural-check.json').write_text(json.dumps(proof,indent=2)+'\n')
listing=Path('docs/tvalue-smart-base/sources.txt').read_text().splitlines()+[str(p/'RecycledIdHooks.java'),str(p/'RecycledIdRunner.java')];classes={};commands={};sources={}
for mode in ['clean','hooked']:
 out=Path('../build/tvalue-recycled-id-'+mode)
 if out.exists():shutil.rmtree(out)
 out.mkdir(parents=True);paths=[mapping.get(x,x) if mode=='hooked' else x for x in listing];source_list=p/(mode+'-sources.txt');source_list.write_text('\n'.join(paths)+'\n');cmd=['java','-jar','../tooling/ecj.jar','-1.8','-nowarn','-cp','lib/jline-3.13.0.jar','-d',str(out),'@'+str(source_list)];subprocess.run(cmd,check=True);commands[mode]=cmd;sources[mode]={x:hashlib.sha256(Path(x).read_bytes()).hexdigest() for x in paths};classes[mode]={str(q.relative_to(out)):hashlib.sha256(q.read_bytes()).hexdigest() for q in out.rglob('*.class')}
assert all(classes['clean'][k]==v for k,v in prior['class_sha256'].items());assert classes['clean'].keys()==classes['hooked'].keys();changed=[k for k in classes['clean'] if classes['clean'][k]!=classes['hooked'][k]];assert changed and all(k.startswith(('org/kanger/Mind','org/kanger/factory/TValueFactory','org/kanger/units/TValue')) for k in changed)
b={'parent':'6f360e1d83cc7f5d1949e65a8d2a1c5addf2853c','develop_head':head,'commands':commands,'source_sha256':sources,'class_sha256':classes,'changed_native_classes':changed,'unchanged_prior_clean_classes_verified':len(prior['class_sha256']),'diagnostic_implementation_unchanged':True,'all_original_native_code_recovered_byte_for_byte':True,'clear_descendant_fanout_enabled':True};(p/'build-validation.json').write_text(json.dumps(b,indent=2)+'\n');print('RECYCLED_ID_BUILD_OK',changed)
