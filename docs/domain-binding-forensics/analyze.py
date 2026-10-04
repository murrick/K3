from pathlib import Path
import re,json,subprocess
root=Path(__file__).parent
raw=set();final=set();warm=[]
for run in [1,2]:
 p=root/('profile-%d.log'%run);s=p.read_text();assert not p.with_suffix('.err').read_text()
 rows={i:{} for i in range(6)}
 for i,site,c,u in re.findall(r'^BINDING_ROUTE (\d+) (\S+) (\[.*\]) unique_domains=(\d+)$',s,re.M):rows[int(i)][site]=(json.loads(c),int(u))
 assert all(rows.values())
 for i in range(6):
  for c,u in rows[i].values():assert c[0]==c[1]+c[2] and c[3]==c[4]+c[5] and c[6]<=c[5]
  if i>=2:warm.append(rows[i])
 for label,target in [('RAW',raw),('OPTIMIZED',final)]:
  lines=re.findall(r'^'+label+r' .*$',s,re.M);assert len(lines)==6;target.update(lines)
 print('PROFILE_COMPLETE',run)
assert len(raw)==len(final)==1
reference=subprocess.check_output(['git','show','e5a6f6608df967491dfb86295e6542cc84c7e8bf:docs/active-mind-reuse-eight/reuse-1-reference.log'],text=True)
assert raw==set(re.findall(r'^RAW .*$',reference,re.M)) and final==set(re.findall(r'^OPTIMIZED .*$',reference,re.M))
for site in sorted(warm[0]):
 rows=[w[site] for w in warm]
 print(site,'counts_min='+str([min(c[j] for c,u in rows) for j in range(7)]),'counts_max='+str([max(c[j] for c,u in rows) for j in range(7)]),'unique_domains='+str(sorted({u for c,u in rows})))
totals=[[sum(c[j] for c,u in w.values()) for j in range(7)] for w in warm]
for j,label in enumerate(['domain_calls','same_domain_mind','changed_domain_mind','variable_selections','same_variable_mind','changed_variable_mind','same_domain_changed_variable']):
 print(label,'min='+str(min(c[j] for c in totals)),'max='+str(max(c[j] for c in totals)))
for label,num,den in [('rule_cache_cascade',None,0),('same_domain_mind',1,0),('same_variable_mind',4,3)]:
 shares=[100*((w['Step:57>Rule:390'][0][0] if num is None else c[num])/c[den]) for w,c in zip(warm,totals)]
 print(label,'share_min=%.6f%% share_max=%.6f%%'%(min(shares),max(shares)))
assert 'DOMAIN_BINDING_WITNESS_OK scenarios=3 checks=21' in (root/'witness.log').read_text()
assert not (root/'witness.err').read_text()
print('DOMAIN_BINDING_FORENSICS_OK samples=12 warm_samples=8 texts_equal_to_clean_base=true witnesses=3')
print('TIMING_EXCLUDED diagnostic_instrumentation=true main_optimization_thread_only=true')
