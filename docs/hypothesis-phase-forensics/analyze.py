from pathlib import Path
import gzip,hashlib,json,re,statistics,subprocess

root=Path(__file__).parent
expected=json.loads((root/'expected.json').read_text())
observations=[]
for label in ['clean','profile-1','profile-2']:
    text=gzip.decompress((root/(label+'.log.gz')).read_bytes()).decode()
    assert not (root/(label+'.err')).read_bytes()
    assert re.findall(r'^SAMPLE (\d+) .* raw=18 optimized=6 solutions=0 values=0$',text,re.M)==list(map(str,range(6)))
    for key,value in expected.items():assert re.findall(r'^'+key+r' (.*)$',text,re.M)==[value]*6
    if label=='clean':continue
    def pairs(prefix):return re.findall(r'^'+prefix+r' (\d+) ([\w/]+) (\[.*\])$',text,re.M)
    phase_rows=pairs('HYPOTHESIS_PHASE');core_rows=pairs('HYPOTHESIS_CORE');pass_rows=pairs('HYPOTHESIS_PASS')
    events=re.findall(r'^HYPOTHESIS_EVENT (\d+) ([\w.]+) (\d+)$',text,re.M)
    totals=re.findall(r'^HYPOTHESIS_TOTAL (\d+) (\[.*\])$',text,re.M)
    candidates=re.findall(r'^HYPOTHESIS_CANDIDATE (\d+) (\d+) compiled=(true|false) collision=(true|false) replayed=(true|false) answer=(null|true|false) accepted=(true|false) source=(.*)$',text,re.M)
    candidate_phases=re.findall(r'^HYPOTHESIS_CANDIDATE_PHASE (\d+) (\d+) ([\w/]+) (\[.*\])$',text,re.M)
    assert [int(n) for n,_ in totals]==list(range(6))
    for n,values in totals:
        total=json.loads(values);assert all(v>0 for v in total)
        phase={k:json.loads(v) for sample,k,v in phase_rows if sample==n}
        core={k:json.loads(v) for sample,k,v in core_rows if sample==n}
        passes={k:json.loads(v) for sample,k,v in pass_rows if sample==n}
        event={k:int(v) for sample,k,v in events if sample==n}
        outcomes=[]
        for sample,index,compiled,collision,replayed,answer,accepted,source in candidates:
            if sample!=n:continue
            timing={k:json.loads(v) for s,i,k,v in candidate_phases if s==n and i==index}
            outcome={'index':int(index),'compiled':compiled=='true','collision':collision=='true','replayed':replayed=='true','answer':None if answer=='null' else answer=='true','accepted':accepted=='true','source':source,'phases':timing}
            assert outcome['accepted']==(outcome['compiled'] and not outcome['collision'] and outcome['replayed'] and outcome['answer'] is not None)
            assert outcome['replayed']==(outcome['compiled'] and not outcome['collision'])
            outcomes.append(outcome)
        assert [r['index'] for r in outcomes]==list(range(event['candidate_count']))
        assert event['root_before']==18 and event['root_final']==6
        assert event['candidate_count']==event['root_after_expansion']==len(outcomes)
        assert event['unique_candidate_source']==len({r['source'] for r in outcomes})
        assert event.get('duplicate_candidate_source',0)==len(outcomes)-event['unique_candidate_source']
        assert sum(r['accepted'] for r in outcomes)==event['accepted']==6
        assert sum(r['compiled'] for r in outcomes)==event['compile_rule']
        assert sum(r['collision'] for r in outcomes)==event.get('collision_true',0)
        assert phase['candidate_create'][0]==phase['candidate_render_compile'][0]==phase['candidate_release'][0]==len(outcomes)
        assert phase['candidate_link'][0]==phase['candidate_analyze'][0]==event['compile_rule']
        assert phase['candidate_replay'][0]==sum(r['replayed'] for r in outcomes)
        for key,metrics in phase.items():
            assert len(metrics)==4 and all(v>=0 for v in metrics)
            if '/' not in key and key.startswith('candidate_'):
                assert metrics==[sum(r['phases'].get(key,[0]*4)[i] for r in outcomes) for i in range(4)]
        primary={k:v for k,v in phase.items() if '/' not in k}
        remainder=[total[i]-sum(v[i+1] for v in primary.values()) for i in range(3)]
        assert all(v>=0 for v in remainder),remainder
        for parent in ['expanded_query','candidate_replay']:
            children=[v for k,v in phase.items() if k.startswith(parent+'/')]
            assert all(sum(v[i] for v in children)<=phase[parent][i] for i in range(1,4))
        for key,metrics in passes.items():assert sum(metrics)==phase[key][0]
        observations.append({'jvm':label,'sample':int(n),'total_wall_cpu_allocation':total,'events':event,'phases':phase,'core_calls':core,'pass_answers':passes,'remainder_wall_cpu_allocation':remainder,'candidates':outcomes})
