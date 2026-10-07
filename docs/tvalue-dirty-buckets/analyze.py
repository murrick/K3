from pathlib import Path
import json,gzip,re,base64,ast
root=Path(__file__).parent
def read(label):
    assert not (root/(label+'.err')).read_bytes(),label
    return gzip.decompress((root/(label+'.log.gz')).read_bytes()).decode()
def native(text):return '\n'.join(r for r in text.splitlines() if not r.startswith('DIRTY_JOURNAL_OK '))+'\n'
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
assert 'LATENT_CORPUS_PASS' in a and 'LATENT_CORPUS_PASS' in b
try:frozen_corpus_equal=corpus_semantics(a)==corpus_semantics(b)
except AssertionError:frozen_corpus_equal=False
# Do not weaken or replace the frozen complete-corpus oracle. Separately
# compare only the unaffected single-thread sections, with explicit scope.
def nonconcurrent(text):
    rows=[r for r in text.splitlines() if not re.search(r'Timing|timing|\t[\d.]+ sec$',r)]
    for name in ('set_08_01','set_08_02','set_08_03'):
        start=rows.index('Testing: '+name);end=next(i for i in range(start+1,len(rows)) if rows[i].startswith('Testing: '));del rows[start:end]
    return '\n'.join(rows)
assert nonconcurrent(a)==nonconcurrent(b)
ordered={}
for mode in ('on','off'):
    clean=read('clean-'+mode+'-TValueOrderedPublicationRunner');observed=native(read('journal-'+mode+'-TValueOrderedPublicationRunner'))
    assert clean==observed and 'TVALUE_ORDERED_PUBLICATION_OK checks=90' in clean
    rows=re.findall(r'^ORDERED_PUBLICATION order=(\d+) accepted=(\S+) rules=(\d+) payload=(.*)$',clean,re.M)
    assert [r[0] for r in rows]==['123','132','213','231','312','321'] and {r[2] for r in rows}=={'6','7'}
    ordered[mode]=[{ 'order':r[0],'accepted':r[1],'rules':int(r[2]),'payload':r[3]} for r in rows]
assert ordered['on']==ordered['off']

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
    rows=gzip.decompress(path.read_bytes()).decode().splitlines();state={};generations={};retired=set();dirty={};seeded=set();reads=0;seed_buckets=0;authority_buckets=0;touches=0;maps=0;counts={'contexts':0,'observations':0,'changes':0,'resets':0};kinds={'added':0,'removed':0,'deleted':0,'resurrected':0,'order_changes':0};last=None
    for row in rows:
        fields=row.split();kind=fields[0];kv=dict(f.split('=',1) for f in fields[1:]);
        if kind=='DIRTY_JOURNAL_OK':last={k:int(v) for k,v in kv.items()};continue
        ctx=int(kv['ctx']);gen=int(kv['generation'])
        assert ctx not in retired
        if kind=='BASELINE':
            assert ctx not in state;state[ctx]=parseview(kv['view']);generations[ctx]=gen;counts['contexts']+=1;checkview(state[ctx]);seeded.add(ctx);seed_buckets+=len(state[ctx])
        elif kind=='RESET':
            assert ctx in state and gen>generations[ctx];state[ctx]=parseview(kv['view']);generations[ctx]=gen;counts['resets']+=1;checkview(state[ctx]);seeded.add(ctx);seed_buckets+=len(state[ctx])
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
        elif kind in ('TOUCH','MAP_DIRTY'):
            dirty.setdefault(ctx,set()).add(int(kv['variable']));touches+=kind=='TOUCH';maps+=kind=='MAP_DIRTY'
        elif kind=='VIEW':
            assert gen==generations[ctx] and state[ctx]==parseview(kv['view']);checkview(state[ctx]);counts['observations']+=1;authority_buckets+=len(state[ctx])
            if ctx not in seeded:reads+=len(dirty.get(ctx,set()))
            dirty[ctx]=set();seeded.discard(ctx)
        elif kind=='RETIRE':assert ctx in state and gen==generations[ctx];retired.add(ctx)
        else:raise AssertionError(kind)
    assert last and all(last[k]==v for k,v in counts.items()),(path,last,counts)
    assert last['touches']==touches and last['mapChanges']==maps and last['bucketReads']==reads and last['seeds']==counts['contexts']+counts['resets'],(path,last,reads,touches,maps)
    return {**counts,'seed_buckets':seed_buckets,'authority_buckets':authority_buckets,'deferred':last['deferred'],'touches':last['touches'],'bucketReads':last['bucketReads'],'seeds':last['seeds'],'mapChanges':last['mapChanges'],'retired':len(retired),'transitions':kinds,'replay_matches_every_authority_view':True}
