from pathlib import Path
import re,statistics
root=Path(__file__).parent
rows=[];raw=set();final=set();stats=set()
for i,mode in enumerate(['reference','false','true','true','false','reference'],1):
    p=root/('binding-%d-%s.log'%(i,mode));s=p.read_text()
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
assert len(raw)==len(final)==len(stats)==1
for label,off,on in [('ON_vs_clean_forward',0,2),('ON_vs_clean_reverse',5,3),('ON_vs_OFF_forward',1,2),('ON_vs_OFF_reverse',4,3),('OFF_vs_clean_forward',0,1),('OFF_vs_clean_reverse',5,4)]:
    print(label,'wall_reduction=%.2f%% cpu_reduction=%.2f%% allocation_reduction=%.2f%%'%tuple((1-rows[on][i]/rows[off][i])*100 for i in range(3)))
print('ALL_RAW_AND_FINAL_TEXTS_EQUAL samples=36')
print("LAST_LINKER_STATISTICS_EQUAL samples=36")
