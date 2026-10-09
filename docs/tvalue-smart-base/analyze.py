from pathlib import Path
import json,gzip,hashlib,subprocess,ast
root=Path(__file__).parent
# Execute the retained independent replay and semantic gates, without overwriting previous evidence.
scoped={}
for folder,source in [('contexts','docs/tvalue-authority-contexts/analyze.py'),('parent-add','docs/tvalue-parent-add/analyze.py')]:
 text=Path(source).read_text().split('build=json.loads')[0]
 text=text.replace('p=Path(__file__).parent',f'p=root/"{folder}"')
 env={'__file__':__file__,'root':root};exec(text,env)
 scoped[folder]={'traces':len(env['traces']),'views':sum(x['observations'] for x in env['traces'].values()),'entries':env['entries'],'trace_replays':env['traces']}
 prior=Path('docs/tvalue-authority-contexts' if folder=='contexts' else 'docs/tvalue-parent-add')
 for path in (root/folder).glob('*.trace.gz'):assert path.read_bytes()==(prior/path.name).read_bytes(),path
 for path in (root/folder).glob('*.negative-errors.txt'):assert path.read_bytes()==(prior/path.name).read_bytes(),path
replay=env['replay'];parseview=env['parseview'];entries=0;views=0;scope_traces={};flag_traces=set()
for q in (root/'scope').glob('*.result.json'):
 r=json.loads(q.read_text());label=q.name.removesuffix('.result.json');assert r['exit_code']==0 and not q.with_name(label+'.err').read_bytes()
 log=gzip.decompress(q.with_name(label+'.log.gz').read_bytes()).decode();assert 'AUTHORITY_CONTROLS_OK routeCases=1028 nativeStates=10' in log and 'REPORT_SCOPE_OK maps=1028 negative=3 checks=5206' in log
 pair=[]
 for mode in ('old','memo'):
  path=q.with_name(label+'.'+mode+'.trace.gz');stats=replay(path);scope_traces[path.name]=stats;views+=stats['observations'];pair.append(path.read_bytes())
  for line in gzip.decompress(path.read_bytes()).decode().splitlines():
   if line.startswith('VIEW '):
    kv=dict(x.split('=',1) for x in line.split()[1:]);view=parseview(kv['view']);entries+=sum(len(v) for v in view.values())
    if kv['reason']=='all-keys-removed':assert view=={}
    if kv['reason']=='new-key-after-clear':assert len(view)==1
 assert pair[0]==pair[1];flag_traces.add(pair[0]);errors=q.with_name(label+'.negative-errors.txt').read_text().splitlines();assert len(errors)==3 and all(x.count('dirty projection mismatch ctx=1')==2 for x in errors)
assert len(scope_traces)==6 and len(flag_traces)==1
scoped['scope']={'traces':6,'views':views,'entries':entries,'trace_replays':scope_traces}
b=json.loads((root/'build-validation.json').read_text());assert subprocess.check_output(['git','-C','../K3-smart-native','rev-parse','HEAD'],text=True).strip()==b['develop_head'];assert not subprocess.check_output(['git','-C','../K3-smart-native','status','--porcelain']);assert all(hashlib.sha256(Path(k).read_bytes()).hexdigest()==v for k,v in b['source_sha256'].items());assert all(hashlib.sha256((Path('../build/tvalue-smart-base')/k).read_bytes()).hexdigest()==v for k,v in b['class_sha256'].items())
assert not subprocess.check_output(['git','diff',b['parent'],'--','kanger/src','kanger-data-dumb/src','kanger-qualification/src','.github','docs/tvalue-authority-stream','docs/tvalue-authority-contexts','docs/tvalue-parent-add'])
s={'parent':b['parent'],'develop_head':b['develop_head'],'develop_tree':b['develop_tree'],'final_JVMs':21,'successful_traces':42,'replayed_authority_views':sum(x['views'] for x in scoped.values()),'serialized_value_entries_checked':sum(x['entries'] for x in scoped.values()),'expected_negative_cases':63,'exact_prior_context_and_parent_add_trace_parity':True,'exact_prior_context_and_parent_add_negative_parity':True,'paired_and_flag_trace_parity':True,'fresh_exact_develop_compilation':True,'compiled_class_count':len(b['class_sha256']),'diagnostic_sources_unchanged':True,'full_oracle_never_disabled':True,'production_changed':False,'SMART_backend_persistence_qualified':False,'native_hook_integration_rerun':False,'complete_inference_corpus_rerun':False,'end_to_end_inference_speedup_qualified':False,'fixtures':scoped}
(root/'summary.json').write_text(json.dumps(s,indent=2)+'\n');print('SMART_BASE_ANALYSIS_OK',s['final_JVMs'],s['successful_traces'],s['replayed_authority_views'],s['serialized_value_entries_checked'],s['expected_negative_cases'])
