from pathlib import Path
import json,hashlib,subprocess
p=Path(__file__).parent;parent='d53b040ae14d135ebebb314ca19910534efac21e'
prior=json.loads(Path('docs/tvalue-layer-cost/build-validation.json').read_text());native=Path('../build/tvalue-layer-cost');assert all(hashlib.sha256((native/k).read_bytes()).hexdigest()==v for k,v in prior['class_sha256'].items())
changes=json.loads((p/'changes.json').read_text());original=Path(changes['original']).read_text();assert (p/'BeforeIdleJournal.java').read_text().replace('BeforeIdleJournal','StreamReportJournal')==original
recovered=(p/'IdleProjectionJournal.java').read_text().replace('IdleProjectionJournal','StreamReportJournal')
for old,new in reversed(changes['replacements']):recovered=recovered.replace(new,old,1)
assert recovered==original
runner=(p/'IdleCostRunner.java').read_text().replace('IdleCostRunner','ReportCostRunner').replace('BeforeIdleJournal','BeforeReportJournal').replace('IdleProjectionJournal','StreamReportJournal');assert runner==Path('docs/tvalue-reporting/ReportCostRunner.java').read_text()
scope=(p/'IdleScopeRunner.java').read_text().replace('IdleScopeRunner','ReportScopeRunner').replace('BeforeIdleJournal','BeforeReportJournal').replace('IdleProjectionJournal','StreamReportJournal');assert scope==Path('docs/tvalue-reporting/ReportScopeRunner.java').read_text()
out=Path('../build/tvalue-idle-projection');out.mkdir(parents=True,exist_ok=True);sources=[p/(x+'.java') for x in ('BeforeIdleJournal','IdleProjectionJournal','IdleCostRunner','IdleScopeRunner')]
cmd=['java','-jar','../tooling/ecj.jar','-1.8','-nowarn','-cp',str(native)+':lib/jline-3.13.0.jar','-d',str(out),*map(str,sources)];subprocess.run(cmd,check=True)
info={'parent':parent,'command':cmd,'prior_runtime_class_hashes_verified':len(prior['class_sha256']),'prior_journal_source_recovered':True,'runner_recovered_by_class_rename_only':True,'full_oracle_unchanged_in_both':True,'class_sha256':{str(q.relative_to(out)):hashlib.sha256(q.read_bytes()).hexdigest() for q in out.rglob('*.class')},'source_sha256':{str(q):hashlib.sha256(q.read_bytes()).hexdigest() for q in sources},'native_clean_runtime':'tvalue-layer-cost','native_setter_metadata_bridge':'explicit after native setter returns; no instrumented native class or engine hooks'};(p/'build-validation.json').write_text(json.dumps(info,indent=2)+'\n');print('REPORTING_BUILD_OK')
