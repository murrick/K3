from pathlib import Path
import json,gzip,re,base64,collections
root=Path(__file__).parent
oracle=json.loads((root/'son-expected.json').read_text());expected=json.loads((root/'expected.json').read_text())
def read(mode,flag,runner):
    label=f'{mode}-{flag}-{runner}'
    assert not (root/(label+'.err')).read_bytes(),label
    return gzip.decompress((root/(label+'.log.gz')).read_bytes()).decode()
for flag in ('on','off'):
    for runner in ('KangerLinkerRuleOrderingSafetyRunner','KangerLinkerCheckpointBalanceSafetyRunner','KangerCompletedHypothesisContractRunner','FrontierDependencyWitness','LatentSolveSyncTransactionRunner'):
        assert read('reference',flag,runner)==read('split',flag,runner),(flag,runner)
    ref=read('reference',flag,'KangerLinkerDonorScopeSafetyRunner');split=read('split',flag,'KangerLinkerDonorScopeSafetyRunner')
    shared=[r for r in split.splitlines() if 'name=full ' in r or 'name=receiver-limited ' in r]
    assert shared==[r for r in ref.splitlines() if r.startswith('DONOR_SCOPE_CASE ')]
    assert 'checks=44' in ref and 'checks=88' in split
    for direction in ('descending','ascending'):
        assert f'name=receiver-retained order={direction} execution=1 donors=3 candidate_donors=2 alpha=true beta=true used=true' in split
        assert f'name=empty-retained order={direction} execution=0 donors=3 candidate_donors=2 alpha=false beta=false used=false' in split
    for mode in ('reference','split'):
        text=read(mode,flag,'ExactCandidateReplayRunner');rows={};small={}
        for line in text.splitlines():
            if line.startswith('ROW '):
                fields=line.split();label=fields[1];kv=dict(f.split('=',1) for f in fields[3:]);source=base64.b64decode(kv['source']).decode()
                row={k:{'true':True,'false':False,'null':None}[kv[k]] for k in ('compiled','collision','answer','accepted')}
                if label=='son':assert source not in rows;rows[source]=row
                else:small.setdefault(label,[]).append(row['answer'])
        assert rows==oracle and small=={'small':[True,False,None],'external-item':[True,False,None],'external-other':[None,None,None]}
        assert len(re.findall(r'^CASE_OK .* reservations=0$',text,re.M))==4 and 'EXACT_CANDIDATE_REPLAY_OK' in text
    for suffix in ('state','work'):
        name='LatentSolveSyncTransactionRunner.'+suffix
        assert (root/('reference-'+flag+'-'+name)).read_bytes()==(root/('split-'+flag+'-'+name)).read_bytes()
states=[(root/(m+'-'+f+'-LatentSolveSyncTransactionRunner.state')).read_bytes() for m in ('reference','split') for f in ('on','off')]
assert all(s==states[0] for s in states)
def corpus_semantics(text):
    rows=[r for r in text.splitlines() if not re.search(r'Timing|timing|\t[\d.]+ sec$',r)]
    # Historical set_08_01 starts three concurrent producers. Keep all resulting
    # payloads and multiplicities, but compare their delivery order as a multiset.
    for name,final in [('set_08_01',39),('set_08_02',494),('set_08_03',6)]:
        a=rows.index('Testing: '+name)
        b=next(i for i in range(a+1,len(rows)) if rows[i].startswith('Testing: '))
        block=rows[a:b];events=[];stable=[];payload=[]
        assert 'PROCESSES STOP: '+str(final) in block
        for row in block:
            if re.match(r'PROCESS [1-4] ',row):
                events.append(re.sub(r'/\d+$','/total',row));continue
            if re.match(r'\t(Solution|Row) \d+: ',row):
                payload.append(re.sub(r'\t(Solution|Row) \d+: ',r'\t\1: ',row));continue
            if payload:stable.extend(sorted(payload));payload=[]
            stable.append(row)
        if payload:stable.extend(sorted(payload))
        rows[a:b]=stable+sorted(events)
    return '\n'.join(rows)
