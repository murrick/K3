from pathlib import Path
import re, statistics
rows=[]
raw=set(); optimized=set()
for p in sorted(Path(__file__).parent.glob("candidate-snapshot-*.log")):
    s=p.read_text()
    samples=re.findall(r"SAMPLE (\d+) query_ns=(\d+) optimize_ns=(\d+) optimize_allocated_bytes=(\d+) raw=18 optimized=6 solutions=0 values=0",s)
    assert len(samples)==4,p
    assert not p.with_suffix(".err").read_text(),p
    raw.update(re.findall(r"^RAW .*$",s,re.M)); optimized.update(re.findall(r"^OPTIMIZED .*$",s,re.M))
    t=statistics.median(int(x[2]) for x in samples[1:])/1e9
    a=statistics.median(int(x[3]) for x in samples[1:])/1e6
    rows.append((t,a))
    print(p.name, "seconds=%.3f MB=%.1f"%(t,a))
assert len(rows)==6
assert len(raw)==len(optimized)==1
for off,on in [(0,1),(3,2),(4,5)]:
    print("PAIR time_reduction=%.2f%% allocation_reduction=%.2f%%"%((1-rows[on][0]/rows[off][0])*100,(1-rows[on][1]/rows[off][1])*100))
print("BENCHMARK_SNAPSHOTS_EQUAL samples=24")
