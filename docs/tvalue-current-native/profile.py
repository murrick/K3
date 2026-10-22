from pathlib import Path
from collections import Counter,defaultdict
from bisect import bisect_right
from datetime import datetime
from decimal import Decimal
from statistics import median
import csv,json,re,subprocess,tempfile,hashlib,gzip
p=Path(__file__).parent;jfr=Path('/Library/Java/JavaVirtualMachines/temurin-21.jdk/Contents/Home/bin/jfr')
def timestamp(value):
 m=re.fullmatch(r'(.*T\d{2}:\d{2}:\d{2})(?:\.(\d+))?(Z|[+-]\d{2}:\d{2})',value);assert m,value;return int(datetime.fromisoformat(m[1]+m[3].replace('Z','+00:00')).timestamp())*10**9+int((m[2] or '').ljust(9,'0'))
def duration(value):
 m=re.fullmatch(r'PT([0-9.]+)S',value);assert m,value;return int(Decimal(m[1])*10**9)
def method(frame):
 m=frame['method'];return m['type']['name'].replace('/','.')+'.'+m['name']
def top(counter):return [{'method':name,'value':value} for name,value in counter.most_common(25)]
results=[]
for file in sorted(p.glob('*-profile.result.json')):
 label=file.name.removesuffix('.result.json');run=json.loads(file.read_text())
 events=json.loads(gzip.decompress((p/(label+'.events.json.gz')).read_bytes()))['recording']['events']
 assert set(e['type'] for e in events)<={'org.kanger.ResidentQuery','jdk.ExecutionSample','jdk.ObjectAllocationSample'}
 qrows={(int(q['iteration']),int(q['query'])):q for q in csv.DictReader((p/(label+'.queries.csv')).open())};intervals=defaultdict(list);seen=set()
 for e in events:
  if e['type']!='org.kanger.ResidentQuery':continue
  v=e['values'];key=(v['iteration'],v['query']);assert key not in seen;seen.add(key);start=timestamp(v['startTime']);elapsed=duration(v['duration']);assert elapsed>=int(qrows[key]['wall_ns']);intervals[v['eventThread']['javaThreadId']].append((start,start+elapsed,*key))
 assert seen==set(qrows) and len(seen)==3584
 for rows in intervals.values():rows.sort();assert all(rows[i][1]<=rows[i+1][0] for i in range(len(rows)-1))
 starts={k:[x[0] for x in rows] for k,rows in intervals.items()};groups={};excluded=Counter();total=Counter()
 for e in events:
  if e['type']=='org.kanger.ResidentQuery':continue
  total[e['type']]+=1;v=e['values'];thread=(v.get('sampledThread') or v.get('eventThread') or {}).get('javaThreadId');stamp=timestamp(v['startTime']);i=bisect_right(starts.get(thread,[]),stamp)-1
  if i<0 or not intervals[thread][i][0]<=stamp<intervals[thread][i][1]:excluded['outside_query']+=1;continue
  _,_,iteration,query=intervals[thread][i];stack=v.get('stackTrace') or {};methods=[method(f) for f in stack.get('frames',[])]
  if 'org.kanger.Mind.query' not in methods:excluded['without_Mind_query_stack']+=1;continue
  key=('early_0_127' if iteration<128 else 'late_128_511')+'/'+('variable' if query>=5 else 'ground')
  if key not in groups:groups[key]={'cpu_samples':0,'allocation_samples':0,'allocation_sample_weight':0,'cpu_leaf':Counter(),'cpu_nearest_kanger':Counter(),'cpu_inclusive_kanger':Counter(),'allocation_nearest_kanger_weight':Counter(),'per_query_cpu_samples':Counter(),'truncated_samples':0}
  g=groups[key];engine=[m for m in methods if m.startswith('org.kanger.') and not m.startswith('org.kanger.ResidentCurrentRunner')];g['truncated_samples']+=bool(stack.get('truncated'))
  if e['type']=='jdk.ExecutionSample':
   g['cpu_samples']+=1;g['per_query_cpu_samples'][str(query)]+=1
   if methods:g['cpu_leaf'][methods[0]]+=1
   if engine:g['cpu_nearest_kanger'][engine[0]]+=1
   for m in set(engine):g['cpu_inclusive_kanger'][m]+=1
  else:
   assert e['type']=='jdk.ObjectAllocationSample';weight=int(v['weight']);g['allocation_samples']+=1;g['allocation_sample_weight']+=weight
   if engine:g['allocation_nearest_kanger_weight'][engine[0]]+=weight
 for g in groups.values():
  for key,value in list(g.items()):
   if isinstance(value,Counter):g[key]=dict(value) if key=='per_query_cpu_samples' else top(value)
 exact={}
 for phase,a,b in [('early_0_127',0,128),('late_128_511',128,512)]:
  selected=[q for (i,n),q in qrows.items() if a<=i<b];exact[phase]={'per_query_median_ns':{str(q):median(int(row['wall_ns']) for row in selected if int(row['query'])==q) for q in range(7)},'per_query_median_allocated_bytes':{str(q):median(int(row['allocated_bytes']) for row in selected if int(row['query'])==q) for q in range(7)},'variable_time_share':sum(int(q['wall_ns']) for q in selected if int(q['query'])>=5)/sum(int(q['wall_ns']) for q in selected),'median_query_cycle_bytes':median(sum(int(qrows[i,n]['allocated_bytes']) for n in range(7)) for i in range(a,b))}
 results.append({'label':label,'base':run['base'],'mode':run['mode'],'rep':run['rep'],'session_override':run['override'],'custom_query_events':len(seen),'sample_events':dict(total),'excluded':dict(excluded),'groups':groups,'exact_profiled_intervals':exact,'jfr_sha256':run['raw_recording_sha256'],'sampling_ms':5,'speedup_claim':False});print('PROFILE_OK',label,flush=True)
assert len(results)==9
(p/'profile-summary.json').write_text(json.dumps(results,indent=2)+'\n');print('PROFILE_ANALYSIS_OK')
