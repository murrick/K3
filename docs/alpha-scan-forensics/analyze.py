from pathlib import Path
import re,subprocess,collections
root=Path(__file__).parent
texts={'RAW':set(),'OPTIMIZED':set(),'POST_OPTIMIZE_LAST_LINKER_STATS':set()}
warm=[]
for run in [1,2]:
 p=root/('profile-%d.log'%run);s=p.read_text();assert not p.with_suffix('.err').read_text()
 assert re.findall(r'^SAMPLE (\d+) ',s,re.M)==[str(i) for i in range(6)]
 assert len(re.findall(r' raw=18 optimized=6 solutions=0 values=0$',s,re.M))==6
 events={i:collections.defaultdict(int) for i in range(6)};tests={i:{} for i in range(6)};hist={i:{} for i in range(6)}
 for i,k,n in re.findall(r'^ALPHA_EVENT (\d+) (\S+) (\d+)$',s,re.M):events[int(i)][k]=int(n)
 for i,k,f,t in re.findall(r'^ALPHA_TEST (\d+) (\S+) false=(\d+) true=(\d+)$',s,re.M):tests[int(i)][k]=(int(f),int(t))
 for i,k,n in re.findall(r'^ALPHA_HIST (\d+) (\S+) (\d+)$',s,re.M):hist[int(i)][k]=int(n)
 for i in range(6):
  e,t,h=events[i],tests[i],hist[i]
  assert e['exception']==0 and e['calls']==e['gateRejected']+e['eligible']
  assert e['eligible']==e['hit']+e['miss']
  assert e['visits']==e['flagsRejected']+e['shapeCalls']
  assert e['flagsRejected']==t['notStored'][1]+t['candidateQuery'][1]
  assert e['shapeCalls']==sum(t[k][1] for k in ['polarityMismatch','predicateMismatch','arityMismatch'])+e['shapeAccepted']
  assert e['shapeAccepted']==e['hit']+sum(e[k] for k in ['emptyArgument','cvarKindMismatch','dominiMismatch','bijectionMismatch','termMismatch'])
  assert t['sourceQuery'][0]+t['sourceQuery'][1]==e['calls']
  assert sum(t['sourceContainsCVar'])==t['sourceQuery'][0]
  assert t['sourceContainsCVar'][1]==e['eligible']
  assert sum(h.values())==e['calls']
  assert sum(int(k.split(':')[1])*n for k,n in h.items())==e['visits']
  for outcome in ['gateRejected','hit','miss']:
   assert sum(n for k,n in h.items() if k.startswith(outcome+':'))==e[outcome]
  assert h['gateRejected:0']==e['gateRejected']
  if i>=2:warm.append((dict(e),t,h))
 for label,target in texts.items():
  lines=re.findall(r'^'+label+r' .*$',s,re.M);assert len(lines)==6;target.update(lines)
 print('PROFILE_COMPLETE',run)
assert all(len(t)==1 for t in texts.values())
reference=subprocess.check_output(['git','show','e5a6f6608df967491dfb86295e6542cc84c7e8bf:docs/active-mind-reuse-eight/reuse-1-reference.log'],text=True)
for label in ['RAW','OPTIMIZED']:assert texts[label]==set(re.findall(r'^'+label+r' .*$',reference,re.M))
assert all(row==warm[0] for row in warm)
e,t,h=warm[0]
for k,n in sorted(e.items()):print('event',k,n)
for k,(f,tr) in sorted(t.items()):print('test',k,'false='+str(f),'true='+str(tr))
for outcome in ['hit','miss']:
 rows=sorted((int(k.split(':')[1]),n) for k,n in h.items() if k.startswith(outcome+':'))
 count=sum(n for v,n in rows);total=sum(v*n for v,n in rows)
 def quantile(q):
  running=0
  for value,n in rows:
   running+=n
   if running>=q*count:return value
 print('scan',outcome,'count='+str(count),'visits='+str(total),'mean=%.6f'%(total/count),'median='+str(quantile(.5)),'p95='+str(quantile(.95)),'max='+str(rows[-1][0]))
print('shape_rejected_share=%.6f%%'%(100*sum(t[k][1] for k in ['polarityMismatch','predicateMismatch','arityMismatch'])/e['visits']))
print('all_rejected_before_arguments_share=%.6f%%'%(100*(e['visits']-e['shapeAccepted'])/e['visits']))
print(next(iter(texts['POST_OPTIMIZE_LAST_LINKER_STATS'])))
witness=[]
for variant in ['reference','instrumented']:
 p=root/('witness-'+variant+'.log');s=p.read_text();assert not p.with_suffix('.err').read_text()
 assert 'ALPHA_SCAN_WITNESS_OK scenarios=3 checks=45' in s;witness.append(s)
assert witness[0]==witness[1]
print('ALPHA_SCAN_FORENSICS_OK samples=12 warm_samples=8 texts_equal_to_clean_base=true witnesses=3 checks_per_build=45')
print('TIMING_EXCLUDED diagnostic_instrumentation=true main_optimization_thread_only=true')
