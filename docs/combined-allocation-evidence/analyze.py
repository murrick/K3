from pathlib import Path
import re,statistics
rows=[];raw=set();final=set()
for p in sorted(Path(__file__).parent.glob('combined-*.log')):
    s=p.read_text()
    samples=re.findall(r'SAMPLE (\d+) query_ns=(\d+) optimize_ns=(\d+) optimize_cpu_ns=(\d+) optimize_allocated_bytes=(\d+) raw=18 optimized=6 solutions=0 values=0',s)
    assert len(samples)==6,p
    assert not p.with_suffix('.err').read_text(),p
    raw.update(re.findall(r'^RAW .*$',s,re.M));final.update(re.findall(r'^OPTIMIZED .*$',s,re.M))
    row=tuple(statistics.median(int(x[i]) for x in samples[2:])/div for i,div in [(2,1e9),(3,1e9),(4,1e6)])
    rows.append(row)
    print(p.name,'wall_s=%.3f cpu_s=%.3f allocated_MB=%.1f'%row)
assert len(rows)==8 and len(raw)==len(final)==1
for base,indices in [(0,[1,2,3]),(7,[6,5,4])]:
    for i in indices:
        print('VS_REFERENCE batch=%d mode=%s wall_reduction=%.2f%% cpu_reduction=%.2f%% allocation_reduction=%.2f%%'%((1 if base==0 else 2),{1:'10',2:'01',3:'11',4:'11',5:'01',6:'10'}[i],*[(1-rows[i][j]/rows[base][j])*100 for j in range(3)]))
print('COMBINED_SNAPSHOTS_EQUAL samples=48')
