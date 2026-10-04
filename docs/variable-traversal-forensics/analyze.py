from pathlib import Path
import json,re,subprocess
root=Path(__file__).parent
raw=set();final=set();warm=[]
for run in [1,2]:
    p=root/('profile-%d.log'%run);s=p.read_text()
    assert not p.with_suffix('.err').read_text()
    routes={i:{} for i in range(6)};stamps={i:{} for i in range(6)}
    for sample,name,counts,unique in re.findall(r'^VARIABLE_ROUTE (\d+) (\S+) (\[.*\]) unique_lists=(\d+)$',s,re.M):
        routes[int(sample)][name]=(json.loads(counts),int(unique))
    for sample,name,counts in re.findall(r'^STAMP_ROUTE (\d+) (\S+) (\[.*\])$',s,re.M):
        stamps[int(sample)][name]=json.loads(counts)
    outside={int(i):int(n) for i,n in re.findall(r'^VARIABLE_OUTSIDE_DELETION (\d+) (\d+)$',s,re.M)}
    assert set(outside)==set(range(6))
    for label,target in [('RAW',raw),('OPTIMIZED',final)]:
        lines=re.findall(r'^'+label+r' .*$',s,re.M);assert len(lines)==6;target.update(lines)
    for i in range(6):
        total=sum(c[4] for c,u in routes[i].values())+outside[i]
        assert total==17573710,(run,i,total)
        assert all(c[0]==c[1]+c[2] for c,u in routes[i].values())
        if i>=2:warm.append((routes[i],outside[i],stamps[i]))
    print('PROFILE_COMPLETE',run)
assert len(raw)==len(final)==1
reference=subprocess.check_output(['git','show','e5a6f6608df967491dfb86295e6542cc84c7e8bf:docs/active-mind-reuse-eight/reuse-1-reference.log'],text=True)
assert raw==set(re.findall(r'^RAW .*$',reference,re.M))
assert final==set(re.findall(r'^OPTIMIZED .*$',reference,re.M))
names=sorted(warm[0][0])
for name in names:
    rows=[r[name] for r,o,s in warm]
    assert len({c[4] for c,u in rows})==len({u for c,u in rows})==1
    print(name,'calls_min=%d calls_max=%d deletion_calls=%d share=%.4f%% unique_lists=%d result_max=%d empty_min=%d empty_max=%d'%(
        min(c[0] for c,u in rows),max(c[0] for c,u in rows),rows[0][0][4],100*rows[0][0][4]/17573710,rows[0][1],max(c[6] for c,u in rows),min(c[7] for c,u in rows),max(c[7] for c,u in rows)))
assert len({o for r,o,s in warm})==1
print('outside_getTVariables deletion_calls=%d share=%.4f%%'%(warm[0][1],100*warm[0][1]/17573710))
for name in sorted(warm[0][2]):
    rows=[s[name] for r,o,s in warm]
    assert len({tuple(c) for c in rows})==1
    c=rows[0]
    print('STAMP',name,'entries=%d comparisons=%d avg_comparisons_per_entry=%.6f'%(c[0],c[1],c[1]/c[0]))
print('VARIABLE_TRAVERSAL_FORENSICS_OK samples=12 warm_samples=8 texts_equal_to_clean_base=true')
print('TIMING_EXCLUDED diagnostic_instrumentation=true main_optimization_thread_only=true')
