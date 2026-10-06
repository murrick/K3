from pathlib import Path
import json,gzip,re,base64,collections
root=Path(__file__).parent
expected=json.loads((root/'expected.json').read_text());oracle=json.loads((root/'son-expected.json').read_text())
def read(label):
 assert not (root/(label+'.err')).read_bytes(),label
 return gzip.decompress((root/(label+'.log.gz')).read_bytes()).decode()
for mode in ('reference','profile'):
 text=read('candidate-'+mode);rows={};small={}
 for line in text.splitlines():
  if line.startswith('ROW '):
   fields=line.split();label=fields[1];kv=dict(f.split('=',1) for f in fields[3:]);source=base64.b64decode(kv['source']).decode();row={k:{'true':True,'false':False,'null':None}[kv[k]] for k in ('compiled','collision','answer','accepted')}
   if label=='son':assert source not in rows;rows[source]=row
   else:small.setdefault(label,[]).append(row['answer'])
 assert rows==oracle and small=={'small':[True,False,None],'external-item':[True,False,None],'external-other':[None,None,None]}
 assert re.findall(r'^CASE_OK .* reservations=0$',text,re.M) and 'EXACT_CANDIDATE_REPLAY_OK' in text
 contract=read('contract-'+mode);assert 'COMPLETED_HYPOTHESIS_CONTRACT_OK' in contract
 witness=(root/('witness-'+mode+'.log')).read_text();assert 'FRONTIER_DEPENDENCY_WITNESS_OK checks=31' in witness and not (root/('witness-'+mode+'.err')).read_bytes()
assert read('contract-reference')==read('contract-profile')
assert (root/'witness-reference.log').read_bytes()==(root/'witness-profile.log').read_bytes()
semantics={};profiles={}
for name in ('clean','profile-1','profile-2'):
 text=read(name)
 assert re.findall(r'^SAMPLE (\d+) .* raw=18 optimized=6 solutions=0 values=0$',text,re.M)==['0','1','2']
 for k,v in expected.items():assert re.findall(r'^'+k+r' (.*)$',text,re.M)==[v]*3
 semantics[name]={'snapshots':3,'RAW':expected['RAW'],'OPTIMIZED':expected['OPTIMIZED'],'stats':expected['POST_OPTIMIZE_LAST_LINKER_STATS']}
 if name=='clean':continue
 samples={};current=None
 for line in text.splitlines():
  if line.startswith('FRONTIER_BEGIN '):
   kv=dict(f.split('=') for f in line.split()[1:]);current=int(kv['sample']);samples[current]={'links':int(kv['links']),'rows':[],'headers':[],'witnesses':[]}
  elif line.startswith('LINK '):samples[current]['headers'].append(dict(f.split('=') for f in line.split()[1:]))
  elif line.startswith('FRONTIER '):
   kv=dict(f.split('=') for f in line.split()[1:]);samples[current]['rows'].append({k:v if k in ('phase','order') else int(v) for k,v in kv.items()})
  elif line.startswith('FRONTIER_WITNESS '):samples[current]['witnesses'].append(line)
  elif line.startswith('FRONTIER_END '):current=None
 assert samples.keys()=={0,1,2}
 for i,s in samples.items():
  assert s['links']==len(s['headers'])==133
  ids=[int(h['id']) for h in s['headers']];assert ids==list(range(1,134))
  groups=collections.defaultdict(list)
  for r in s['rows']:groups[(r['link'],r['pass'])].append(r)
  for key,pair in groups.items():assert {r['order'] for r in pair}=={'ascending','descending'} and len(pair)==2 and pair[0]['rules']==pair[1]['rules'],key
  for link in ids:
   passes=sorted({r['pass'] for r in s['rows'] if r['link']==link});assert passes==list(range(1,max(passes)+1))
  numeric=[k for k in s['rows'][0] if k not in ('link','phase','pass','order')]
  totals={k:sum(r[k] for r in s['rows']) for k in numeric}
  by_pass={str(p):{k:sum(r[k] for r in s['rows'] if r['pass']==p) for k in numeric} for p in sorted({r['pass'] for r in s['rows']})}
  by_phase={p:{k:sum(r[k] for r in s['rows'] if r['phase']==p) for k in numeric} for p in sorted({r['phase'] for r in s['rows']})}
  by_order={p:{k:sum(r[k] for r in s['rows'] if r['order']==p) for k in numeric} for p in ('ascending','descending')}
  first=by_pass['1'];later={k:totals[k]-first[k] for k in numeric}
  s['totals']=totals;s['by_pass']=by_pass;s['by_phase']=by_phase;s['by_order']=by_order;s['later_passes']=later
  s['later_shares']={k:later[k]/totals[k] for k in ('cpu_ns','rules','rotations','unifications')}
  s['without_tvalue_attempts_with_state_change_share']=totals['without_tvalue_attempts_with_state_change']/totals['rules']
  s['no_counted_change_share']=totals['no_counted_change']/totals['rules']
  s['signature']=[{k:v for k,v in r.items() if k!='cpu_ns'} for r in s['rows']]
 profiles[name]=samples
for i in range(3):assert profiles['profile-1'][i]['signature']==profiles['profile-2'][i]['signature'],('cross-JVM',i)
for name in profiles:
 assert profiles[name][1]['signature']==profiles[name][2]['signature'],('warm-index',name)
for name in profiles:
 for s in profiles[name].values():del s['signature']
for mode in ('reference','profile'):
 old=json.loads((root/(mode+'-census-class-sha256.json')).read_text());new=json.loads((root/(mode+'-class-sha256.json')).read_text())
 assert {k for k,v in old.items() if new.get(k)!=v}=={'org/kanger/FrontierDependencyWitness.class'}
 assert all(old[k]==new[k] for k in old if k.startswith('org/kanger/Linker'))
comparison=json.loads((root/'class-comparison.json').read_text());ref=json.loads((root/'reference-class-sha256.json').read_text());base=json.loads((root/'base-class-sha256.json').read_text());assert len(base)==651 and all(ref[k]==v for k,v in base.items())
result={'base':comparison['base'],'clean_baseline_classes_identical':651,'changed_shared_classes':comparison['changed'],'semantic_snapshots':semantics,'native_witness_checks_per_mode':31,'candidate_sources_per_mode':47,'profiles':profiles,'timing_scope':'sum of instrumented rule-visit current-thread CPU intervals; excludes domain-index construction, active-set expansion, per-rule snapshot bookkeeping and outer optimizer work; descriptive only, not removable cost or speedup'}
(root/'details.json.gz').write_bytes(gzip.compress((json.dumps(result,indent=2)+'\n').encode(),mtime=0))
for samples in profiles.values():
 for sample in samples.values():
  sample['phase_link_counts']=dict(collections.Counter(h['phase'] for h in sample['headers']))
  sample['recorded_pass_order_groups']=len(sample['rows'])
  for key in ('headers','rows','witnesses'):del sample[key]
result['details']='details.json.gz contains every recorded group, link header and witness'
(root/'summary.json').write_text(json.dumps(result,indent=2)+'\n')
print(json.dumps({name:{'sample2':{k:samples[2][k] for k in ('totals','by_pass','by_phase','by_order','later_shares','without_tvalue_attempts_with_state_change_share','no_counted_change_share')}} for name,samples in profiles.items()},indent=2))
print('FRONTIER_ANALYSIS_OK snapshots=9 source_oracles=2 witnesses=62')
