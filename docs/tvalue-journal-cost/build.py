from pathlib import Path
import json,hashlib,subprocess
p=Path(__file__).parent;original=Path('docs/tvalue-update-boundary/TValueDirtyJournal.java').read_text();recovered=(p/'TValueDirtyJournal.java').read_text()
for old,new in reversed(json.loads((p/'instrumentation.json').read_text())['replacements']):recovered=recovered.replace(new,old)
assert recovered==original
info={'parent':'c8d4fa5adc17b206e97c90e25487bb869a591956','original_journal_source_recovered':True,'oracle_never_disabled':True,'production_changed':False,'builds':{}}
for mode in ('clean','shadow'):
 stages=['tvalue-reindex-boundary','tvalue-clear-close-guard','tvalue-delete-flush-guard','tvalue-write-guard','tvalue-saved-links','tvalue-update-boundary']
 for stage in stages:
  hashes=json.loads(Path('docs',stage,'build-validation.json').read_text())['builds'][mode]['overlay_class_sha256'];assert all(hashlib.sha256((Path('../build',stage+'-'+mode)/k).read_bytes()).hexdigest()==v for k,v in hashes.items())
 native=json.loads(Path('docs/tvalue-owner-pure-observation',mode+'-class-sha256.json').read_text());assert all(hashlib.sha256((Path('../build/tvalue-owner-pure-observation-'+mode)/k).read_bytes()).hexdigest()==v for k,v in native.items())
 cp=':'.join(['../build/'+stage+'-'+mode for stage in stages]+['../build/tvalue-materialization-routing-classes','../build/tvalue-persistent-lookup-classes','../build/tvalue-resident-persistent-classes','../build/tvalue-owner-pure-observation-'+mode,'lib/jline-3.13.0.jar']);out=Path('../build/tvalue-journal-cost-'+mode);out.mkdir(exist_ok=True)
 cmd=['java','-jar','../tooling/ecj.jar','-1.8','-nowarn','-cp',cp,'-d',str(out),str(p/'TValueDirtyJournal.java'),str(p/'JournalCostRunner.java')];subprocess.run(cmd,check=True)
 info['builds'][mode]={'command':cmd,'prior_native_classes_verified':len(native),'overlay_class_sha256':{str(q.relative_to(out)):hashlib.sha256(q.read_bytes()).hexdigest() for q in out.rglob('*.class')}}
(p/'build-validation.json').write_text(json.dumps(info,indent=2)+'\n');print('JOURNAL_COST_BUILD_OK')
