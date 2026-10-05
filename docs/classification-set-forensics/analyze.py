from pathlib import Path
import json,re
root=Path(__file__).parent;expected=json.loads((root/'expected.json').read_text());groups=['excluded','calculated','candidates','assumed','stored'];warm=[]
for n in [1,2]:
 s=(root/f'profile-{n}.log').read_text()
 assert re.findall(r'^SAMPLE (\d+) ',s,re.M)==list(map(str,range(6)))
 assert not (root/f'profile-{n}.err').read_text()
 for key,value in expected.items():assert re.findall(r'^'+key+r' (.*)',s,re.M)==[value]*6
 matches=re.findall(r'^CLASSIFICATION sample=(\d+) group=(\w+) totals=(\[.*?\]) peaks=(\[.*?\]) classified=(\[.*?\]) finals=(\[.*?\])$',s,re.M);assert len(matches)==30
 for sample,group,t,p,c,f in matches:
  assert group in groups;t,p,c,f=map(json.loads,[t,p,c,f]);assert t[0]==t[7]==sum(p)==sum(c)==sum(f)
  assert t[1]>=t[2] and t[4]>=t[5] and p[-1]==0
  if int(sample)>=2:warm.append({'run':n,'sample':int(sample),'group':group,'created':t[0],'add_attempts':t[1],'successful_adds':t[2],'clears':t[3],'contains':t[4],'contains_empty':t[5],'iterations':t[6],'never_populated':p[0],'peaks':{i:v for i,v in enumerate(p) if v},'classified':{i:v for i,v in enumerate(c) if v},'finals':{i:v for i,v in enumerate(f) if v}})
assert len(warm)==40
# All warm repetitions reconcile; verify exact per-group counters and histograms.
for group in groups:
 rows=[{k:v for k,v in row.items() if k not in ['run','sample']} for row in warm if row['group']==group]
 assert all(row==rows[0] for row in rows),group
 print(json.dumps(rows[0]))
never=sum(row['never_populated'] for row in warm[:5]);created=sum(row['created'] for row in warm[:5]);print('NEVER_POPULATED',never,'OF',created,'PERCENT',round(100*never/created,2))
(root/'summary.json').write_text(json.dumps({'warm_rows':warm,'snapshots_verified':12,'warm_counters_identical':8,'never_populated_per_optimization':never,'created_per_optimization':created},indent=2)+'\n')
