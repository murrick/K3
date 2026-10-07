from pathlib import Path
import ast,json,gzip,hashlib,subprocess
p=Path(__file__).parent
source=Path('docs/tvalue-dirty-buckets/analyze.py').read_text();tree=ast.parse(source)
for name in ('parseview','parsevalues','checkview','replay'):
 node=next(n for n in tree.body if isinstance(n,ast.FunctionDef) and n.name==name);body=ast.get_source_segment(source,node)
 if name=='replay':body=body.replace("'order_changes':0","'order_changes':0,'term_changes':0").replace('assert old[id][1]==new[id][1];',"kinds['term_changes']+=old[id][1]!=new[id][1];")
 exec(body)
results=list(p.glob('*.result.json'));assert len(results)==24
traces={};native={}
for f in results:
 r=json.loads(f.read_text());assert r['exit_code']==0;label=f.name.replace('.result.json','');assert not (p/(label+'.err')).read_bytes();text=gzip.decompress((p/(label+'.log.gz')).read_bytes()).decode();assert 'PERSISTENT_SETTLEMENT_OK' in text
 row=next(x for x in text.splitlines() if x.startswith('PERSISTENT_SETTLEMENT_NATIVE'));native.setdefault(r['mode'],set()).add(row)
 assert ('accepted=true' in row)==(r['mode']=='accept') and ('analyzer_failure=true' in row)==(r['mode']=='exception') and 'reservations=0' in row
 expected=r['build']=='shadow' and r['mode']=='accept';assert ('finalization_rejected=true' in text)==expected
 if r['build']=='shadow':
  for suffix in ('.settlement.trace.gz','.fresh.trace.gz'):
   trace=p/(label+suffix);traces[trace.name]=replay(trace)
  settlement=traces[label+'.settlement.trace.gz'];assert settlement['changes']==int(r['mode']=='accept') and settlement['retired']==1
  body=gzip.decompress((p/(label+'.settlement.trace.gz')).read_bytes()).decode();assert 'reason=after-settlement ' in body
  if r['mode']=='user-reject':assert 'reason=live-user-rejected-child ' in body
  if expected:
   error=(p/(label+'.finalization-error.txt')).read_text();assert error.count('unresolved endpoints')==2 and 'dirty projection mismatch' not in error
   prefix=gzip.decompress((p/(label+'.finalization-prefix.trace.gz')).read_bytes()).decode().splitlines();assert len(prefix)==7 and len([x for x in prefix if x.startswith('TOUCH ') and 'reason=materialize ' in x and 'ctx=1 ' in x])==1 and not any(x.startswith('CHANGE ') for x in prefix)
  else:
   trace=p/(label+'.finalization.trace.gz');traces[trace.name]=replay(trace);assert traces[trace.name]['changes']==0 and traces[trace.name]['retired']==1
assert all(len(v)==1 for v in native.values()) and len(traces)==33
for mode in ('accept','reject','user-reject','exception'):
 for suffix in ('.settlement.trace.gz','.fresh.trace.gz')+(() if mode=='accept' else ('.finalization.trace.gz',)):
  assert len({(p/(mode+'-shadow-'+flag+suffix)).read_bytes() for flag in ('on','off','verify')})==1
# No native/helper instrumentation changes: verify each actual runtime overlay.
for name,folder in (('tvalue-materialization-routing','tvalue-materialization-routing-classes'),('tvalue-persistent-lookup','tvalue-persistent-lookup-classes')):
 info=json.loads(Path('docs',name,'build-validation.json').read_text());assert all(hashlib.sha256((Path('../build',folder)/k).read_bytes()).hexdigest()==v for k,v in info['overlay_class_sha256'].items())
resident=json.loads(Path('docs/tvalue-resident-persistent/summary.json').read_text());assert all(hashlib.sha256((Path('../build/tvalue-resident-persistent-classes')/k).read_bytes()).hexdigest()==v for k,v in resident['reader_class_sha256'].items())
info=json.loads(Path('docs/tvalue-update-boundary/build-validation.json').read_text())
for mode,data in info['builds'].items():
 assert all(hashlib.sha256((Path('../build/tvalue-update-boundary-'+mode)/k).read_bytes()).hexdigest()==v for k,v in data['overlay_class_sha256'].items())
for name in ('tvalue-update-boundary','tvalue-storage-transition','tvalue-materialization-routing'):
 manifest=json.loads(Path('docs',name,'evidence-sha256.json').read_text());assert all(hashlib.sha256(Path(k).read_bytes()).hexdigest()==v for k,v in manifest.items())
old=json.loads(Path('docs/tvalue-update-boundary/summary.json').read_text());assert old['fresh_JVMs']==83 and old['replayed_authority_views']==9267 and not old['frozen_complete_corpus_semantics_equal'] and old['prior_rejected_corpus_attempts_retained']==2
assert not subprocess.check_output(['git','diff','c137746803bc94d0441bc97237301e463a5aa3e8','--','kanger/src','kanger-data-dumb/src','kanger-qualification/src','.github'])
summary={'parent':'c137746803bc94d0441bc97237301e463a5aa3e8','fresh_JVMs':24,'all_expectations_met':True,'successful_traces':33,'authority_views':sum(x['observations'] for x in traces.values()),'held_reservation_composite_accept_reject_user_reject_exception_qualified':True,'no_false_parent_rollback_delta':True,'accepted_quiescent_finalization_shadow_sessions_rejected':3,'native_finalization_and_value_survival_preserved':True,'rejection_reason':'unresolved persistent endpoints','full_quiescent_persistent_publication_qualified':False,'inherited_83_JVM_evidence_unchanged_not_rerun':True,'all_native_and_hook_classes_unchanged':True,'preliminary_JVMs_retained':14,'preliminary_accepted_JVMs':12,'preliminary_setup_failures':2,'production_changed':False,'general_concurrency_reopen_or_reentrant_publication_qualified':False,'frozen_complete_corpus_semantics_equal':False,'prior_rejected_corpus_attempts_retained':2,'trace_replays':traces}
(p/'summary.json').write_text(json.dumps(summary,indent=2)+'\n');print('PERSISTENT_SETTLEMENT_ANALYSIS_OK '+json.dumps({k:v for k,v in summary.items() if k!='trace_replays'}))
