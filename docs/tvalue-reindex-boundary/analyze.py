from pathlib import Path
import json,gzip,hashlib,subprocess,ast
p=Path(__file__).parent;rows={}
results=list(p.glob('*.result.json'));assert len(results)==48
for q in results:
 r=json.loads(q.read_text());assert r['exit_code']==0;label=q.name.replace('.result.json','');assert not (p/(label+'.err')).read_bytes();text=gzip.decompress((p/(label+'.log.gz')).read_bytes()).decode();db=label.startswith('db-');prefix='DB_REINDEX_NATIVE' if db else 'REINDEX_NATIVE';row=next(x for x in text.splitlines() if x.startswith(prefix));assert (p/(label+'.physical.txt')).read_text().strip()==row;rows.setdefault((db,r['stop']),set()).add(row);assert '_OK' in text
 fault=r['stop']!=0;n=r['stop'] if fault else 3
 if db:assert 'copied='+str(n) in row and 'published='+('original' if fault else 'new') in row and 'source_closed='+str(not fault).lower() in row and 'target_closed=true authoritative_rows=3' in row
 else:assert 'source=3 target='+str(n) in row and 'physical=admitted whole_copy='+('failed' if fault else 'returned') in row
 assert ('exception=none' not in row)==fault
assert len(rows)==8 and all(len(v)==1 for v in rows.values())
source=Path('docs/tvalue-dirty-buckets/analyze.py').read_text();tree=ast.parse(source)
for name in ('parseview','parsevalues','checkview','replay'):
 node=next(n for n in tree.body if isinstance(n,ast.FunctionDef) and n.name==name);body=ast.get_source_segment(source,node)
 if name=='replay':body=body.replace("'order_changes':0","'order_changes':0,'term_changes':0").replace('assert old[id][1]==new[id][1];',"kinds['term_changes']+=old[id][1]!=new[id][1];")
 exec(body)
traces={};reg=list((p/'regression').glob('*.result.json'));assert len(reg)==36
for q in reg:
 r=json.loads(q.read_text());assert r['exit_code']==0;label=q.name.replace('.result.json','');root=q.parent;assert not (root/(label+'.err')).read_bytes();text=gzip.decompress((root/(label+'.log.gz')).read_bytes()).decode();assert '_OK' in text
 fixture=r['fixture'];suffix=r['build']+'-'+r['flag']
 if fixture in ('delete','flush'):old=Path('docs/tvalue-delete-flush-guard');oldlabel=fixture+'-none-'+suffix;prefix='DELETE_FLUSH_NATIVE'
 elif fixture in ('clear','close'):old=Path('docs/tvalue-clear-close-guard');oldlabel=fixture+'-none-'+suffix;prefix='CLEAR_CLOSE_NATIVE'
 elif fixture=='upsert':old=Path('docs/tvalue-write-guard/faults');oldlabel='none-'+suffix;prefix='STORAGE_FAULT_NATIVE'
 else:old=Path('docs/tvalue-write-guard/settlement');oldlabel='scope-'+suffix;prefix='SAVED_LINKS_OK'
 prior=gzip.decompress((old/(oldlabel+'.log.gz')).read_bytes()).decode();assert next(x for x in text.splitlines() if x.startswith(prefix))==next(x for x in prior.splitlines() if x.startswith(prefix))
 for proof in root.glob(label+'.physical.txt'):assert proof.read_bytes()==(old/(oldlabel+'.physical.txt')).read_bytes()
 for trace in root.glob(label+'.success.trace.gz'):
  assert trace.read_bytes()==(old/(oldlabel+'.success.trace.gz')).read_bytes();traces[trace.name]=replay(trace)
assert len(traces)==3 and sum(x['observations'] for x in traces.values())==9
build=json.loads((p/'build-validation.json').read_text());assert build['native_reindex_body_recovered']
for mode,info in build['builds'].items():assert all(hashlib.sha256((Path('../build/tvalue-reindex-boundary-'+mode)/k).read_bytes()).hexdigest()==v for k,v in info['overlay_class_sha256'].items())
regbuild=json.loads((p/'initial-DB-reopen-fixture-attempt/build-validation.json').read_text())
for mode,info in build['builds'].items():assert all(regbuild['builds'][mode]['overlay_class_sha256'][k]==v for k,v in info['overlay_class_sha256'].items() if 'DBReindexRunner' not in k)
initial=[]
for folder in ('initial-standalone-copy-proof','initial-DB-copy-fixture-attempt','initial-DB-reopen-fixture-attempt','initial-semantic-reopen-fixture-attempt'):
 initial.extend(json.loads(q.read_text()) for q in (p/folder).glob('*.result.json'))
prior=list((p/'successful-physical-DB-proof-before-binding-check').glob('*.result.json'));assert len(prior)==24 and all(json.loads(q.read_text())['exit_code']==0 for q in prior)
assert len(initial)==100 and sum(r['exit_code']!=0 for r in initial)==4
for stage in ('tvalue-clear-close-guard','tvalue-delete-flush-guard','tvalue-write-guard','tvalue-saved-links','tvalue-update-boundary'):
 hashes=json.loads(Path('docs',stage,'evidence-sha256.json').read_text());assert all(hashlib.sha256(Path(k).read_bytes()).hexdigest()==v for k,v in hashes.items())
assert not subprocess.check_output(['git','diff','c64805f7535dcc384439e935605fea1e1075784e','--','kanger/src','kanger-data-dumb/src','.github'])
s={'parent':'c64805f7535dcc384439e935605fea1e1075784e','fresh_final_JVMs':84,'new_standalone_reindex_JVMs':24,'new_native_DB_reindex_JVMs':24,'focused_regression_JVMs':36,'all_final_expectations_met':True,'archived_preliminary_or_diagnostic_JVMs':100,'archived_preliminary_fixture_failures':4,'additional_reused_home_startup_attempts':1,'additional_successful_physical_DB_JVMs_before_binding_checks':24,'total_executed_fixture_JVMs':209,'successful_regression_journal_traces':3,'authority_views':9,'successful_regression_traces_byte_identical':True,'native_DB_copy_failures_keep_original_published_Base_and_core_files':True,'native_DB_success_publishes_complete_new_generation_with_ordered_schema_acquisition':True,'standalone_partial_copy_physically_admitted':True,'native_temporary_cache_source_bound_nodes_physically_refused':True,'existing_composite_active_Base_identity_gate_selects_source_and_refuses_stale_owner_after_swap':True,'whole_copy_guard_added':False,'production_changed':False,'general_crash_atomicity_rollback_IO_failure_concurrency_semantic_reopen_or_full_reindex_space_qualified':False,'frozen_complete_corpus_semantics_equal':False,'prior_rejected_corpus_attempts_retained':2,'trace_replays':traces}
(p/'summary.json').write_text(json.dumps(s,indent=2)+'\n');print('REINDEX_BOUNDARY_ANALYSIS_OK '+json.dumps({k:v for k,v in s.items() if k!='trace_replays'}))
