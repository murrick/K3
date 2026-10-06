from pathlib import Path
import gzip, hashlib, json, re, subprocess

root=Path(__file__).parent
expected=json.loads((root/'expected.json').read_text())
source=(root/'ResolvedCandidateProfile.java').read_text()
columns=re.findall(r'"([a-z_]+)"',re.search(r'COLUMNS = \{(.*?)\};',source,re.S).group(1))
assert len(columns)==34
(root/'columns.json').write_text(json.dumps(columns,indent=2)+'\n')
observations=[]
for label in ['clean','profile-1','profile-2']:
    text=gzip.decompress((root/(label+'.log.gz')).read_bytes()).decode()
    assert not (root/(label+'.err')).read_bytes()
    assert re.findall(r'^SAMPLE (\d+) .* raw=18 optimized=6 solutions=0 values=0$',text,re.M)==list(map(str,range(6)))
    for key,value in expected.items():assert re.findall(r'^'+key+r' (.*)$',text,re.M)==[value]*6
    if label=='clean':continue
    counts=re.findall(r'^RESOLVED_COUNTS (\d+) (\[.*\])$',text,re.M)
    identities=re.findall(r'^RESOLVED_IDENTITIES (\d+) final_rules=(\d+) batch_rules=(\d+) sources=(\d+)$',text,re.M)
    lengths=re.findall(r'^RESOLVED_LENGTHS (\d+) ([\d,]+) (\d+)$',text,re.M)
    depths=re.findall(r'^RESOLVED_DEPTH (\d+) (\d+) (\d+)$',text,re.M)
    bindings=re.findall(r'^RESOLVED_BINDINGS (\d+) (\d+) (\[.*\])$',text,re.M)
    assert [int(n) for n,_ in counts]==list(range(6))
    assert len(identities)==6 and len(bindings)==12
    for n,values in counts:
        c=json.loads(values);assert len(c)==34
        identity={k:int(v) for k,v in zip(['final_rules','batch_rules','sources'],next(x[1:] for x in identities if x[0]==n))}
        hist={k:int(v) for sample,k,v in lengths if sample==n}
        depth={k:int(v) for sample,k,v in depths if sample==n}
        binding={k:json.loads(v) for sample,k,v in bindings if sample==n}
        assert c[0]==c[1]+c[2]+c[3]
        assert c[3]==c[28] and c[29]==c[30]==0
        assert c[4]==c[5]==c[6]+c[7]+c[8]
        assert c[8]==c[12]
        assert c[17]==c[18]+c[19]
        assert c[5]==c[26]+identity['final_rules']
        assert c[20]==c[21]+c[27]+identity['batch_rules']
        assert c[31]==c[11]
        assert sum(hist.values())==c[3]
        assert sum(int(k.split(',')[1])*v for k,v in hist.items())==c[4]
        assert sum(int(k.split(',')[2])*v for k,v in hist.items())==c[8]
        assert sum(int(k.split(',')[3])*v for k,v in hist.items())==c[11]
        assert sum(depth.values())==c[14]
        assert [sum(binding[k][i] for k in binding) for i in range(3)]==c[23:26]
        observations.append({'jvm':label,'sample':int(n),'counts':dict(zip(columns,c)),'identities':identity,'length_histogram':hist,'collect_depths':depth,'bindings_by_read_stage':binding})
for n in range(6):
    a,b=[row for row in observations if row['sample']==n]
    assert {k:v for k,v in a.items() if k!='jvm'}=={k:v for k,v in b.items() if k!='jvm'},n
warm=[row for row in observations if row['sample']>=2]
ranges={k:[min(row['counts'][k] for row in warm),max(row['counts'][k] for row in warm)] for k in columns}
summary={'base':'1c943d02d39bd33ebd91fabb7c4190112ab49459','snapshots_verified':18,'diagnostic_snapshots':12,'warm_diagnostic_samples':8,'counts_match_between_jvms_at_each_sample':True,'instrumented_timing_excluded':True,'columns':columns,'warm_ranges':ranges,'representative':warm[0],'observations':observations}
(root/'summary.json').write_text(json.dumps(summary,indent=2)+'\n')
clean=json.loads((root/'reference-class-sha256.json').read_text());diagnostic=json.loads((root/'classes-class-sha256.json').read_text())
changed=sorted(k for k,v in clean.items() if k in diagnostic and diagnostic[k]!=v)
assert changed==['org/kanger/Linker$1.class','org/kanger/Linker$2.class','org/kanger/Linker$3.class','org/kanger/Linker$4.class','org/kanger/Linker.class','org/kanger/factory/RuleCandidateIndex.class','org/kanger/factory/RuleFactory$1.class','org/kanger/factory/RuleFactory.class','org/kanger/units/Domain.class','org/kanger/units/Rule.class','org/kanger/units/TVariable.class'],changed
(root/'class-differences.json').write_text(json.dumps(changed,indent=2)+'\n')
assert not subprocess.check_output(['git','diff',summary['base'],'--','kanger/src','kanger-udf/src','kanger-qualification/src','.github'])
print(json.dumps({'snapshots_verified':18,'warm_ranges':ranges,'representative':warm[0]},indent=2))
