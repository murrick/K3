from pathlib import Path
import re,json,subprocess,collections
root=Path(__file__).parent
raw=set();final=set();stats=set();warm=[];warm_bind=[]
for run in [1,2]:
 p=root/('profile-%d.log'%run);s=p.read_text();assert not p.with_suffix('.err').read_text()
 assert re.findall(r'^SAMPLE (\d+) ',s,re.M)==[str(i) for i in range(6)]
 assert len(re.findall(r' raw=18 optimized=6 solutions=0 values=0$',s,re.M))==6
 rows={i:{} for i in range(6)};bindings={i:{} for i in range(6)};samples=collections.Counter()
 for i,site,c,u in re.findall(r'^BINDING_ROUTE (\d+) (\S+) (\[.*\]) unique_domains=(\d+)$',s,re.M):
  c=json.loads(c);assert c[0]==c[1]+c[2] and c[3]==c[4]+c[5] and c[6]<=c[5]
  bindings[int(i)][site]=(c,int(u))
 for i,site,c,u in re.findall(r'^RULE_READ (\d+) (\S+) count=(\d+) unique_rules=(\d+)$',s,re.M):
  rows[int(i)][site]=(int(c),int(u))
 for i,site,n in re.findall(r'^RULE_STACK (\d+) (\S+) samples=(\d+) ',s,re.M):samples[int(i),site]+=int(n)
 for i in range(6):
  assert rows[i] and bindings[i]
  for site,(c,u) in rows[i].items():assert samples[i,site]==(c+1023)//1024
  if i>=2:warm.append(rows[i]);warm_bind.append(bindings[i])
 for label,target in [('RAW',raw),('OPTIMIZED',final),('POST_OPTIMIZE_LAST_LINKER_STATS',stats)]:
  lines=re.findall(r'^'+label+r' .*$',s,re.M);assert len(lines)==6;target.update(lines)
 print('PROFILE_COMPLETE',run)
assert len(raw)==len(final)==len(stats)==1
reference=subprocess.check_output(['git','show','e5a6f6608df967491dfb86295e6542cc84c7e8bf:docs/active-mind-reuse-eight/reuse-1-reference.log'],text=True)
assert raw==set(re.findall(r'^RAW .*$',reference,re.M)) and final==set(re.findall(r'^OPTIMIZED .*$',reference,re.M))
assert all(set(w)==set(warm[0]) for w in warm)
for site in sorted(warm[0],key=lambda x:-warm[0][x][0]):
 counts=[w[site][0] for w in warm];unique=[w[site][1] for w in warm]
 domains=[sum(c[0] for route,(c,u) in b.items() if route.startswith(site+'>Rule:')) for b in warm_bind]
 print(site,'reads_min='+str(min(counts)),'reads_max='+str(max(counts)),'unique_rules='+str(sorted(set(unique))),
       'domain_calls_min='+str(min(domains)),'domain_calls_max='+str(max(domains)))
for label,key in [('point_reads','Escalera:280'),('iterator_reads','Escalera:655'),('top_reads','RuleFactory:1037')]:
 counts=[sum(c for site,(c,u) in w.items() if key in site) for w in warm]
 print(label,'min='+str(min(counts)),'max='+str(max(counts)))
domain_totals=[sum(c[0] for c,u in b.values()) for b in warm_bind]
rule_domain_totals=[sum(c[0] for site,(c,u) in b.items() if '>Step:57>Rule:390' in site) for b in warm_bind]
print('total_domain_calls',min(domain_totals),max(domain_totals))
print('rule_cascade_domain_calls',min(rule_domain_totals),max(rule_domain_totals))
for prefix in ['Domain:460>','GeneratedCVarMaterializer:57>']:
 reads=[sum(c for site,(c,u) in w.items() if site.startswith(prefix)) for w in warm]
 domains=[sum(c[0] for site,(c,u) in b.items() if site.startswith(prefix)) for b in warm_bind]
 shares=[100*c/t for c,t in zip(domains,domain_totals)]
 print(prefix,'reads='+str(sorted(set(reads))),'domains='+str(sorted(set(domains))),
       'all_domain_share_min=%.6f%% all_domain_share_max=%.6f%%'%(min(shares),max(shares)))
print(next(iter(stats)))
print('RULE_READ_FORENSICS_OK samples=12 warm_samples=8 texts_equal_to_clean_base=true')
print('TIMING_EXCLUDED diagnostic_instrumentation=true main_optimization_thread_only=true')
