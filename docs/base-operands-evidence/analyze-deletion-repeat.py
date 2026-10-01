import json,re,statistics
from pathlib import Path
rows=[];snapshots=[]
for i,mode in enumerate(['false','true','true','false','false','true'],1):
    p=Path('docs/base-operands-evidence')/('deletion-repeat-%d-%s.log'%(i,mode))
    source=p.read_text();samples=[int(x) for x in re.findall(r'optimize_ns=(\d+)',source)]
    assert len(samples)==4,p
    rows.append({'order':i,'mode':mode,'first_s':samples[0]/1e9,'warm_median_s':statistics.median(samples[1:])/1e9,'warm_s':[x/1e9 for x in samples[1:]]})
    raw=[x for x in source.splitlines() if x.startswith('RAW ')]
    final=[x for x in source.splitlines() if x.startswith('OPTIMIZED ')]
    states=re.findall(r'raw=18 optimized=6 solutions=0 values=0',source)
    assert len(raw)==len(final)==len(states)==4,p
    assert not p.with_suffix('.err').read_text(),p
    snapshots+=list(zip(raw,final))
assert len(snapshots)==24 and len(set(snapshots))==1
reductions=[100*(1-rows[on]['warm_median_s']/rows[off]['warm_median_s']) for off,on in [(0,1),(3,2),(4,5)]]
result={'jvms':rows,'paired_warm_reduction_percent':reductions,'hypothesis_snapshots_equal':24}
print(json.dumps(result,indent=2))
Path('docs/base-operands-evidence/deletion-repeat-summary.json').write_text(json.dumps(result,indent=2)+'\n')
