from pathlib import Path
import re,statistics
rows=[]
for p in sorted(Path(__file__).parent.glob('set08-factorial-*.log')):
    s=p.read_text()
    samples=[float(x) for x in re.findall(r'^set_08_02\s+([0-9.]+) sec$',s,re.M)]
    assert len(samples)==12 and 'SET08_PROFILE_OK samples=12' in s,p
    assert len(re.findall(r'^SET08_SAMPLE_OK ',s,re.M))==12,p
    assert len(re.findall(r'^Success: 1$',s,re.M))==12,p
    assert len(re.findall(r'^  Fails: 0$',s,re.M))==12,p
    assert not p.with_suffix('.err').read_text(),p
    med=statistics.median(samples[6:]);rows.append(med)
    print(p.name,'samples='+str(samples),'warm_median_s=%.4f'%med)
assert len(rows)==8
for base,indices in [(0,[1,2,3]),(7,[6,5,4])]:
    for i in indices:
        print('VS_REFERENCE batch=%d mode=%s wall_reduction=%.2f%%'%((1 if base==0 else 2),{1:'10',2:'01',3:'11',4:'11',5:'01',6:'10'}[i],(1-rows[i]/rows[base])*100))
print('SET08_FACTORIAL_ALL_PASS samples=96')
