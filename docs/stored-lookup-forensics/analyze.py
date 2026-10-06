from pathlib import Path
import gzip
import hashlib
import json
import re

root = Path(__file__).parent
expected = json.loads((root/'expected.json').read_text())
source = (root/'StoredLookupProfile.java').read_text()
columns = re.findall(r'"([a-z_]+)"', re.search(r'COLUMNS = \{(.*?)\};',source,re.S).group(1))
assert len(columns)==27
(root/'columns.json').write_text(json.dumps(columns,indent=2)+'\n')
observations=[]
for jvm in [1,2]:
    text=gzip.decompress((root/('profile-%d.log.gz'%jvm)).read_bytes()).decode()
    assert re.findall(r'^SAMPLE (\d+) .* raw=18 optimized=6 solutions=0 values=0$',text,re.M)==list(map(str,range(6)))
    for label,value in expected.items():
        assert re.findall(r'^'+label+r' (.*)$',text,re.M)==[value]*6
    globals_=re.findall(r'^STORED_GLOBAL (\d+) (\[.*\])$',text,re.M)
    sites=re.findall(r'^STORED_SITE (\d+) (\d+) (\[.*\])$',text,re.M)
    lengths=re.findall(r'^STORED_HIST (\d+) (\d+) (\d+) (\d+)$',text,re.M)
    pairs=re.findall(r'^STORED_PAIR (\d+) (\d+) (\d+) (\[.*\])$',text,re.M)
    assert [int(n) for n,_ in globals_]==list(range(6))
    assert [(int(n),int(site)) for n,site,_ in sites]==[(n,site) for n in range(6) for site in range(7)]
    for n,global_ in globals_:
        rows={site:json.loads(value) for sample,site,value in sites if sample==n}
        hist={site:{int(length):int(count) for sample,s,length,count in lengths if sample==n and s==site} for site in rows}
        transitions={a+'->'+b:json.loads(value) for sample,a,b,value in pairs if sample==n}
        for site,c in rows.items():
            assert len(c)==len(columns)
            assert c[0]==c[1]+c[2]+c[3]
            assert c[3]==c[7]==c[13]==c[23]==c[24]==0
            assert c[0]==c[5]==c[6]==c[21]
            assert c[8]==c[9]+c[10]==c[22]
            assert c[6]==c[11]+c[12]+c[13]
            assert c[9]==c[11]
            assert c[16]==c[17]+c[18]
            assert c[18]==c[19]+c[20]
            assert sum(hist[site].values())==c[6]
            assert sum(length*count for length,count in hist[site].items())==c[8]
            assert hist[site].get(0,0)==c[14]
        global_values=json.loads(global_)
        assert global_values[1]<=global_values[0]
        assert global_values[2]+sum(c[16] for c in rows.values())==sum(c[26] for c in rows.values())
        assert sum(v[0] for v in transitions.values())==sum(c[17] for c in rows.values())
        assert sum(v[1] for v in transitions.values())==sum(c[18] for c in rows.values())
        observations.append({'jvm':jvm,'sample':int(n),'global':global_values,'sites':rows,'histograms':hist,'pairs':transitions})
for n in range(6):
    left,right=[row for row in observations if row['sample']==n]
    assert {k:v for k,v in left.items() if k!='jvm'}=={k:v for k,v in right.items() if k!='jvm'}
warm=[row for row in observations if row['sample']>=2]
totals=[]
for row in warm:
    sites=row['sites']
    totals.append({'calls':sum(c[0] for c in sites.values()),'visits':sum(c[8] for c in sites.values()),
                   'repeat_calls':sum(c[16] for c in sites.values()),'repeat_visits':sum(sites[str(n)][8] for n in range(2,7)),
                   'empty_lookups':sum(c[14] for c in sites.values()),'changed_repeats':sum(c[18] for c in sites.values()),
                   'max_scan':max(max(hist) for hist in row['histograms'].values())})
range_={key:[min(row[key] for row in totals),max(row[key] for row in totals)] for key in totals[0]}
summary={'base':'1c943d02d39bd33ebd91fabb7c4190112ab49459','snapshots_verified':12,'warm_samples':8,
         'scope':'main-thread optimizeHypothesis only; instrumented timing excluded',
         'columns':columns,'counts_equal_between_jvms_at_each_sample':True,'warm_ranges':range_,
         'representative':warm[0],'observations':observations}
(root/'summary.json').write_text(json.dumps(summary,indent=2)+'\n')
clean=Path('../build/stored-lookup-reference');diagnostic=Path('../build/stored-lookup-classes')
changed=sorted(str(p.relative_to(clean)) for p in clean.rglob('*.class') if (diagnostic/p.relative_to(clean)).exists() and p.read_bytes()!=(diagnostic/p.relative_to(clean)).read_bytes())
assert changed==['org/kanger/Linker.class','org/kanger/factory/RuleFactory$1.class','org/kanger/factory/RuleFactory.class','org/kanger/units/Domain.class'],changed
(root/'class-differences.json').write_text(json.dumps(changed,indent=2)+'\n')
witness={mode:hashlib.sha256((folder/'org/kanger/StoredLookupWitness.class').read_bytes()).hexdigest() for mode,folder in [('clean',clean),('diagnostic',diagnostic)]}
assert len(set(witness.values()))==1
(root/'witness-class-sha256.json').write_text(json.dumps(witness,indent=2)+'\n')
print(json.dumps({'snapshots_verified':12,'counts_match_at_each_sample':True,'warm_ranges':range_},indent=2))
