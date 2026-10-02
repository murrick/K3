from pathlib import Path
import re,statistics
rows=[];raw=set();final=set()
for p in sorted(Path(__file__).parent.glob("active-mind-reuse-*.log")):
    s=p.read_text()
    samples=re.findall(r"SAMPLE (\d+) query_ns=(\d+) optimize_ns=(\d+) optimize_cpu_ns=(\d+) optimize_allocated_bytes=(\d+) raw=18 optimized=6 solutions=0 values=0",s)
    assert len(samples)==6,p
    assert not p.with_suffix(".err").read_text(),p
    raw.update(re.findall(r"^RAW .*$",s,re.M));final.update(re.findall(r"^OPTIMIZED .*$",s,re.M))
    warm=samples[2:]
    row=tuple(statistics.median(int(x[i]) for x in warm)/div for i,div in [(2,1e9),(3,1e9),(4,1e6)])
    rows.append(row)
    print(p.name,"wall_s=%.3f cpu_s=%.3f allocated_MB=%.1f"%row)
assert len(rows)==4 and len(raw)==len(final)==1
for off,on in [(1,0),(2,3)]:
    print("PAIR wall_reduction=%.2f%% cpu_reduction=%.2f%% allocation_reduction=%.2f%%"%tuple((1-rows[on][i]/rows[off][i])*100 for i in range(3)))
print("REPEAT_SNAPSHOTS_EQUAL samples=24")
