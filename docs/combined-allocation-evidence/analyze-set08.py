from pathlib import Path
import re,statistics
rows=[]
for p in sorted(Path(__file__).parent.glob('set08-*.log')):
    s=p.read_text()
    a=[float(x) for x in re.findall(r'^set_08_02\s+([0-9.]+) sec$',s,re.M)]
    assert len(a)==8 and 'SET08_PROFILE_OK samples=8' in s,p
    assert len(re.findall(r'^SET08_SAMPLE_OK ',s,re.M))==8,p
    assert not p.with_suffix('.err').read_text(),p
    median=statistics.median(a[2:]);rows.append(median)
    print(p.name,'samples='+str(a),'warm_median_s=%.3f'%median)
assert len(rows)==4
for off,on in [(0,1),(3,2)]: print('PAIR wall_reduction=%.2f%%'%((1-rows[on]/rows[off])*100))
print('SET08_ALL_PASS samples=32')
