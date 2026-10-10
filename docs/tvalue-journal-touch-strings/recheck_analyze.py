from pathlib import Path
import csv,gzip,json
from statistics import median
# Re-run the original qualification and reuse its checked replay parser.
exec((Path(__file__).parent/'analyze.py').read_text())
files=sorted(p.glob('recheck-*.result.json'));assert len(files)==12
measurements=[];traces=0
for f in files:
 r=json.loads(f.read_text());label=f.name.removesuffix('.result.json')
 assert r['exit_code']==0 and not (p/(label+'.err')).read_bytes()
 assert b'OBSERVER_COST_OK' in gzip.decompress((p/(label+'.log.gz')).read_bytes())
 old=Path('docs/tvalue-observer-fast-disabled/cost/32-0-on-fast-attached')
 assert (p/(label+'.native.txt')).read_bytes()==Path(str(old)+'.native.txt').read_bytes()
 rows=list(csv.DictReader((p/(label+'.csv')).open()));assert len(rows)==64
 for workload,ops in [('setter',8192),('export',16)]:
  samples=[x for x in rows if x['workload']==workload]
  assert [int(x['iteration']) for x in samples]==list(range(32))
  assert all(int(x['operations'])==ops and int(x['wall_ns'])>0 and int(x['allocated_bytes'])>=0 for x in samples)
  trace=p/(label+'.'+workload+'.trace.gz');replay(trace)
  assert trace.read_bytes()==Path(str(old)+'.'+workload+'.trace.gz').read_bytes();traces+=1
  measurements.append({'repetition':r['repetition'],'module':r['module'],'workload':workload,'ns':median(int(x['wall_ns'])/ops for x in samples),'bytes':median(int(x['allocated_bytes'])/ops for x in samples)})
metrics=[]
for workload in ('setter','export'):
 ratios=[];savings=[]
 for rep in range(6):
  pair={x['module']:x for x in measurements if x['repetition']==rep and x['workload']==workload}
  ratios.append(pair['candidate']['ns']/pair['baseline']['ns']);savings.append(pair['baseline']['bytes']-pair['candidate']['bytes'])
 metrics.append({'size':32,'workload':workload,'paired_candidate_baseline_ratio':median(ratios),'ratios':ratios,'paired_saved_bytes':median(savings),'savings':savings})
result={'JVMs':12,'measured_batches':768,'identical_trace_replays':traces,'metrics':metrics,'per_JVM':measurements}
(p/'recheck-summary.json').write_text(json.dumps(result,indent=2)+'\n');print(json.dumps({'JVMs':12,'metrics':metrics},indent=2))
