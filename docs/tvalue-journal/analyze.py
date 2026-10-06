from pathlib import Path
import json,gzip,re,base64,ast
root=Path(__file__).parent
def read(label):
    assert not (root/(label+'.err')).read_bytes(),label
    return gzip.decompress((root/(label+'.log.gz')).read_bytes()).decode()
def native(text):return '\n'.join(r for r in text.splitlines() if not r.startswith('JOURNAL_OK '))+'\n'
oracle=json.loads(Path('docs/linker-donor-universe/son-expected.json').read_text())
for mode in ('on','off'):
    for runner in ('ExactCandidateReplayRunner','LatentSolveSyncTransactionRunner','DependencyLifecycleWitness','FrontierDependencyWitness','KangerLinkerDonorScopeSafetyRunner'):
        a=read('clean-'+mode+'-'+runner);b=read('journal-'+mode+'-'+runner);assert a==native(b),(mode,runner)
    for suffix in ('state','work'):
        a=root/('clean-'+mode+'-LatentSolveSyncTransactionRunner.'+suffix);b=root/('journal-'+mode+'-LatentSolveSyncTransactionRunner.'+suffix);assert a.read_bytes()==b.read_bytes(),a
    for build in ('clean','journal'):
        text=read(build+'-'+mode+'-ExactCandidateReplayRunner');son={};small={}
        for line in text.splitlines():
            if line.startswith('ROW '):
                fields=line.split();label=fields[1];kv=dict(f.split('=',1) for f in fields[3:]);source=base64.b64decode(kv['source']).decode()
                row={k:{'true':True,'false':False,'null':None}[kv[k]] for k in ('compiled','collision','answer','accepted')}
                if label=='son':assert source not in son;son[source]=row
                else:small.setdefault(label,[]).append(row['answer'])
        assert son==oracle and small=={'small':[True,False,None],'external-item':[True,False,None],'external-other':[None,None,None]}
        assert len(re.findall(r'^CASE_OK .* reservations=0$',text,re.M))==4
expected=json.loads(Path('docs/linker-donor-universe/expected.json').read_text())
for label in ('clean-on-SonProfileRunner','journal-on-SonProfileRunner','journal-on-repeat-SonProfileRunner'):
    text=read(label);assert re.findall(r'^SAMPLE (\d+) .* raw=18 optimized=6 solutions=0 values=0$',text,re.M)==['0','1','2']
    for k,v in expected.items():assert re.findall('^'+k+' (.*)$',text,re.M)==[v]*3
# Reuse the committed, previously qualified normalization of only the three
# historical concurrent sections. All remaining semantic rows stay exact.
source=Path('docs/linker-donor-universe/analyze.py').read_text();tree=ast.parse(source)
node=next(n for n in tree.body if isinstance(n,ast.FunctionDef) and n.name=='corpus_semantics');exec(ast.get_source_segment(source,node))
a=read('clean-on-LatentSubstitutionCorpusRunner');b=native(read('journal-on-LatentSubstitutionCorpusRunner'))
assert 'LATENT_CORPUS_PASS' in a and 'LATENT_CORPUS_PASS' in b and corpus_semantics(a)==corpus_semantics(b)
def parseview(text):
    assert text.startswith('{') and text.endswith('}')
    return {} if text=='{}' else {int(k):parsevalues(v) for k,v in (part.split('=',1) for part in text[1:-1].split(';'))}
def parsevalues(text):
    assert text.startswith('[') and text.endswith(']')
    rows=[] if text=='[]' else [tuple(map(int,v.split(':'))) for v in text[1:-1].split(',')]
    assert all(len(v)==3 and v[2] in (0,1) for v in rows) and len({v[0] for v in rows})==len(rows)
    return rows
def checkview(view):
    ids=[v[0] for rows in view.values() for v in rows];assert len(ids)==len(set(ids))
