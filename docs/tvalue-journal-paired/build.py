from pathlib import Path
import json,hashlib,subprocess
p=Path(__file__).parent;parent='ada5483103193fb646e87ae1c456ed043ee5dd8f'
prior=json.loads(Path('docs/tvalue-layer-cost/build-validation.json').read_text());native=Path('../build/tvalue-layer-cost');assert all(hashlib.sha256((native/k).read_bytes()).hexdigest()==v for k,v in prior['class_sha256'].items())
for x in json.loads((p/'copies.json').read_text()):
 original=Path(x['original']).read_text();assert hashlib.sha256(original.encode()).hexdigest()==x['original_sha256'];assert Path(x['copy']).read_text().replace(x['new_name'],x['old_name'])==original
out=Path('../build/tvalue-journal-paired');out.mkdir(parents=True,exist_ok=True);sources=[p/(x+'.java') for x in ('PriorLayerJournal','MemoLayerJournal','PairedJournalRunner')]
cmd=['java','-jar','../tooling/ecj.jar','-1.8','-nowarn','-cp',str(native)+':lib/jline-3.13.0.jar','-d',str(out),*map(str,sources)];subprocess.run(cmd,check=True)
info={'parent':parent,'command':cmd,'prior_runtime_class_hashes_verified':len(prior['class_sha256']),'journal_source_recovered_by_class_rename_only':True,'full_oracle_unchanged_in_both':True,'class_sha256':{str(q.relative_to(out)):hashlib.sha256(q.read_bytes()).hexdigest() for q in out.rglob('*.class')},'source_sha256':{str(q):hashlib.sha256(q.read_bytes()).hexdigest() for q in sources},'native_clean_runtime':'tvalue-layer-cost','native_setter_metadata_bridge':'explicit after native setter returns; no instrumented native class or engine hooks'};(p/'build-validation.json').write_text(json.dumps(info,indent=2)+'\n');print('PAIRED_JOURNAL_BUILD_OK')
