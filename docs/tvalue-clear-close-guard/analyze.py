from pathlib import Path
import ast,json,gzip,hashlib,subprocess
p=Path(__file__).parent;source=Path('docs/tvalue-dirty-buckets/analyze.py').read_text();tree=ast.parse(source)
for name in ('parseview','parsevalues','checkview','replay'):
 node=next(n for n in tree.body if isinstance(n,ast.FunctionDef) and n.name==name);body=ast.get_source_segment(source,node)
 if name=='replay':body=body.replace("'order_changes':0","'order_changes':0,'term_changes':0").replace('assert old[id][1]==new[id][1];',"kinds['term_changes']+=old[id][1]!=new[id][1];")
 exec(body)
new=list(p.glob('*.result.json'));assert len(new)==156;rows={};active=0
expected_clear={'none':14,'empty':1,'clear-after-data':5,'clear-after-index':6,'clear-after-integrity':7,'clear-after-cache':12,'clear-after-last-id':13,'clear-after-endpoints':14}
for n in (1,2):
 for i,phase in enumerate(('index','data','integrity','checkpoint'),1):expected_clear['flush-after-'+phase+'-'+str(n)]=(n-1)*7+i
expected_close={'none':9,**{'flush-after-'+phase+'-1':i for i,phase in enumerate(('index','data','integrity','checkpoint'),1)},**{'close-after-'+phase:i for i,phase in enumerate(('compact','cache','index','data','endpoints'),5)}}
for f in new:
 r=json.loads(f.read_text());assert r['exit_code']==0;label=f.name.replace('.result.json','');assert not (p/(label+'.err')).read_bytes();text=gzip.decompress((p/(label+'.log.gz')).read_bytes()).decode();assert 'CLEAR_CLOSE_OK' in text
 row=next(x for x in text.splitlines() if x.startswith('CLEAR_CLOSE_NATIVE'));assert (p/(label+'.physical.txt')).read_text().strip()==row;rows.setdefault((r['operation'],r['point']),set()).add(row)
 expected=(expected_clear if r['operation']=='clear' else expected_close)[r['point']];assert row.endswith('active_checks='+str(expected));active+=expected
 fault=r['point'] not in ('none','empty');assert ('exception=none' not in row)==fault
assert len(rows)==26 and all(len(x)==1 for x in rows.values()) and active==1044
native_reg=list((p/'regression').glob('*.result.json'));assert len(native_reg)==60
for f in native_reg:
 r=json.loads(f.read_text());assert r['exit_code']==0;label=f.name.replace('.result.json','');root=p/'regression';prior=Path('docs/tvalue-delete-flush-guard');assert not (root/(label+'.err')).read_bytes()
 text=gzip.decompress((root/(label+'.log.gz')).read_bytes()).decode();oldtext=gzip.decompress((prior/(label+'.log.gz')).read_bytes()).decode();assert 'DELETE_FLUSH_OK' in text
 assert next(x for x in text.splitlines() if x.startswith('DELETE_FLUSH_NATIVE'))==next(x for x in oldtext.splitlines() if x.startswith('DELETE_FLUSH_NATIVE'))
 assert (root/(label+'.physical.txt')).read_bytes()==(prior/(label+'.physical.txt')).read_bytes()
