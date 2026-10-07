from pathlib import Path
import ast,base64,gzip,hashlib,json,re,collections,subprocess
root=Path(__file__).parent
assert not subprocess.check_output(['git','diff','6c45ebe16fbdc3a05fd5ff573bebc2f33ffb601d','--','kanger/src','kanger-qualification/src','.github'])

def read(label):
    assert not (root/(label+'.err')).read_bytes(),label
    return gzip.decompress((root/(label+'.log.gz')).read_bytes()).decode()
def native(text):return '\n'.join(row for row in text.splitlines() if not row.startswith('DIRTY_JOURNAL_OK '))+'\n'
def decode(value):return base64.b64decode(value,validate=True).decode()
def digest(value):return hashlib.sha256(value.encode()).hexdigest()
def payload_sources(text,rule_state=False):
    rows=text.split('|') if text else [];sources=[]
    for row in rows:
        match=re.fullmatch(r'(\d+):(.+?;) 0 B'+(':deleted=false:stored=true:query=false' if rule_state else ''),row)
        assert match,row;sources.append(match[2])
    assert len(sources)==len(set(sources));return sorted(sources)

forced=['123','132','213','231','312','321']
captures={};outcome_by_order={};all_inputs=set();frequency=collections.Counter();races=collections.Counter();positive_jvms=0
for build in ('order','shadow'):
    for mode,repeat in [('on',''),('off',''),('verify',''),('on','repeat-')]:
        label=build+'-'+mode+'-'+repeat+'capture'
        log=native(read(label));replay_log=read(label+'-clean-replay');positive_jvms+=2
        rows=(root/(label+'.cases')).read_text().splitlines();events=(root/(label+'.events')).read_text().splitlines()
        assert len(rows)==18 and len(events)==108
        entries=collections.defaultdict(list);exits=collections.defaultdict(list)
        for row in events:
            parts=row.split();kv=dict(f.split('=',1) for f in parts[1:]);case=kv['label'];entry=(int(kv['sequence']),int(kv['actor']))
            if parts[0]=='ORDER_ENTER':assert kv['policy']=='settle';entries[case].append(entry)
            elif parts[0]=='ORDER_EXIT':assert kv['failure']=='none' and kv['accepted'] in ('true','false');exits[case].append((*entry,kv['accepted']=='true'))
            else:raise AssertionError(parts[0])
        actual_log=[];actual_replay=[];details=[]
        for n,row in enumerate(rows):
            f=row.split('\t');assert len(f)==5
            case,order,input_hash,encoded_input,encoded_result=f
            assert case==('forced-' if n<6 else 'race-')+str(n) and sorted(order)==['1','2','3']
            if n<6:assert order==forced[n]
            input_text=decode(encoded_input);result=decode(encoded_result);assert digest(input_text)==input_hash;all_inputs.add(input_text)
            assert entries[case]==list(enumerate(map(int,order),1))
            assert [e[:2] for e in exits[case]]==entries[case]
            winner=1 if order.index('1')<order.index('2') else 2
            outcomes=[winner==1,winner==2,True];assert {e[1]:e[2] for e in exits[case]}==dict(enumerate(outcomes,1))
            state=dict(line.split('=',1) for line in result.splitlines());assert set(state)=={'accepted','rules','before','answer','solutions','values','hypotheses','temporary','after','reservations'}
            assert state['accepted']=='['+','.join(str(v).lower() for v in outcomes)+']'
            expected=sorted('!value(1.0,'+str(i)+'.0,'+str(actor*1000+i)+'.0);' for actor in (winner,3) for i in range(3))
            assert payload_sources(state['solutions'])==expected
            expected_rules=expected+(['?value(1.0,2.0,1002.0);'] if winner==2 else [])
            assert payload_sources(state['before'],True)==sorted(expected_rules) and state['after']==state['before']
            assert state['rules']==str(6 if winner==1 else 7) and state['answer']=='true' and state['reservations']=='0' and not state['hypotheses'] and not state['temporary']
            values=[]
            for value in state['values'].split('|'):
                match=re.fullmatch(r'\{x=\d+:([\d.]+), y=\d+:([\d.]+)\}',value);assert match,value;values.append((float(match[1]),float(match[2])))
            assert sorted(values)==sorted((float(i),float(actor*1000+i)) for actor in (winner,3) for i in range(3))
            if order in outcome_by_order:assert outcome_by_order[order]==result
            outcome_by_order[order]=result;frequency[order]+=1
            if n>=6:races[order]+=1
            h=digest(result);actual_log.append('CAPTURE_CASE label='+case+' order='+order+' result='+h);actual_replay.append('REPLAY_CASE label='+case+' order='+order+' result='+h)
            details.append({'case':case,'order':order,'accepted':outcomes,'rules':int(state['rules']),'input_sha256':input_hash,'result_sha256':h,'clean_replay_exact':True})
        assert log.splitlines()==actual_log+['THREADED_PUBLICATION_CAPTURE_OK cases=18 workers=54 entries=54 exits=54 registrations=0']
        assert replay_log.splitlines()==actual_replay+['CLEAN_PUBLICATION_REPLAY_OK cases=18 full_inputs=true full_results=true reservations=0']
        captures[label]=details
