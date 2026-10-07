from pathlib import Path
import ast,gzip,json,re,hashlib,subprocess
p=Path(__file__).parent
source=Path('docs/tvalue-dirty-buckets/analyze.py').read_text();tree=ast.parse(source)
for name in ('parseview','parsevalues','checkview','replay'):
 node=next(n for n in tree.body if isinstance(n,ast.FunctionDef) and n.name==name);body=ast.get_source_segment(source,node)
 if name=='replay':body=body.replace("'order_changes':0","'order_changes':0,'term_changes':0").replace('assert old[id][1]==new[id][1];',"kinds['term_changes']+=old[id][1]!=new[id][1];")
 exec(body)
results=list(p.glob('*.result.json'));assert len(results)==18
rows={};traces={}
for f in results:
 r=json.loads(f.read_text());assert r['exit_code']==0 and not f.with_name(f.name.replace('.result.json','.err')).read_bytes()
 label=f.name.replace('.result.json','');log=gzip.decompress((p/(label+'.log.gz')).read_bytes()).decode();assert 'STORAGE_TRANSITION_OK' in log and 'subsequent_rejection=false' in log
 native=next(x for x in log.splitlines() if x.startswith('STORAGE_TRANSITION_NATIVE'));rows.setdefault(r['mode'],set()).add(native)
 shadow=r['build']=='shadow';assert ('update_rejected=true' in log)==shadow
 if shadow:
  error=(p/(label+'.update-error.txt')).read_text();assert error.startswith('journal errors [') and error.count('materialization requires valid native lookup metadata')==2
  if r['mode']!='update':
   trace=p/(label+'.trace.gz');text=gzip.decompress(trace.read_bytes()).decode();traces[trace.name]=replay(trace)
   assert 'reason=post-transition-setter' in text and 'reason=setPersistentReferences variable=0 value=1 term=2' in text
   if r['mode']=='generation':assert traces[trace.name]['resets']==1 and traces[trace.name]['seeds']==2 and traces[trace.name]['transitions']['term_changes']==1
   else:assert traces[trace.name]['contexts']==2 and text.count('reason=promote ')==8 and traces[trace.name]['transitions']['term_changes']==2
assert all(len(v)==1 for v in rows.values()) and len(traces)==6
for mode in ('generation','publication'):
 assert len({(p/(mode+'-shadow-'+flag+'.trace.gz')).read_bytes() for flag in ('on','off','verify')})==1
manifest=json.loads(Path('docs/tvalue-materialization-routing/evidence-sha256.json').read_text());assert all(hashlib.sha256(Path(k).read_bytes()).hexdigest()==v for k,v in manifest.items())
prior=json.loads(Path('docs/tvalue-materialization-routing/summary.json').read_text());assert prior['final_matrix_JVMs']==71 and prior['replayed_authority_views']==9216 and not prior['frozen_complete_corpus_semantics_equal'] and prior['prior_rejected_corpus_attempts_retained']==2
assert not subprocess.check_output(['git','diff','8ca65c10e6d1cbe3a1cb4f3d46140ea65fe4b187','--','kanger/src','kanger-data-dumb/src','kanger-qualification/src','.github'])
summary={'parent':'8ca65c10e6d1cbe3a1cb4f3d46140ea65fe4b187','fresh_JVMs':18,'all_expectations_met':True,'native_rows_equal_clean_shadow_and_flags':True,'update_sessions_rejected':9,'positive_new_sessions':6,'success_traces':6,'authority_views':sum(x['observations'] for x in traces.values()),'native_update_intermediate_lookup_metadata_qualified':False,'dirty_setter_gap_in_isolation_proven':False,'generation_reset_and_typed_factory_publication_only':True,'composite_persistent_Mind_publication_qualified':False,'inherited_71_JVM_evidence_unchanged_not_rerun':True,'production_changed':False,'prior_rejected_corpus_attempts_retained':2,'frozen_complete_corpus_semantics_equal':False,'trace_replay':traces}
(p/'summary.json').write_text(json.dumps(summary,indent=2)+'\n');print('STORAGE_TRANSITION_ANALYSIS_OK fresh=18 rejected_update=9 positive_sessions=6 views='+str(summary['authority_views']))