traces={};regression=0;prefixes=0
for folder,count,prefix in [('settlement',30,'PERSISTENT_SETTLEMENT_NATIVE'),('serialization',12,'UPDATE_BOUNDARY_NATIVE'),('faults',30,'STORAGE_FAULT_NATIVE')]:
 root=p/'regression'/folder;old=Path('docs/tvalue-write-guard',folder);results=list(root.glob('*.result.json'));assert len(results)==count;regression+=len(results)
 for f in results:
  r=json.loads(f.read_text());assert r['exit_code']==0;label=f.name.replace('.result.json','');assert not (root/(label+'.err')).read_bytes();text=gzip.decompress((root/(label+'.log.gz')).read_bytes()).decode();prior=gzip.decompress((old/(label+'.log.gz')).read_bytes()).decode();assert '_OK' in text
  if r.get('mode')=='scope':assert 'SAVED_LINKS_OK checks=21' in text;continue
  assert next(x for x in text.splitlines() if x.startswith(prefix))==next(x for x in prior.splitlines() if x.startswith(prefix))
  for proof in root.glob(label+'.physical.txt'):assert proof.read_bytes()==(old/proof.name).read_bytes()
  for error in root.glob(label+'*error.txt'):assert error.read_bytes()==(old/error.name).read_bytes()
  if r['build']=='shadow':
   if folder=='settlement':suffixes=('.settlement.trace.gz','.finalization.trace.gz','.fresh.trace.gz')
   elif folder=='serialization':suffixes=('.success.trace.gz',) if r['mode']=='success' else ()
   else:suffixes=('.success.trace.gz',) if r['point']=='none' else ()
   for suffix in suffixes:
    trace=root/(label+suffix);assert trace.read_bytes()==(old/trace.name).read_bytes();traces[folder+'/'+trace.name]=replay(trace)
   for denied in root.glob(label+'.failure-prefix.trace.gz'):
    assert denied.read_bytes()==(old/denied.name).read_bytes();prefixes+=1
assert regression==72 and len(traces)==42 and sum(x['observations'] for x in traces.values())==225 and prefixes==15
build=json.loads((p/'build-validation.json').read_text());assert build['original_clear_close_bodies_recovered'] and build['prior_delete_flush_upsert_get_wrappers_unchanged'] and build['native_halt_path_recovered']
for mode,info in build['builds'].items():assert all(hashlib.sha256((Path('../build/tvalue-clear-close-guard-'+mode)/k).read_bytes()).hexdigest()==v for k,v in info['overlay_class_sha256'].items())
initial=json.loads((p/'initial-closed-check-fixture-attempt'/'build-validation.json').read_text())
for mode,info in build['builds'].items():
 assert all(v==initial['builds'][mode]['overlay_class_sha256'][k] for k,v in info['overlay_class_sha256'].items() if 'ClearCloseRunner' not in k)
for stage in ('tvalue-delete-flush-guard','tvalue-write-guard','tvalue-saved-links','tvalue-update-boundary'):
 hashes=json.loads(Path('docs',stage,'evidence-sha256.json').read_text());assert all(hashlib.sha256(Path(k).read_bytes()).hexdigest()==v for k,v in hashes.items())
assert not subprocess.check_output(['git','diff','f6ac32d2e86129d39b646e09a39d8a19889f7d4f','--','kanger/src','kanger-data-dumb/src','.github'])
attempt=json.loads((p/'initial-closed-check-fixture-attempt'/'attempt-summary.json').read_text());assert attempt['JVMs']==97 and attempt['passed']==96 and attempt['failed']==1
s={'parent':'f6ac32d2e86129d39b646e09a39d8a19889f7d4f','fresh_final_JVMs':288,'new_clear_close_JVMs':156,'regression_JVMs':132,'preliminary_JVMs':97,'preliminary_fixture_failures':1,'total_executed_JVMs':385,'all_final_expectations_met':True,'successful_journal_traces':42,'authority_views':225,'all_42_regression_successful_traces_byte_identical':True,'denied_regression_prefixes':15,'new_active_clear_close_checks':1044,'total_active_native_mutation_checks':1440,'original_clear_close_bodies_recovered':True,'only_exact_native_clear_close_flush_calls_nested':True,'closed_reader_check_preserved_and_gate_separately_checked':True,'Data_reader_journal_factory_unchanged':True,'production_changed':False,'general_crash_IO_recovery_reentrant_bulk_or_concurrent_mutations_qualified':False,'frozen_complete_corpus_semantics_equal':False,'prior_rejected_corpus_attempts_retained':2,'trace_replays':traces}
(p/'summary.json').write_text(json.dumps(s,indent=2)+'\n');print('CLEAR_CLOSE_GUARD_ANALYSIS_OK '+json.dumps({k:v for k,v in s.items() if k!='trace_replays'}))