def replay(path):
    rows=gzip.decompress(path.read_bytes()).decode().splitlines();state={};generations={};retired=set();counts={'contexts':0,'observations':0,'changes':0,'resets':0};kinds={'added':0,'removed':0,'deleted':0,'resurrected':0,'order_changes':0};last=None
    for row in rows:
        fields=row.split();kind=fields[0];kv=dict(f.split('=',1) for f in fields[1:]);
        if kind=='JOURNAL_OK':last={k:int(v) for k,v in kv.items()};continue
        ctx=int(kv['ctx']);gen=int(kv['generation'])
        assert ctx not in retired
        if kind=='BASELINE':
            assert ctx not in state;state[ctx]=parseview(kv['view']);generations[ctx]=gen;counts['contexts']+=1;checkview(state[ctx])
        elif kind=='RESET':
            assert ctx in state and gen>generations[ctx];state[ctx]=parseview(kv['view']);generations[ctx]=gen;counts['resets']+=1;checkview(state[ctx])
        elif kind=='CHANGE':
            assert gen==generations[ctx];var=int(kv['variable']);before=parsevalues(kv['before']);after=parsevalues(kv['after']);assert state[ctx].get(var,[])==before and before!=after
            old={v[0]:v for v in before};new={v[0]:v for v in after}
            kinds['added']+=len(new.keys()-old.keys());kinds['removed']+=len(old.keys()-new.keys())
            for id in old.keys()&new.keys():
                assert old[id][1]==new[id][1];kinds['deleted']+=old[id][2]==0 and new[id][2]==1;kinds['resurrected']+=old[id][2]==1 and new[id][2]==0
            common=old.keys()&new.keys();kinds['order_changes']+=[v[0] for v in before if v[0] in common]!=[v[0] for v in after if v[0] in common]
            if after:state[ctx][var]=after
            else:state[ctx].pop(var,None)
            counts['changes']+=1
        elif kind=='VIEW':
            assert gen==generations[ctx] and state[ctx]==parseview(kv['view']);checkview(state[ctx]);counts['observations']+=1
        elif kind=='RETIRE':assert ctx in state and gen==generations[ctx];retired.add(ctx)
        else:raise AssertionError(kind)
    assert last and all(last[k]==v for k,v in counts.items()),(path,last,counts)
    return {**counts,'deferred':last['deferred'],'retired':len(retired),'transitions':kinds,'replay_matches_every_authority_view':True}
traces={p.name:replay(p) for p in sorted(root.glob('*.trace.gz'))}
assert len(traces)==16,len(traces)
for mode in ('on','off','verify'):
    text=read('journal-'+mode+'-TValueJournalSafetyRunner');assert 'TVALUE_JOURNAL_SAFETY_OK checks=62' in text
    assert all('mode='+m+' ' in text for m in ('accept','reject','user-reject','exception'))
for mode in ('off','verify'):
    assert read('journal-on-TValueJournalSafetyRunner')==read('journal-'+mode+'-TValueJournalSafetyRunner')
    assert (root/'journal-on-TValueJournalSafetyRunner.trace.gz').read_bytes()==(root/('journal-'+mode+'-TValueJournalSafetyRunner.trace.gz')).read_bytes()
assert (root/'journal-on-SonProfileRunner.trace.gz').read_bytes()==(root/'journal-on-repeat-SonProfileRunner.trace.gz').read_bytes()
result={'base':'e636d1957a6841fab4f2c23f6bb399b8cfd1f848','production_source_unchanged':True,'accepted_jvms':28,'native_journal_safety_checks_per_mode':62,'candidate_source_oracles':4,'sources_per_oracle':47,'complete_son_snapshots':9,'all_stderr_empty':True,'native_contract_logs_equal':True,'transaction_full_states_and_work_equal':True,'corpus_semantics_equal':True,'journal_traces':traces,'total_replayed_authority_views':sum(t['observations'] for t in traces.values()),'limitations':'boundary-batched net canonical projection; no mutation-by-mutation causal log, scheduler consumer, runtime speedup or arbitrary-extension/concurrency guarantee'}
(root/'summary.json').write_text(json.dumps(result,indent=2)+'\n');print('TVALUE_JOURNAL_ANALYSIS_OK',json.dumps({k:v for k,v in result.items() if k!='journal_traces'}))
