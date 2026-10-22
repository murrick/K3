from pathlib import Path
from statistics import median
import ast,csv,gzip,json,re,hashlib
p=Path(__file__).parent
raw=Path('docs/tvalue-dirty-buckets/analyze.py').read_text();tree=ast.parse(raw)
for name in ('parseview','parsevalues','checkview','replay'):
 node=next(n for n in tree.body if isinstance(n,ast.FunctionDef) and n.name==name);body=ast.get_source_segment(raw,node)
 if name=='replay':body=body.replace("'order_changes':0","'order_changes':0,'term_changes':0").replace('assert old[id][1]==new[id][1];',"kinds['term_changes']+=old[id][1]!=new[id][1];")
 exec(body)
files=sorted(p.glob('*.result.json'));assert len(files)==76,len(files)
per=[];traces=0;pairs=0;windows=[(0,3),(3,9),(12,24),(24,36),(36,48),(48,60)]
for f in files:
 r=json.loads(f.read_text());label=f.name.removesuffix('.result.json');assert r['exit_code']==0 and not (p/(label+'.err')).read_bytes()
 assert b'RESIDENT_INFERENCE_OK' in gzip.decompress((p/(label+'.log.gz')).read_bytes())
 if not r['logging']:assert all(not any(t in x for t in ('LogCompilation','LogFile','Xlog','PrintCompilation')) for x in r['command'])
 native=(p/(label+'.native.txt')).read_bytes();control=p/f"{r['profile']}-{r['flag']}-{r['rep']}-clean.native.txt";assert native==control.read_bytes(),label
 assert len(native.splitlines())==420 and native.count(b'values=[{x=Tom}]')==120
 rows=list(csv.DictReader((p/(label+'.csv')).open()));assert len(rows)==60
 assert [int(x['iteration']) for x in rows]==list(range(60))
 assert all(int(x['queries'])==7 and int(x['wall_ns'])>=int(x['query_wall_ns'])>0 and int(x['allocated_bytes'])>=int(x['query_allocated_bytes'])>=0 and int(x['end_ns'])-int(x['start_ns'])==int(x['wall_ns']) for x in rows)
 entry={k:v for k,v in r.items() if k!='command'};entry['label']=label;entry['windows']={f'{a}:{b}':{key:median(int(x[key]) for x in rows[a:b]) for key in ('wall_ns','query_wall_ns','allocated_bytes','query_allocated_bytes')} for a,b in windows};per.append(entry)
 if r['mode'] in ('baseline','candidate'):
  for i in range(60):
   trace=p/f'{label}.{i}.trace.gz';replay(trace);traces+=1
   events=(p/f'{label}.{i}.events.txt').read_text();assert 'metadata=' in events and 'add=' in events
   if r['mode']=='candidate':
    ref=p/(label.replace('-candidate','-baseline')+f'.{i}');assert trace.read_bytes()==Path(str(ref)+'.trace.gz').read_bytes();assert events==Path(str(ref)+'.events.txt').read_text();pairs+=1
 else:assert not list(p.glob(label+'.*.trace.gz'))
metrics=[]
for profile in ('default','batch'):
 for flag in ('on','off','verify'):
  result={'profile':profile,'flag':flag,'windows':{}}
  for a,b in windows:
   key=f'{a}:{b}';rr={}
   for field in ('wall_ns','query_wall_ns','allocated_bytes','query_allocated_bytes'):
    ratios=[];savings=[]
    for rep in range(3):
     pair={x['mode']:x['windows'][key][field] for x in per if not x['logging'] and x['profile']==profile and x['flag']==flag and x['rep']==rep}
     ratios.append(pair['candidate']/pair['baseline']);savings.append(pair['baseline']-pair['candidate'])
    rr[field]={'paired_ratios':ratios,'median_ratio':median(ratios),'slower_pairs':sum(x>1 for x in ratios),'median_bytes_saved' if 'bytes' in field else 'median_ns_saved':median(savings)}
   result['windows'][key]=rr
  metrics.append(result)
assert traces==2400 and pairs==1200,(traces,pairs)
archive=p/'pre-alignment'
assert len(list(archive.glob('*.result.json')))==4
archive_replays=0
for f in archive.glob('*.result.json'):
 r=json.loads(f.read_text());label=f.name.removesuffix('.result.json');assert r['exit_code']==0 and not (archive/(label+'.err')).read_bytes()
 assert (archive/(label+'.native.txt')).read_bytes()==(p/f"{r['profile']}-{r['flag']}-{r['rep']}-clean.native.txt").read_bytes()
 for i in range(60):
  trace=archive/f'{label}.{i}.trace.gz';replay(trace);archive_replays+=1
  if r['mode']=='candidate':assert trace.read_bytes()==(archive/(label.replace('-candidate','-baseline')+f'.{i}.trace.gz')).read_bytes()
assert archive_replays==240
summary={'executed_JVMs':80,'archived_unaligned_logged_JVMs':4,'archived_trace_replays':archive_replays,'executed_queries':80*60*7,'JVMs':76,'unlogged_JVMs':72,'diagnostic_logged_JVMs':4,'queries':76*60*7,'trace_replays':traces,'identical_trace_pairs':pairs,'metrics':metrics,'limitations':['new host and Java 21; not pooled with Java 17 results','shared host; three pairs per cell','bounded resident fixture; no SMART/full corpus qualification']}
(p/'summary.json').write_text(json.dumps(summary,indent=2)+'\n');(p/'per-JVM-windows.json').write_text(json.dumps(per,indent=2)+'\n')
for x in metrics:
 print(x['profile'],x['flag'],[(k,round(v['query_wall_ns']['median_ratio'],3),v['query_wall_ns']['slower_pairs']) for k,v in x['windows'].items()])
print('WARMUP_ANALYSIS_OK',traces,pairs)
