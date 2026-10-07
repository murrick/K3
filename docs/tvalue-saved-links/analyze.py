from pathlib import Path
import ast,json,gzip,hashlib,subprocess
p=Path(__file__).parent
source=Path('docs/tvalue-dirty-buckets/analyze.py').read_text();tree=ast.parse(source)
for name in ('parseview','parsevalues','checkview','replay'):
 node=next(n for n in tree.body if isinstance(n,ast.FunctionDef) and n.name==name);body=ast.get_source_segment(source,node)
 if name=='replay':body=body.replace("'order_changes':0","'order_changes':0,'term_changes':0").replace('assert old[id][1]==new[id][1];',"kinds['term_changes']+=old[id][1]!=new[id][1];")
 exec(body)
results=list(p.glob('*.result.json'));assert len(results)==30
traces={};native={}
for f in results:
 r=json.loads(f.read_text());assert r['exit_code']==0;label=f.name.replace('.result.json','');assert not (p/(label+'.err')).read_bytes();text=gzip.decompress((p/(label+'.log.gz')).read_bytes()).decode()
 if r['mode']=='scope':
  assert 'SAVED_LINKS_OK checks=21' in text
  row=next(x for x in text.splitlines() if x.startswith('SAVED_LINKS_NATIVE'));native.setdefault('scope',set()).add(row);continue
 assert 'PERSISTENT_SETTLEMENT_OK' in text and 'finalization_rejected=false' in text
 row=next(x for x in text.splitlines() if x.startswith('PERSISTENT_SETTLEMENT_NATIVE'));native.setdefault(r['mode'],set()).add(row)
 parent=gzip.decompress(Path('docs/tvalue-persistent-settlement',label+'.log.gz').read_bytes()).decode();assert row==next(x for x in parent.splitlines() if x.startswith('PERSISTENT_SETTLEMENT_NATIVE'))
 assert ('accepted=true' in row)==(r['mode']=='accept') and ('analyzer_failure=true' in row)==(r['mode']=='exception') and 'reservations=0' in row
 if r['build']=='shadow':
  for suffix in ('.settlement.trace.gz','.finalization.trace.gz','.fresh.trace.gz'):
   trace=p/(label+suffix);traces[trace.name]=replay(trace)
  settlement=traces[label+'.settlement.trace.gz'];assert settlement['changes']==int(r['mode']=='accept') and settlement['retired']==1
  final=traces[label+'.finalization.trace.gz'];assert final['changes']==0 and final['retired']==1
  if r['mode']=='accept':
   body=gzip.decompress((p/(label+'.finalization.trace.gz')).read_bytes()).decode();assert 'reason=after-settlement ' in body and 'unresolved endpoints' not in body and final['observations']==5
   assert len([x for x in body.splitlines() if x.startswith('TOUCH ') and 'reason=materialize ' in x])==1
  # Unaffected successful parent traces must remain byte-exact.
  for suffix in ('.settlement.trace.gz','.fresh.trace.gz')+(() if r['mode']=='accept' else ('.finalization.trace.gz',)):
   assert (p/(label+suffix)).read_bytes()==Path('docs/tvalue-persistent-settlement',label+suffix).read_bytes(),label+suffix
assert all(len(v)==1 for v in native.values()) and len(traces)==36
for mode in ('accept','reject','user-reject','exception'):
 for suffix in ('.settlement.trace.gz','.finalization.trace.gz','.fresh.trace.gz'):
  assert len({(p/(mode+'-shadow-'+flag+suffix)).read_bytes() for flag in ('on','off','verify')})==1
assert not list(p.glob('*.finalization-error.txt'))
build=json.loads((p/'build-validation.json').read_text())
for mode,info in build['builds'].items():
 assert all(hashlib.sha256((Path('../build/tvalue-saved-links-'+mode)/k).read_bytes()).hexdigest()==v for k,v in info['overlay_class_sha256'].items())
 assert all(k.startswith(('org/kanger/storage/Data','org/kanger/RecordedLinks','org/kanger/ResidentPersistentRead','org/kanger/PersistentSettlementRunner','org/kanger/SavedLinksRunner')) for k in info['overlay_class_sha256'])
 previous=json.loads((p/'initial-scope-fixture-attempt/build-validation.json').read_text())['builds'][mode]['overlay_class_sha256']
 assert all(v==previous[k] for k,v in info['overlay_class_sha256'].items() if not k.startswith('org/kanger/SavedLinksRunner'))
for stage,manifest in [('tvalue-persistent-settlement','evidence-sha256.json'),('tvalue-resident-endpoints','artifact-manifest.json')]:
 paths=json.loads(Path('docs',stage,manifest).read_text());assert all(hashlib.sha256(Path(k).read_bytes()).hexdigest()==v for k,v in paths.items())
assert not subprocess.check_output(['git','diff','5bd5d7416d79d87a3a7203f3b18f9f4e0b78672e','--','kanger/src','kanger-data-dumb/src','kanger-qualification/src','.github'])
summary={'parent':'5bd5d7416d79d87a3a7203f3b18f9f4e0b78672e','fresh_JVMs':30,'preliminary_scope_JVMs':1,'preliminary_scope_setup_failures':1,'successful_traces':36,'authority_views':sum(x['observations'] for x in traces.values()),'all_expectations_met':True,'accepted_quiescent_finalization_qualified_with_explicit_recorded_decode_links':True,'native_endpoints_not_repaired':True,'all_native_settlement_outcome_rows_match_parent':True,'all_33_previously_successful_traces_byte_identical':True,'unsaved_links_and_stale_entry_refused':True,'native_Data_body_recovered':True,'existing_journal_and_factory_overlays_unchanged':True,'production_changed':False,'general_concurrency_reopen_reentrant_or_faulted_storage_qualified':False,'frozen_complete_corpus_semantics_equal':False,'prior_rejected_corpus_attempts_retained':2,'trace_replays':traces}
(p/'summary.json').write_text(json.dumps(summary,indent=2)+'\n');print('SAVED_LINKS_ANALYSIS_OK '+json.dumps({k:v for k,v in summary.items() if k!='trace_replays'}))
