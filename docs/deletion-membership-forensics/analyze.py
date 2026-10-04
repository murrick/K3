from pathlib import Path
import re, json, subprocess
root=Path(__file__).parent
raw=set();final=set();warm=[]
for run in [1,2]:
    p=root/('profile-%d.log'%run);s=p.read_text()
    assert not p.with_suffix('.err').read_text()
    counts={int(i):json.loads(a) for i,a in re.findall(r'^DELETION_COUNTS (\d+) (\[.*\])$',s,re.M)}
    depths={int(i):json.loads(a) for i,a in re.findall(r'^DELETION_DEPTHS (\d+) (\[.*\])$',s,re.M)}
    assert set(counts)==set(depths)==set(range(6))
    for label,target in [('RAW',raw),('OPTIMIZED',final)]:
        lines=re.findall(r'^'+label+r' .*$',s,re.M);assert len(lines)==6;target.update(lines)
    for i in range(6):
        c=counts[i];h=depths[i]
        assert sum(h)==c[0]
        assert c[8]+c[9]+c[14]==c[0]
        assert c[4]+c[6]==c[1]
        assert c[5]+c[7]==c[1]-c[8]
        assert c[10]+c[11]==c[6]+c[7]
        if i>=2:
            warm.append((c,h))
            print('WARM run=%d sample=%d calls=%d levels=%d avg_depth=%.6f restored_contains=%d deleted_contains=%d repeated_builtin_box_opportunities=%d'%(run,i,c[0],c[1],c[1]/c[0],c[6],c[7],c[13]))
    print('PROFILE_COMPLETE',run)
assert len(raw)==len(final)==1
reference=subprocess.check_output(['git','show','e5a6f6608df967491dfb86295e6542cc84c7e8bf:docs/active-mind-reuse-eight/reuse-1-reference.log'],text=True)
assert raw==set(re.findall(r'^RAW .*$',reference,re.M))
assert final==set(re.findall(r'^OPTIMIZED .*$',reference,re.M))
assert len({tuple(c) for c,h in warm})==len({tuple(h) for c,h in warm})==1
c,h=warm[0]
for name,numerator,denominator in [('empty_restored_maps',c[2],c[1]),('empty_deleted_maps',c[3],c[1]-c[8]),('absent_restored_sets',c[4],c[1]),('absent_deleted_sets',c[5],c[1]-c[8])]:
    print(name,'count=%d share=%.6f%%'%(numerator,100*numerator/denominator))
for percentile in [50,95,99,100]:
    cumulative=0
    for depth,n in enumerate(h):
        cumulative+=n
        if cumulative*100>=percentile*c[0]:
            print('depth_p%d=%d'%(percentile,depth));break
print('DELETION_MEMBERSHIP_FORENSICS_OK samples=12 warm_samples=8 texts_equal_to_clean_base=true')
print('TIMING_EXCLUDED diagnostic_instrumentation=true main_optimization_thread_only=true')
