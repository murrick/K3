from pathlib import Path
import re,json,hashlib
root=Path('docs/warm-profile')
reference=Path('../K3-lazy-binding38/docs/lazy-binding-variables/binding-1-reference.log').read_text()
raw0=re.findall(r'^RAW (.*)',reference,re.M)[0];opt0=re.findall(r'^OPTIMIZED (.*)',reference,re.M)[0]
summary=[]
for n in [2,3]:
    log=(root/f'profile-{n}.log').read_text()
    assert re.findall(r'^SAMPLE (\d+) ',log,re.M)==list(map(str,range(6)))
    assert re.findall(r'^RAW (.*)',log,re.M)==[raw0]*6
    assert re.findall(r'^OPTIMIZED (.*)',log,re.M)==[opt0]*6
    assert re.findall(r'^POST_OPTIMIZE_LAST_LINKER_STATS (.*)',log,re.M)==['passes=6 rules=1292 rotations=6824 pairs=13281 unifications=3080']*6
    assert len(re.findall(r'raw=18 optimized=6 solutions=0 values=0',log))==6
    assert not (root/f'profile-{n}.err').read_text()
    text=(root/f'profile-{n}.tsv').read_text()
    rows={}
    for line in text.splitlines():
        part=line.split('\t',2)
        if len(part)==3 and part[0]!='WINDOW': rows.setdefault(part[0],{})[part[2]]=int(part[1])
    total=dict((k,int(v)) for k,v in re.findall(r'(\w+)=(\d+)',next(l for l in text.splitlines() if l.startswith('TOTAL'))))
    first=rows['CPU_FIRST_KANGER'];alloc=rows['ALLOC_SITE_WEIGHT'];cpu=total['cpu_samples'];weight=total['sampled_allocation_weight']
    def pct(value,denom):return round(100*value/denom,2)
    points={}
    for name in ['org.kanger.Mind.isUnitDeleted','org.kanger.units.TVariable.activeMind','org.kanger.units.Predicate.getName','org.kanger.factory.TValueFactory.isEmpty']:
        count=sum(v for k,v in first.items() if k.startswith(name+':'));points[name]={'samples':count,'share_pct':pct(count,cpu)}
    for name in ['org.kanger.primitives.ArgumentsList.getTVariables','org.kanger.primitives.ArgumentsList.getStamp','org.kanger.units.Predicate.getName','org.kanger.units.TVariable.setMind']:
        count=sum(v for k,v in alloc.items() if (' @ '+name+':') in k);points[name+'_allocation']={'weight':count,'share_pct':pct(count,weight)}
    summary.append({'run':n,'totals':total,'points':points})
(root/'summary.json').write_text(json.dumps({'snapshots_match':12,'linker_statistics_match':12,'runs':summary},indent=2)+'\n')
print(json.dumps(summary,indent=2))
