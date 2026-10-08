from pathlib import Path
import json,hashlib,subprocess
p=Path(__file__).parent;original=Path('docs/tvalue-journal-cost/TValueDirtyJournal.java').read_text();recovered=(p/'TValueDirtyJournal.java').read_text()
for old,new in reversed(json.loads((p/'changes.json').read_text())['replacements']):recovered=recovered.replace(new,old,1)
assert recovered==original
info={'parent':'0c3684326d1592cda91e79076394027cd5fcd67d','prior_profiled_journal_recovered':True,'oracle_never_disabled':True,'original_reader_unchanged':True,'production_changed':False,'builds':{}}
for mode in ('clean','shadow'):
 stages=['tvalue-journal-cost','tvalue-reindex-boundary','tvalue-clear-close-guard','tvalue-delete-flush-guard','tvalue-write-guard','tvalue-saved-links','tvalue-update-boundary']
 for stage in stages:
  hashes=json.loads(Path('docs',stage,'build-validation.json').read_text())['builds'][mode]['overlay_class_sha256'];assert all(hashlib.sha256((Path('../build',stage+'-'+mode)/k).read_bytes()).hexdigest()==v for k,v in hashes.items())
 native=json.loads(Path('docs/tvalue-owner-pure-observation',mode+'-class-sha256.json').read_text());assert all(hashlib.sha256((Path('../build/tvalue-owner-pure-observation-'+mode)/k).read_bytes()).hexdigest()==v for k,v in native.items())
 cp=':'.join(['../build/'+stage+'-'+mode for stage in stages]+['../build/tvalue-materialization-routing-classes','../build/tvalue-persistent-lookup-classes','../build/tvalue-resident-persistent-classes','../build/tvalue-owner-pure-observation-'+mode,'lib/jline-3.13.0.jar']);out=Path('../build/tvalue-observation-layers-'+mode);out.mkdir(exist_ok=True)
 cmd=['java','-jar','../tooling/ecj.jar','-1.8','-nowarn','-cp',cp,'-d',str(out),str(p/'TValueDirtyJournal.java'),str(p/'ObservationLayers.java'),str(p/'ObservationLayersRunner.java')];subprocess.run(cmd,check=True)
 info['builds'][mode]={'command':cmd,'prior_native_classes_verified':len(native),'overlay_class_sha256':{str(q.relative_to(out)):hashlib.sha256(q.read_bytes()).hexdigest() for q in out.rglob('*.class')}}
(p/'build-validation.json').write_text(json.dumps(info,indent=2)+'\n');print('OBSERVATION_LAYERS_BUILD_OK')
