from pathlib import Path
import json,hashlib,subprocess
p=Path(__file__).parent;parent='24770ae402c41ea43dce0cc7309245dc9396fdfd'
prior=json.loads(Path('docs/tvalue-layer-cost/build-validation.json').read_text());native=Path('../build/tvalue-layer-cost');assert all(hashlib.sha256((native/k).read_bytes()).hexdigest()==v for k,v in prior['class_sha256'].items())
changes=json.loads((p/'changes.json').read_text());original=Path(changes['original']).read_text();assert (p/'BeforeValidationJournal.java').read_text().replace('BeforeValidationJournal','StreamReportJournal')==original
recovered=(p/'OrderedValidationJournal.java').read_text().replace('OrderedValidationJournal','StreamReportJournal')
for old,new in reversed(changes['replacements']):recovered=recovered.replace(new,old,1)
assert recovered==original
runner=(p/'ValidationCostRunner.java').read_text().replace('ValidationCostRunner','ReportCostRunner').replace('BeforeValidationJournal','BeforeReportJournal').replace('OrderedValidationJournal','StreamReportJournal');assert runner==Path('docs/tvalue-reporting/ReportCostRunner.java').read_text()
out=Path('../build/tvalue-ordered-validation');out.mkdir(parents=True,exist_ok=True);sources=[p/(x+'.java') for x in ('BeforeValidationJournal','OrderedValidationJournal','ValidationCostRunner','ValidationScopeRunner')]
cmd=['java','-jar','../tooling/ecj.jar','-1.8','-nowarn','-cp',str(native)+':lib/jline-3.13.0.jar','-d',str(out),*map(str,sources)];subprocess.run(cmd,check=True)
info={'parent':parent,'command':cmd,'prior_runtime_class_hashes_verified':len(prior['class_sha256']),'prior_journal_source_recovered':True,'runner_recovered_by_class_rename_only':True,'full_oracle_unchanged_in_both':True,'class_sha256':{str(q.relative_to(out)):hashlib.sha256(q.read_bytes()).hexdigest() for q in out.rglob('*.class')},'source_sha256':{str(q):hashlib.sha256(q.read_bytes()).hexdigest() for q in sources},'native_clean_runtime':'tvalue-layer-cost','native_setter_metadata_bridge':'explicit after native setter returns; no instrumented native class or engine hooks'};(p/'build-validation.json').write_text(json.dumps(info,indent=2)+'\n');print('ORDERED_VALIDATION_BUILD_OK')