assert len(all_inputs)==1 and set(outcome_by_order)==set(forced)

for mode in ('on','off','verify'):
    log=read('shadow-'+mode+'-TValueDirtyJournalSafetyRunner');assert 'TVALUE_DIRTY_JOURNAL_SAFETY_OK checks=83' in log
    assert log==read('shadow-on-TValueDirtyJournalSafetyRunner')
    log=read('order-'+mode+'-KangerMindCommitExceptionAtomicitySafetyRunner');assert 'MIND_COMMIT_EXCEPTION_ATOMICITY_OK' in log
    positive_jvms+=2
oracle=json.loads(Path('docs/linker-donor-universe/son-expected.json').read_text())
for build in ('order','shadow'):
    log=native(read(build+'-on-ExactCandidateReplayRunner'));positive_jvms+=1
    assert log==gzip.decompress(Path('docs/tvalue-dirty-buckets/clean-on-ExactCandidateReplayRunner.log.gz').read_bytes()).decode()
    candidates={}
    for row in log.splitlines():
        if row.startswith('ROW son '):
            fields=dict(f.split('=',1) for f in row.split()[3:]);source=decode(fields['source']);assert source not in candidates
            candidates[source]={k:{'true':True,'false':False,'null':None}[fields[k]] for k in ('compiled','collision','answer','accepted')}
    assert candidates==oracle
    runner='LatentSolveSyncTransactionRunner';assert native(read(build+'-on-'+runner))==gzip.decompress(Path('docs/tvalue-dirty-buckets/clean-on-'+runner+'.log.gz').read_bytes()).decode();positive_jvms+=1
    for suffix in ('state','work'):assert (root/(build+'-on-'+runner+'.'+suffix)).read_bytes()==Path('docs/tvalue-dirty-buckets/clean-on-'+runner+'.'+suffix).read_bytes()
assert positive_jvms==26

# Use the existing independent projection replay without executing its broader
# qualification. Every delta, full native view and inspection counter is checked.
source=Path('docs/tvalue-dirty-buckets/analyze.py').read_text();tree=ast.parse(source)
for name in ('parseview','parsevalues','checkview','replay'):
    node=next(n for n in tree.body if isinstance(n,ast.FunctionDef) and n.name==name);exec(ast.get_source_segment(source,node))
traces={p.name:replay(p) for p in sorted(root.glob('*.trace.gz'))};assert len(traces)==9
for mode in ('on','off','verify'):
    assert (root/('shadow-'+mode+'-TValueDirtyJournalSafetyRunner.trace.gz')).read_bytes()==Path('docs/tvalue-dirty-buckets/journal-'+mode+'-TValueDirtyJournalSafetyRunner.trace.gz').read_bytes()
for runner in ('ExactCandidateReplayRunner','LatentSolveSyncTransactionRunner'):
    assert (root/('shadow-on-'+runner+'.trace.gz')).read_bytes()==Path('docs/tvalue-dirty-buckets/journal-on-'+runner+'.trace.gz').read_bytes()

tamper=json.loads((root/'tampered-order-result.json').read_text());assert tamper['exit_code']!=0 and tamper['expected_rejection']
assert 'complete native replay outcome forced-0 order=213' in (root/'tampered-order.err').read_text()
original=(root/'order-on-capture.cases').read_text().splitlines();changed=(root/'tampered-order.cases').read_text().splitlines()
assert original[1:]==changed[1:];a=original[0].split('\t');b=changed[0].split('\t');assert a[0]==b[0] and a[2:]==b[2:] and a[1]=='123' and b[1]=='213'
prior=json.loads(Path('docs/tvalue-dirty-buckets/summary.json').read_text());assert prior['frozen_complete_corpus_semantics_equal'] is False and prior['rejected_corpus_attempts']==2
result={'base':'6c45ebe16fbdc3a05fd5ff573bebc2f33ffb601d','production_source_unchanged':True,'positive_jvms':26,'expected_negative_jvms':1,'captures':captures,'threaded_cases':144,'clean_replay_cases':144,'actual_worker_commits':432,'orders_covered':forced,'order_frequency':dict(sorted(frequency.items())),'unforced_race_order_frequency':dict(sorted(races.items())),'all_recorded_inputs_identical':True,'full_native_results_match_clean_actual_order_replay':True,'independent_native_fact_and_values_oracle':True,'journal_traces':traces,'total_replayed_authority_views':sum(t['observations'] for t in traces.values()),'native_safety_checks_per_mode':83,'source_keyed_candidates_per_mode':47,'transaction_states_and_work_equal':True,'tampered_order_rejected':True,'prior_randomized_corpus_gate_unqualified':True,'prior_rejected_corpus_attempts_retained':2,'limits':'prepared operands before worker start; measured publication order, not scheduling transparency or general worker TValue/callback/storage coverage; original overlapping preparation corpus gate remains unqualified'}
(root/'summary.json').write_text(json.dumps(result,indent=2)+'\n');print('PUBLICATION_REPLAY_ANALYSIS_OK',json.dumps({k:v for k,v in result.items() if k not in ('captures','journal_traces')}))