corpora=[]
for mode in ('reference','split'):
    corpus=read(mode,'on','LatentSubstitutionCorpusRunner');assert 'LATENT_CORPUS_PASS' in corpus
    assert not re.search(r'Fails:\s*[1-9]',corpus)
    corpora.append(corpus_semantics(corpus))
    concurrency=read(mode,'on','KangerRuleCandidateConcurrencyRunner');assert 'RULE_CANDIDATE_CONCURRENCY_OK iterations=3' in concurrency
assert corpora[0]==corpora[1]
traces={};snapshots=0
for mode,runner in [('reference','SonProfileRunner'),('split','SonProfileRunner'),('reference-trace','ScopeTraceSonRunner'),('split-trace','ScopeTraceSonRunner')]:
    text=read(mode,'on',runner)
    assert re.findall(r'^SAMPLE (\d+) .* raw=18 optimized=6 solutions=0 values=0$',text,re.M)==['0','1','2']
    for k,v in expected.items():assert re.findall(r'^'+k+r' (.*)$',text,re.M)==[v]*3
    snapshots+=3
    if mode.endswith('-trace'):traces[mode]=[r for r in text.splitlines() if r.startswith('TRACE_')]
assert traces['reference-trace']==traces['split-trace']
samples={};current=None
for row in traces['split-trace']:
    if row.startswith('TRACE_BEGIN '):
        kv=dict(f.split('=') for f in row.split()[1:]);current=int(kv['sample']);samples[current]={'links':int(kv['links']),'headers':[],'groups':[]}
    elif row.startswith('TRACE_LINK '):samples[current]['headers'].append(row)
    elif row.startswith('TRACE_SCOPE '):
        kv=dict(f.split('=',1) for f in row.split()[1:]);e=json.loads(kv['execution']);d=json.loads(kv['donors'])
        assert e==d and e==sorted(e,reverse=kv['order']=='descending')
        samples[current]['groups'].append((int(kv['link']),int(kv['pass']),kv['order'],e))
    elif row.startswith('TRACE_END '):assert int(row.split('=')[1])==current;current=None
assert set(samples)=={0,1,2}
for sample in samples.values():
    assert sample['links']==len(sample['headers'])
    ids=[int(re.search(r'id=(\d+)',r)[1]) for r in sample['headers']];assert ids==list(range(1,sample['links']+1))
    pairs=collections.defaultdict(list)
    for link,p,d,e in sample['groups']:pairs[(link,p)].append((d,e))
    for key,pair in pairs.items():assert len(pair)==2 and pair[0][0]=='descending' and pair[1][0]=='ascending' and pair[0][1]==list(reversed(pair[1][1]))
    for link in ids:
        passes=sorted(p for l,p in pairs if l==link);assert passes==list(range(1,max(passes)+1))
    sample['rule_visits']=sum(len(e) for _,_,_,e in sample['groups']);sample['pass_order_groups']=len(sample['groups'])
    del sample['headers'];del sample['groups']
result={'baseline_classes_identical':651,'candidate_sources_per_mode':47,'candidate_modes':4,'scope_checks_reference':44,'scope_checks_split':88,'native_witness_checks_per_mode':31,'transaction_operations_per_mode':20,'semantic_snapshots':snapshots,'exact_native_order_traces_equal':True,'traced_samples':samples,'corpora_semantics_equal':True,'stderr_all_successful_runs_empty':True,'truncated_attempt':'failed-attempts/split-corpus-truncated.log.gz; exit 0 without completion marker, not accepted; fresh retry passed; cause unresolved','timing':'no speedup claim; this is a role separation only'}
(root/'summary.json').write_text(json.dumps(result,indent=2)+'\n')
print('DONOR_ANALYSIS_OK',json.dumps(result))
