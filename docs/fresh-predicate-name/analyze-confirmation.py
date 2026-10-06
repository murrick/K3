from pathlib import Path
import re,statistics,json
root=Path(__file__).parent
rows=[];raw=set();final=set();stats=set()
expected=json.loads((root/"expected.json").read_text())
for i,mode in enumerate(['reference','true','true','reference'],1):
    p=root/('confirm-%d-%s.log'%(i,mode));s=p.read_text()
    samples=re.findall(r'^SAMPLE (\d+) query_ns=(\d+) optimize_ns=(\d+) optimize_cpu_ns=(\d+) optimize_allocated_bytes=(\d+) raw=18 optimized=6 solutions=0 values=0$',s,re.M)
    assert [int(x[0]) for x in samples]==list(range(6)),p
    assert not p.with_suffix('.err').read_text(),p
    for label,target in [('RAW',raw),('OPTIMIZED',final)]:
        lines=re.findall(r'^'+label+r' .*$',s,re.M);assert len(lines)==6,p;target.update(lines)
    lines=re.findall(r'^POST_OPTIMIZE_LAST_LINKER_STATS .*$',s,re.M)
    assert len(lines)==6,p
    stats.update(lines)
    row=tuple(statistics.median(int(x[col]) for x in samples[2:])/div for col,div in [(2,1e9),(3,1e9),(4,1e6)])
    rows.append(row)
    print(p.name,'wall_s=%.3f cpu_s=%.3f allocated_MB=%.1f'%row)
assert raw=={"RAW "+expected["RAW"]}
assert final=={"OPTIMIZED "+expected["OPTIMIZED"]}
assert stats=={"POST_OPTIMIZE_LAST_LINKER_STATS "+expected["POST_OPTIMIZE_LAST_LINKER_STATS"]}
(root/"confirmation-summary.json").write_text(json.dumps({"rows":[{"sequence":i+1,"mode":mode,"wall_s":r[0],"cpu_s":r[1],"allocated_MB":r[2]} for i,(mode,r) in enumerate(zip(["reference","false","true","true","false","reference"],rows))],"snapshots_verified":24},indent=2)+"\n")
for label,off,on in [('ON_vs_clean_forward',0,1),('ON_vs_clean_reverse',3,2)]:
    print(label,'wall_reduction=%.2f%% cpu_reduction=%.2f%% allocation_reduction=%.2f%%'%tuple((1-rows[on][i]/rows[off][i])*100 for i in range(3)))
print('ALL_RAW_AND_FINAL_TEXTS_EQUAL samples=24')
print("LAST_LINKER_STATISTICS_EQUAL samples=24")
