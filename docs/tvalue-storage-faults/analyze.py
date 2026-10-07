from pathlib import Path
import ast,json,gzip,hashlib,subprocess
p=Path(__file__).parent;source=Path('docs/tvalue-dirty-buckets/analyze.py').read_text();tree=ast.parse(source)
for name in ('parseview','parsevalues','checkview','replay'):
 node=next(n for n in tree.body if isinstance(n,ast.FunctionDef) and n.name==name);body=ast.get_source_segment(source,node)
 if name=='replay':body=body.replace("'order_changes':0","'order_changes':0,'term_changes':0").replace('assert old[id][1]==new[id][1];',"kinds['term_changes']+=old[id][1]!=new[id][1];")
 exec(body)
results=list(p.glob('*.result.json'));assert len(results)==30
native={};proofs={};errors={};traces={};prefixes=0
for f in results:
 r=json.loads(f.read_text());assert r['exit_code']==0;label=f.name.replace('.result.json','');point=r['point'];fault=point!='none';assert not (p/(label+'.err')).read_bytes()
 text=gzip.decompress((p/(label+'.log.gz')).read_bytes()).decode();assert 'STORAGE_FAULT_OK' in text;row=next(x for x in text.splitlines() if x.startswith('STORAGE_FAULT_NATIVE'));native.setdefault(point,set()).add(row)
 assert ('exception=InjectedStorageFailure' in row)==fault and ('disk_term=a' in row)==(point=='upsert-after-wal') and ('physical=refused' in row)==(point=='upsert-after-integrity')
 assert ('manifest=old' in row)==(fault and point!='upsert-after-integrity') and 'lease=held' in row
 proof=(p/(label+'.physical.txt')).read_text();proofs.setdefault(point,set()).add(proof);assert 'native_endpoints=unresolved reader=pure' in proof
 if point=='upsert-after-integrity':assert 'admitted=false rows=null' in proof and 'stale saved-link witness' in proof
 else:assert 'admitted=true rows=' in proof and ('1:0:2:0' in proof)==(point=='none') and ('1:0:1:0' in proof)==fault
 if r['build']=='shadow':
  if not fault:
   trace=p/(label+'.success.trace.gz');traces[trace.name]=replay(trace);assert traces[trace.name]['observations']==3 and traces[trace.name]['changes']==0
   body=gzip.decompress(trace.read_bytes()).decode();assert len([x for x in body.splitlines() if x.startswith('TOUCH ') and 'reason=materialize ' in x])==3
  else:
   prefix=gzip.decompress((p/(label+'.failure-prefix.trace.gz')).read_bytes()).decode();prefixes+=1;assert len(prefix.splitlines())==2 and 'TOUCH ' not in prefix and 'CHANGE ' not in prefix and 'DIRTY_JOURNAL_OK' not in prefix
   error=(p/(label+'.failure-error.txt')).read_text();errors.setdefault(point,set()).add(error);assert 'unsupported incomplete native factory update' in error
   if point=='upsert-after-integrity':assert 'stale saved-link witness' in error
   else:assert 'dirty projection mismatch ctx=1 reason=session-end' in error and 'shadow={0=[0:3:0,1:2:0,2:4:0]} authority={0=[0:3:0,1:1:0,2:4:0]}' in error
assert len(traces)==3 and prefixes==12 and all(len(x)==1 for x in [*native.values(),*proofs.values(),*errors.values()])
assert len({(p/('none-shadow-'+f+'.success.trace.gz')).read_bytes() for f in ('on','off','verify')})==1
for point in ('upsert-after-wal','upsert-after-data','upsert-after-index','upsert-after-integrity'):
 assert len({(p/(point+'-shadow-'+f+'.failure-prefix.trace.gz')).read_bytes() for f in ('on','off','verify')})==1
for stage in ('tvalue-saved-links','tvalue-update-boundary','tvalue-storage-faults'):
 info=json.loads(Path('docs',stage,'build-validation.json').read_text())
 for mode,build in info['builds'].items():assert all(hashlib.sha256((Path('../build',stage+'-'+mode)/k).read_bytes()).hexdigest()==v for k,v in build['overlay_class_sha256'].items())
for stage in ('tvalue-saved-links','tvalue-saved-link-failure'):
 hashes=json.loads(Path('docs',stage,'evidence-sha256.json').read_text());assert all(hashlib.sha256(Path(k).read_bytes()).hexdigest()==v for k,v in hashes.items())
assert not subprocess.check_output(['git','diff','79b5fe1da42a523d5f56c8b32101ff32248a0064','--','kanger/src','kanger-data-dumb/src','kanger-qualification/src','.github'])
s={'parent':'79b5fe1da42a523d5f56c8b32101ff32248a0064','fresh_JVMs':30,'preliminary_JVM_failures':0,'all_expectations_met':True,'successful_traces':3,'authority_views':sum(x['observations'] for x in traces.values()),'denied_failure_prefixes':12,'fault_points':['upsert-after-wal','upsert-after-data','upsert-after-index','upsert-after-integrity'],'factory_update_refuses_all_faults_before_finish_oracle':True,'pending_materialization_not_published_on_failure':True,'physical_reader_admits_old_cache_before_manifest_replacement':True,'disk_payload_changes_before_manifest_replacement_demonstrated':True,'physical_reader_refuses_stale_witness_after_manifest_replacement':True,'standalone_reader_unsettled_write_admission_qualified':False,'recorder_reader_Data_Base_journal_factory_unchanged':True,'native_halt_path_recovered_not_executed':True,'actual_process_crash_recovery_qualified':False,'production_changed':False,'general_IO_relocation_concurrency_reentrancy_or_corruption_qualified':False,'frozen_complete_corpus_semantics_equal':False,'prior_rejected_corpus_attempts_retained':2,'trace_replays':traces}
(p/'summary.json').write_text(json.dumps(s,indent=2)+'\n');print('STORAGE_FAULT_ANALYSIS_OK '+json.dumps({k:v for k,v in s.items() if k!='trace_replays'}))
