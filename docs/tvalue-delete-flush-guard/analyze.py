from pathlib import Path
import ast,json,gzip,hashlib,subprocess
p=Path(__file__).parent;source=Path('docs/tvalue-dirty-buckets/analyze.py').read_text();tree=ast.parse(source)
for name in ('parseview','parsevalues','checkview','replay'):
 node=next(n for n in tree.body if isinstance(n,ast.FunctionDef) and n.name==name);body=ast.get_source_segment(source,node)
 if name=='replay':body=body.replace("'order_changes':0","'order_changes':0,'term_changes':0").replace('assert old[id][1]==new[id][1];',"kinds['term_changes']+=old[id][1]!=new[id][1];")
 exec(body)
new=list(p.glob('*.result.json'));assert len(new)==60;rows={};active=0
for f in new:
 r=json.loads(f.read_text());assert r['exit_code']==0;label=f.name.replace('.result.json','');assert not (p/(label+'.err')).read_bytes();text=gzip.decompress((p/(label+'.log.gz')).read_bytes()).decode();assert 'DELETE_FLUSH_OK' in text
 row=next(x for x in text.splitlines() if x.startswith('DELETE_FLUSH_NATIVE'));rows.setdefault((r['operation'],r['point']),set()).add(row);assert (p/(label+'.physical.txt')).read_text().strip()==row
 fault=r['point']!='none';assert ('exception=InjectedMutationFailure' in row)==fault and ('physical=refused' in row)==fault
 if r['operation']=='delete':
  assert ('indexed_head=true' in row)==(r['point']=='delete-after-wal') and ('manifest_head=true' in row)==(fault and r['point']!='delete-after-integrity') and 'cached_head=false pending_WAL=true' in row
  expected={'none':4,'delete-after-wal':1,'delete-after-index':2,'delete-after-data':3,'delete-after-integrity':4}[r['point']]
 else:
  assert 'indexed_head=true manifest_head=true cached_head=true' in row and ('pending_WAL=true' in row)==(fault and r['point']!='flush-after-checkpoint')
  expected={'none':4,'flush-after-index':1,'flush-after-data':2,'flush-after-integrity':3,'flush-after-checkpoint':4}[r['point']]
 assert row.endswith('active_checks='+str(expected));active+=expected
assert len(rows)==10 and all(len(x)==1 for x in rows.values()) and active==168
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
build=json.loads((p/'build-validation.json').read_text());assert build['original_delete_flush_bodies_recovered'] and build['prior_upsert_and_get_wrappers_unchanged'] and build['native_halt_path_recovered']
for mode,info in build['builds'].items():assert all(hashlib.sha256((Path('../build/tvalue-delete-flush-guard-'+mode)/k).read_bytes()).hexdigest()==v for k,v in info['overlay_class_sha256'].items())
for stage in ('tvalue-write-guard','tvalue-saved-links','tvalue-update-boundary'):
 hashes=json.loads(Path('docs',stage,'evidence-sha256.json').read_text());assert all(hashlib.sha256(Path(k).read_bytes()).hexdigest()==v for k,v in hashes.items())
assert not subprocess.check_output(['git','diff','82cf4a2cad984d62bea22187031c4b6701085828','--','kanger/src','kanger-data-dumb/src','.github'])
s={'parent':'82cf4a2cad984d62bea22187031c4b6701085828','fresh_JVMs':132,'new_delete_flush_JVMs':60,'regression_JVMs':72,'preliminary_JVM_failures':0,'all_expectations_met':True,'successful_journal_traces':42,'authority_views':225,'all_42_regression_successful_traces_byte_identical':True,'denied_regression_prefixes':15,'new_active_delete_flush_pure_refusal_checks':168,'inherited_active_upsert_checks':228,'total_active_native_mutation_checks':396,'failed_delete_flush_refused_with_known_endpoints':True,'successful_head_delete_and_flush_physically_admitted':True,'empty_pending_WAL_after_checkpoint_exception_does_not_clear_failure':True,'original_delete_flush_bodies_recovered':True,'Data_reader_journal_factory_unchanged':True,'production_changed':False,'general_crash_recovery_clear_close_relocation_bulk_or_reentrant_mutations_qualified':False,'frozen_complete_corpus_semantics_equal':False,'prior_rejected_corpus_attempts_retained':2,'trace_replays':traces}
(p/'summary.json').write_text(json.dumps(s,indent=2)+'\n');print('DELETE_FLUSH_GUARD_ANALYSIS_OK '+json.dumps({k:v for k,v in s.items() if k!='trace_replays'}))
