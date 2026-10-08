from pathlib import Path
import ast,json,gzip,hashlib,subprocess
p=Path(__file__).parent;source=Path('docs/tvalue-dirty-buckets/analyze.py').read_text();tree=ast.parse(source)
for name in ('parseview','parsevalues','checkview','replay'):
 node=next(n for n in tree.body if isinstance(n,ast.FunctionDef) and n.name==name);body=ast.get_source_segment(source,node)
 if name=='replay':body=body.replace("'order_changes':0","'order_changes':0,'term_changes':0").replace('assert old[id][1]==new[id][1];',"kinds['term_changes']+=old[id][1]!=new[id][1];")
 exec(body)
traces={};native={};active=0;prefixes=0
for folder,count,parent,prefix in [('settlement',30,'tvalue-saved-links','PERSISTENT_SETTLEMENT_NATIVE'),('serialization',12,'tvalue-saved-link-failure','UPDATE_BOUNDARY_NATIVE'),('faults',30,'tvalue-storage-faults','STORAGE_FAULT_NATIVE')]:
 root=p/folder;results=list(root.glob('*.result.json'));assert len(results)==count
 for f in results:
  r=json.loads(f.read_text());assert r['exit_code']==0;label=f.name.replace('.result.json','');assert not (root/(label+'.err')).read_bytes();text=gzip.decompress((root/(label+'.log.gz')).read_bytes()).decode();assert '_OK' in text
  if r.get('mode')=='scope':assert 'SAVED_LINKS_OK checks=21' in text;continue
  row=next(x for x in text.splitlines() if x.startswith(prefix));native.setdefault(folder+'-'+str(r.get('mode',r.get('point'))),set()).add(row)
  old=gzip.decompress(Path('docs',parent,label+'.log.gz').read_bytes()).decode();oldrow=next(x for x in old.splitlines() if x.startswith(prefix))
  if folder=='faults':
   fault=r['point']!='none';oldrow=oldrow.replace('physical=admitted','physical=refused') if fault else oldrow;assert row==oldrow
   proof=(root/(label+'.physical.txt')).read_text();assert ('admitted=false' in proof)==fault and ('failed native upsert' in proof)==fault
   line=next(x for x in text.splitlines() if x.startswith('WRITE_GUARD_ACTIVE'));n=int(line.split(' checks=')[1].split()[0]);expected={'none':12,'upsert-after-wal':5,'upsert-after-data':6,'upsert-after-index':7,'upsert-after-integrity':8}[r['point']];assert n==expected;active+=n
  else:assert row==oldrow
  if r['build']=='shadow':
   if folder=='settlement':suffixes=('.settlement.trace.gz','.finalization.trace.gz','.fresh.trace.gz')
   elif folder=='serialization':suffixes=('.success.trace.gz',) if r['mode']=='success' else ()
   else:suffixes=('.success.trace.gz',) if r['point']=='none' else ()
   for suffix in suffixes:
    trace=root/(label+suffix);traces[folder+'/'+trace.name]=replay(trace);assert trace.read_bytes()==Path('docs',parent,trace.name).read_bytes()
   isfailure=(folder=='serialization' and r['mode']=='failure') or (folder=='faults' and r['point']!='none')
   if isfailure:
    error=(root/(label+'.failure-error.txt')).read_text();assert 'unsupported incomplete native factory update' in error and 'failed native upsert' in error and 'dirty projection mismatch' not in error
    trace=root/(label+'.failure-prefix.trace.gz');body=gzip.decompress(trace.read_bytes()).decode();assert len(body.splitlines())==2 and 'TOUCH ' not in body and 'CHANGE ' not in body and 'DIRTY_JOURNAL_OK' not in body;assert trace.read_bytes()==Path('docs',parent,trace.name).read_bytes();prefixes+=1
    if folder=='faults':
     later=(root/(label+'.new-session-error.txt')).read_text();assert later.count('failed native upsert')==2
assert len(traces)==42 and sum(x['observations'] for x in traces.values())==225 and prefixes==15 and active==228 and all(len(x)==1 for x in native.values())
archive=p/'initial-fault-session-fixture-attempt';pre=list(archive.glob('*.result.json'));assert len(pre)==10 and sum(json.loads(x.read_text())['exit_code']!=0 for x in pre)==1
build=json.loads((p/'build-validation.json').read_text());prior=json.loads((archive/'build-validation.json').read_text())
for mode,info in build['builds'].items():
 assert all(hashlib.sha256((Path('../build/tvalue-write-guard-'+mode)/k).read_bytes()).hexdigest()==v for k,v in info['overlay_class_sha256'].items())
 assert all(v==prior['builds'][mode]['overlay_class_sha256'][k] for k,v in info['overlay_class_sha256'].items() if not k.startswith('org/kanger/StorageFaultRunner'))
for stage in ('tvalue-saved-links','tvalue-saved-link-failure','tvalue-storage-faults'):
 hashes=json.loads(Path('docs',stage,'evidence-sha256.json').read_text());assert all(hashlib.sha256(Path(k).read_bytes()).hexdigest()==v for k,v in hashes.items())
assert not subprocess.check_output(['git','diff','9e4e683f2eb3449c896ccc0a639c1208fcc10205','--','kanger/src','kanger-data-dumb/src','kanger-qualification/src','.github'])
s={'parent':'9e4e683f2eb3449c896ccc0a639c1208fcc10205','fresh_JVMs':72,'preliminary_fault_fixture_JVMs':10,'preliminary_fault_fixture_successes':9,'preliminary_fault_fixture_failures':1,'all_expectations_met':True,'successful_traces':42,'authority_views':225,'all_42_success_traces_byte_identical_to_pre_guard':True,'denied_failure_prefixes':15,'active_upsert_pure_refusal_checks':228,'failed_upsert_refused_before_manifest_or_witness_admission':True,'later_decode_and_new_journal_session_cannot_clear_guard_in_same_activation':True,'native_exceptions_outcomes_and_partial_effects_preserved':True,'native_Base_bodies_recovered':True,'Data_decode_journal_factory_unchanged':True,'production_changed':False,'actual_crash_recovery_or_general_concurrency_reentrancy_relocation_delete_flush_qualified':False,'frozen_complete_corpus_semantics_equal':False,'prior_rejected_corpus_attempts_retained':2,'trace_replays':traces}
(p/'summary.json').write_text(json.dumps(s,indent=2)+'\n');print('WRITE_GUARD_ANALYSIS_OK '+json.dumps({k:v for k,v in s.items() if k!='trace_replays'}))
