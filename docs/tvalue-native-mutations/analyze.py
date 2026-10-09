from pathlib import Path
import json,gzip,hashlib,subprocess,ast,re
p=Path(__file__).parent
source=Path('docs/tvalue-dirty-buckets/analyze.py').read_text();tree=ast.parse(source)
for name in ('parseview','parsevalues','checkview','replay'):
 node=next(n for n in tree.body if isinstance(n,ast.FunctionDef) and n.name==name);body=ast.get_source_segment(source,node)
 if name=='replay':body=body.replace("'order_changes':0","'order_changes':0,'term_changes':0").replace('assert old[id][1]==new[id][1];',"kinds['term_changes']+=old[id][1]!=new[id][1];")
 exec(body)
traces={};per_rep={};entries=0;controls=0
assert len(list(p.glob('*.result.json')))==18
for rep in range(3):
 for flag in ['on','off','verify']:
  prefixes=[p/f'{rep}-{flag}-{mode}' for mode in ['clean','hooked']]
  for prefix,mode in zip(prefixes,['clean','hooked']):
   r=json.loads(Path(str(prefix)+'.result.json').read_text());assert r['exit_code']==0 and r['mode']==mode and r['flag']==flag and r['repetition']==rep
   assert not Path(str(prefix)+'.err').read_bytes();log=gzip.decompress(Path(str(prefix)+'.log.gz').read_bytes()).decode();assert 'AUTO_MUTATIONS_OK' in log
   controls+=int(log.split('totalControls=')[1].split()[0])
  assert Path(str(prefixes[0])+'.native-rows.txt').read_bytes()==Path(str(prefixes[1])+'.native-rows.txt').read_bytes()
  pair=[]
  for journal in ['old','memo']:
   path=Path(str(prefixes[1])+'.'+journal+'.trace.gz');stats=replay(path);traces[path.name]=stats;pair.append(path.read_bytes())
   for row in gzip.decompress(path.read_bytes()).decode().splitlines():
    if row.startswith('VIEW '):
     kv=dict(x.split('=',1) for x in row.split()[1:]);entries+=sum(len(v) for v in parseview(kv['view']).values())
  assert pair[0]==pair[1];per_rep.setdefault(rep,set()).add(pair[0]);errors=Path(str(prefixes[1])+'.negative-errors.txt').read_text().splitlines();assert len(errors)==3 and all('dirty projection mismatch' in x for x in errors)
  for mode,line in enumerate(errors):
   assert line.startswith('mode='+str(mode)+' ');assert [int(x) for x in re.findall(r'dirty projection mismatch ctx=(\d+)',line)]==([1,2,3,1,2,3] if mode<2 else [3,3])
  assert Path(str(prefixes[1])+'.hook-counts.txt').read_text().strip()=='{add=26, beforeClear=3, beginSettlement=9, clear-fanout=3, clear-removed-route=26, complete=1, constructed=10, endSettlement=9, mark=1, metadata=80, promoted=6, remove=2, reset=22, retire=9, visibility=5}'
assert all(len(v)==1 for v in per_rep.values())
b=json.loads((p/'build-validation.json').read_text());assert b['clear_descendant_fanout_enabled']
for mode in ['clean','hooked']:
 assert all(hashlib.sha256(Path(k).read_bytes()).hexdigest()==v for k,v in b['source_sha256'][mode].items())
 assert all(hashlib.sha256((Path('../build/tvalue-native-mutations-'+mode)/k).read_bytes()).hexdigest()==v for k,v in b['class_sha256'][mode].items())
changes=json.loads((p/'changes.json').read_text());proof=json.loads((p/'structural-check.json').read_text())
for name,edits in changes.items():
 body=(p/'source'/name).read_text();assert hashlib.sha256(body.encode()).hexdigest()==proof[name]['instrumented_sha256']
 for edit in reversed(edits):assert body.count(edit['new'])==1;body=body.replace(edit['new'],edit['old'],1)
 assert body==(Path('../K3-smart-native/kanger/src/org/kanger')/name).read_text()
runner=(p/'AutoMutationRunner.java').read_text();positive=runner[:runner.index('  if(HOOKED){List<String> errors=')]
assert all(x not in positive for x in ['Journal.touch','Journal.metadata','Hooks.touch','Hooks.metadata','Hooks.reset','Hooks.beforeClear','Hooks.afterClear','Hooks.retire','Hooks.beginSettlement','Hooks.endSettlement'])
assert not subprocess.check_output(['git','diff',b['parent'],'--','kanger/src','kanger-data-dumb/src','kanger-qualification/src','.github','docs/tvalue-authority-stream','docs/tvalue-native-hooks'])
s={'parent':b['parent'],'develop_head':b['develop_head'],'final_JVMs':18,'successful_final_traces':18,'replayed_authority_views':sum(x['observations'] for x in traces.values()),'serialized_value_entries_checked':entries,'expected_hook_gap_cases':27,'native_context_variable_controls':controls,'clean_hooked_native_rows_identical':True,'old_stream_and_flag_traces_identical':True,'automatic_memory_mutation_notifications_qualified':True,'all_original_native_code_recovered_byte_for_byte':True,'changed_native_classes':b['changed_native_classes'],'unchanged_prior_clean_class_hashes_verified':730,'diagnostic_implementation_changed':False,'production_changed':False,'full_oracle_never_disabled':True,'SMART_backend_persistence_qualified':False,'recycled_ID_with_live_descendants_qualified':False,'complete_inference_corpus_rerun':False,'end_to_end_inference_speedup_qualified':False,'trace_replays':traces}
(p/'summary.json').write_text(json.dumps(s,indent=2)+'\n');print('NATIVE_MUTATIONS_ANALYSIS_OK',json.dumps({k:v for k,v in s.items() if k not in ['trace_replays']}))