def semantic(row):
    return {'events':row['events'],'phase_calls':{k:v[0] for k,v in row['phases'].items()},'core_calls':row['core_calls'],'pass_answers':row['pass_answers'],'candidates':sorted([{k:v for k,v in r.items() if k not in ['index','phases']} for r in row['candidates']],key=lambda r:r['source'])}
for n in range(6):
    a,b=[r for r in observations if r['sample']==n]
    assert semantic(a)==semantic(b),n
warm=[r for r in observations if r['sample']>=2]
assert all(semantic(r)==semantic(warm[0]) for r in warm)
performance={}
for label in ['profile-1','profile-2']:
    rows=[r for r in warm if r['jvm']==label]
    cpu=sum(r['total_wall_cpu_allocation'][1] for r in rows)
    allocation=sum(r['total_wall_cpu_allocation'][2] for r in rows)
    performance[label]={'mean_total': [statistics.mean(r['total_wall_cpu_allocation'][i] for r in rows) for i in range(3)],'primary_phases':{k:{'calls':rows[0]['phases'][k][0],'mean_wall_cpu_allocation':[statistics.mean(r['phases'][k][i] for r in rows) for i in range(1,4)],'cpu_pct':sum(r['phases'][k][2] for r in rows)/cpu*100,'allocation_pct':sum(r['phases'][k][3] for r in rows)/allocation*100} for k in rows[0]['phases'] if '/' not in k},'query_passes':{k:{'calls':rows[0]['phases'][k][0],'cpu_pct':sum(r['phases'][k][2] for r in rows)/cpu*100} for k in rows[0]['phases'] if '/' in k},'remainder_cpu_pct':sum(r['remainder_wall_cpu_allocation'][1] for r in rows)/cpu*100}
summary={'base':'1c943d02d39bd33ebd91fabb7c4190112ab49459','snapshots_verified':18,'diagnostic_snapshots':12,'warm_diagnostic_samples':8,'counts_and_outcomes_equal_between_jvms':True,'phase_timer_scope':'main-thread coarse instrumentation; primary phases disjoint, query pass intervals nested; no A/B speedup measurement','metrics':['calls','wall_ns','main_thread_cpu_ns','main_thread_allocated_bytes'],'core_columns':['child_constructor','compile_line','link','analyze','release'],'warm_census':semantic(warm[0]),'warm_performance':performance,'observations':observations}
(root/'summary.json').write_text(json.dumps(summary,indent=2)+'\n')
clean=json.loads((root/'reference-class-sha256.json').read_text());diagnostic=json.loads((root/'classes-class-sha256.json').read_text())
changed=sorted(k for k,v in clean.items() if k in diagnostic and diagnostic[k]!=v)
assert changed and all(k.startswith('org/kanger/Mind') or k=='org/kanger/stores/HypothesisStore.class' for k in changed),changed
(root/'class-differences.json').write_text(json.dumps(changed,indent=2)+'\n')
assert not subprocess.check_output(['git','diff',summary['base'],'--','kanger/src','kanger-udf/src','kanger-qualification/src','.github'])
print(json.dumps({'snapshots_verified':18,'warm_census':summary['warm_census'],'warm_performance':performance,'changed_shared_classes':changed},indent=2))