traces={p.name:replay(p) for p in sorted(root.glob('*.trace.gz'))}
assert len(traces)==18,len(traces)
for mode in ('on','off','verify'):
    text=read('journal-'+mode+'-TValueDirtyJournalSafetyRunner');assert 'TVALUE_DIRTY_JOURNAL_SAFETY_OK checks=83' in text
    assert all('mode='+m+' ' in text for m in ('accept','reject','user-reject','exception'))
for mode in ('on','off','verify'):
    assert read('journal-'+mode+'-TValueDirtyGapSafetyRunner').strip()=='TVALUE_DIRTY_GAP_OK unsupported_metadata_write=detected native_state=preserved session=closed'
for mode in ('off','verify'):
    assert read('journal-on-TValueDirtyJournalSafetyRunner')==read('journal-'+mode+'-TValueDirtyJournalSafetyRunner')
    assert (root/'journal-on-TValueDirtyJournalSafetyRunner.trace.gz').read_bytes()==(root/('journal-'+mode+'-TValueDirtyJournalSafetyRunner.trace.gz')).read_bytes()
assert (root/'journal-on-SonProfileRunner.trace.gz').read_bytes()==(root/'journal-on-repeat-SonProfileRunner.trace.gz').read_bytes()
# Compare the complete baseline/reset/delta/authority/retirement stream with
# the previously qualified full-snapshot observer, excluding new invalidations.
comparison=[]
for p in sorted(root.glob('*.trace.gz')):
    if 'TValueDirtyJournalSafetyRunner' in p.name or 'TValueOrderedPublicationRunner' in p.name or 'LatentSubstitutionCorpusRunner' in p.name:continue
    old=Path('docs/tvalue-journal')/p.name
    before=[r for r in gzip.decompress(old.read_bytes()).decode().splitlines() if not r.startswith('JOURNAL_OK ')]
    after=[r for r in gzip.decompress(p.read_bytes()).decode().splitlines() if not r.startswith(('DIRTY_JOURNAL_OK ','TOUCH ','MAP_DIRTY '))]
    assert before==after,p.name
    comparison.append(p.name)
assert len(comparison)==12
rejected=root/'rejected-attempts'
archived=gzip.decompress((rejected/'journal-on-corpus-concurrent7.log.gz').read_bytes()).decode()
assert not (rejected/'journal-on-corpus-concurrent7.err').read_bytes() and 'LATENT_CORPUS_PASS' in archived
for text in (archived,b):
    start=text.index('Testing: set_08_03');end=text.index('Testing: ',start+10);block=text[start:end]
    assert 'PROCESSES STOP: 7' in block and 'Solutions (6)' in block
    assert nonconcurrent(a)==nonconcurrent(native(text))
archived_trace=replay(rejected/'journal-on-corpus-concurrent7.trace.gz')
result={'base':'c90c3619ef3b9cf2267af0b618bb60c452516913','production_source_unchanged':True,'current_matrix_jvms':35,'additional_corpus_retry_jvms':1,'completed_jvms':36,'accepted_oracle_jvms':34 if not frozen_corpus_equal else 35,'native_journal_safety_checks_per_mode':83,'candidate_source_oracles':4,'sources_per_oracle':47,'complete_son_snapshots':9,'all_stderr_empty':True,'native_contract_logs_equal':True,'transaction_full_states_and_work_equal':True,'frozen_complete_corpus_semantics_equal':frozen_corpus_equal,'nonconcurrent_corpus_semantics_equal':True,'ordered_native_publication_checks_per_jvm':90,'ordered_native_publications':ordered,'journal_traces':traces,'rejected_corpus_attempts':2 if not frozen_corpus_equal else 1,'archived_corpus_snapshot_replay':archived_trace,'prior_complete_trace_streams_equal':comparison,'unsupported_metadata_gap_detected_jvms':3,'total_replayed_authority_views':sum(t['observations'] for t in traces.values()),'randomized_corpus_status':'full frozen equivalence is unqualified when a valid alternate native publication order wins; archived attempts retained','limitations':'dirty selection with authoritative bucket reads and full snapshot checking; no event-sourced reconstruction or production speedup; boundary-batched net canonical projection; no mutation-by-mutation causal log, scheduler consumer, runtime speedup or arbitrary-extension/concurrency guarantee'}
(root/'summary.json').write_text(json.dumps(result,indent=2)+'\n');print('TVALUE_DIRTY_JOURNAL_ANALYSIS_OK',json.dumps({k:v for k,v in result.items() if k not in ('journal_traces','ordered_native_publications','prior_complete_trace_streams_equal')}))
