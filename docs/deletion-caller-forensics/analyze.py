from pathlib import Path
import gzip, json, re

root = Path(__file__).parent
expected = json.loads((root/'expected.json').read_text())
previous = json.loads((root/'previous-totals.json').read_text())
warm = []
for run in [1,2]:
    s = gzip.decompress((root/('profile-'+str(run)+'.log.gz')).read_bytes()).decode()
    assert not (root/('profile-'+str(run)+'.err')).read_text()
    assert re.findall(r'^SAMPLE (\d+) ',s,re.M) == list(map(str,range(6)))
    for key,value in expected.items():
        assert re.findall(r'^'+key+r' (.*)',s,re.M) == [value]*6
    groups = re.findall(r'^DELETION_CALLER sample=(\d+) role=(\w+) phase=(\w+) type=(\w+) totals=(\[.*?\]) depths=(\[.*?\]) never_exposed=(\[.*?\])$',s,re.M)
    edges = re.findall(r'^DELETION_EDGES sample=(\d+) role=(\w+) phase=(\w+) values=(\[.*?\])$',s,re.M)
    collectors = re.findall(r'^DELETION_COLLECTORS sample=(\d+) values=(\[.*?\])$',s,re.M)
    assert len(collectors)==6
    exposure=re.findall(r'^DELETION_EXPOSURE sample=(\d+) minds=(\d+) getters=(\d+)$',s,re.M)
    assert len(exposure)==6
    for sample in range(6):
        rows=[]; aggregate={}
        for n,role,phase,type,t,d,unexposed in groups:
            if int(n)!=sample:continue
            t,d,u=json.loads(t),json.loads(d),json.loads(unexposed)
            assert len(u)==2 and 0<=u[0]<=t[0] and 0<=u[1]<=t[1]
            assert len(t)==8 and len(d)==64 and not d[0] and not d[-1]
            assert t[0]==sum(d)==t[5]+t[6]+t[7]
            assert t[1]==sum(i*v for i,v in enumerate(d)) and t[3]==t[1]-t[5]
            assert t[2]<=t[1] and t[4]<=t[3] and t[5]<=t[2] and t[6]<=t[4]
            rows.append(dict(role=role,phase=phase,type=type,totals=t,depths={i:v for i,v in enumerate(d) if v},never_exposed=u))
            if type not in aggregate:aggregate[type]=dict(totals=[0]*8,depths={})
            a=aggregate[type]
            a['totals']=[x+y for x,y in zip(a['totals'],t)]
            for i,v in enumerate(d):
                if v:a['depths'][str(i)]=a['depths'].get(str(i),0)+v
        if sample>=2:
            assert aggregate==previous, (run,sample,'aggregate changed')
        edge_rows=[]
        for n,role,phase,value in edges:
            if int(n)!=sample:continue
            v=json.loads(value);assert len(v)==4 and v[0]>=v[1]>=v[2]>=v[3]>=0
            edge_rows.append(dict(role=role,phase=phase,values=v))
        collect=json.loads(collectors[sample][1]);assert len(collect)==3 and collect[0]==0
        if sample>=2:
            for edge in edge_rows:
                if edge['phase'] not in ['FIRST','SECOND']:continue
                calls=sum(row['totals'][0] for row in rows if row['role']==edge['role'] and row['phase']==edge['phase'] and row['type']=='TVARIABLE')
                assert edge['values']==[calls]*4
        if sample>=2:warm.append(dict(run=run,sample=sample,groups=rows,edges=edge_rows,collectors=collect,exposure=dict(minds=int(exposure[sample][1]),getters=int(exposure[sample][2]))))
assert len(warm)==8
# Empty collector counts can vary as the workspace evolves; deletion rows must agree.
assert all(row['groups']==warm[0]['groups'] and row['edges']==warm[0]['edges'] for row in warm)
assert 'EMPTY_DELETION_BOUNDARIES_OK checks=27' in (root/'witness.log').read_text()
assert 'REPEATED_DELETION_WITNESS_OK scenarios=2 checks=8' in (root/'repeated-witness.log').read_text()
assert not (root/'witness.err').read_text() and not (root/'repeated-witness.err').read_text()
assert 'EXPOSED_SET_DELETION_WITNESS_OK scenarios=2 checks=11' in (root/'exposed-set-witness.log').read_text()
assert not (root/'exposed-set-witness.err').read_text()
summary=dict(base='1c943d02d39bd33ebd91fabb7c4190112ab49459',snapshots_verified=12,identical_warm_deletion_groups=8,previous_totals_reconciled=True,warm_rows=warm)
(root/'summary.json').write_text(json.dumps(summary,indent=2)+'\n')
for role in ['BINDING','STAMP','OTHER']:
    for phase in ['FIRST','SECOND','OUTSIDE']:
        rows=[row for row in warm[0]['groups'] if row['role']==role and row['phase']==phase]
        if not rows:continue
        print(role,phase,'calls',sum(row['totals'][0] for row in rows),'levels',sum(row['totals'][1] for row in rows),'types',[(row['type'],row['totals'][0]) for row in rows],'never_exposed_calls',sum(row['never_exposed'][0] for row in rows),'never_exposed_levels',sum(row['never_exposed'][1] for row in rows))
for row in warm[0]['edges']:print('ARGUMENT_CLASSES',row['role'],row['phase'],row['values'])
for i,role in enumerate(['OTHER','BINDING','STAMP']):
    values=[row['collectors'][i] for row in warm]
    print('SCOPED_COLLECTOR_ENTRIES',role,'range',[min(values),max(values)])
