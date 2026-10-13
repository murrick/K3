from pathlib import Path
import csv,gzip,json,hashlib,re,subprocess,collections,xml.etree.ElementTree as ET
from statistics import median
p=Path(__file__).parent;files=list(p.glob('*.result.json'));assert len(files)==24
per=[];jit=[];gc=[]
for f in sorted(files):
 r=json.loads(f.read_text());label=f.name.removesuffix('.result.json');assert r['exit_code']==0 and not (p/(label+'.err')).read_bytes()
 assert b'EXPORT_PHASE_OK' in gzip.decompress((p/(label+'.log.gz')).read_bytes())
 old=Path('docs/tvalue-observer-fast-disabled/cost/32-0-on-fast-attached')
 assert (p/(label+'.native.txt')).read_bytes()==Path(str(old)+'.native.txt').read_bytes()
 assert (p/(label+'.export.trace.gz')).read_bytes()==Path(str(old)+'.export.trace.gz').read_bytes()
 rows=list(csv.DictReader((p/(label+'.csv')).open()));assert len(rows)==1152
 for phase in ('total','open','baseline','mutate_b','changed','mutate_a','restored','finish','close'):
  cells=[x for x in rows if x['workload']==phase];assert [int(x['iteration']) for x in cells]==list(range(128))
  assert all(int(x['operations'])==8 and int(x['allocated_bytes'])>=0 and int(x['wall_ns'])>=0 for x in cells)
  per.append({'profile':r['driver'],'module':r['module'],'rep':r['repetition'],'phase':phase,**{k:median(int(x[v])/8 for x in cells) for k,v in [('ns','wall_ns'),('cpu_ns','cpu_ns'),('bytes','allocated_bytes')]}})
 root=ET.fromstring(gzip.decompress((p/(label+'.jit-selected.xml.gz')).read_bytes()))
 tasks=list(root.findall('task'));assert tasks
 jit.append({'label':label,'raw_sha256':root.attrib['raw_sha256'],'raw_bytes':int(root.attrib['raw_bytes']),'tasks':[{'method':t.attrib['method'],'compiler':t.attrib.get('compiler'),'compile_id':t.attrib.get('compile_id'),'stamp':t.attrib.get('stamp'),'inline_success':len(list(t.iter('inline_success'))),'inline_failures':dict(collections.Counter(x.attrib.get('reason','') for x in t.iter('inline_fail')))} for t in tasks],'compiled':[dict(n.attrib) for n in root.findall('nmethod')]})
 text=(p/(label+'.gc.txt')).read_text();pauses=[float(x) for x in re.findall(r'\[info\]\[gc\s*\].*Pause[^\n]*? ([0-9.]+)ms',text)]
 gc.append({'label':label,'pause_count':len(pauses),'pause_total_ms':sum(pauses),'pause_max_ms':max(pauses,default=0),'full_gc':bool(re.search(r'Pause Full',text))})
metrics=[]
for profile in ('default','batch'):
 for phase in ('total','baseline','mutate_b','changed','mutate_a','restored','finish'):
  row={'profile':profile,'phase':phase}
  for module in ('baseline','candidate'):
   cells=[x for x in per if (x['profile'],x['phase'],x['module'])==(profile,phase,module)];assert len(cells)==6
   for key in ('ns','cpu_ns','bytes'):row[module+'_'+key]=median(x[key] for x in cells)
  ratios=[]
  for rep in range(6):
   pair={x['module']:x for x in per if (x['profile'],x['phase'],x['rep'])==(profile,phase,rep)};ratios.append(pair['candidate']['ns']/pair['baseline']['ns'])
  row['paired_ratios']=ratios;row['paired_ratio']=median(ratios);metrics.append(row)
sha=lambda f:hashlib.sha256(Path(f).read_bytes()).hexdigest()
for stage in ('tvalue-journal-context-snapshots','tvalue-journal-touch-strings'):
 b=json.loads(Path('docs',stage,'build-validation.json').read_text());assert sha('../build/'+stage+'.jar')==b['jar_sha256']
b=json.loads(Path('docs/tvalue-export-phase-study/build-validation.json').read_text());assert all(sha(f)==h for f,h in b['classes'].items())
assert not subprocess.check_output(['git','diff','23f01d8816f821f92d983ae71da26bc505d731c3','--','kanger','kanger-qualification','docs/tvalue-export-phase-study'])
summary={'JVMs':24,'traces_identical':24,'native_controls_identical':True,'metrics':metrics,'GC_whole_JVM':gc,'JIT_selected':jit,'phase_or_measurement_interval_GC_attribution_available':False}
(p/'summary.json').write_text(json.dumps(summary,indent=2)+'\n');(p/'per-JVM-medians.json').write_text(json.dumps(per,indent=2)+'\n')
for row in metrics:print(row['profile'],row['phase'],round(row['paired_ratio'],3),row['baseline_bytes'],row['candidate_bytes'])
for profile in ('default','batch'):
 for module in ('baseline','candidate'):
  cells=[x for x in gc if x['label'].startswith(profile) and x['label'].endswith(module)];print('GC',profile,module,'median pauses',median(x['pause_count'] for x in cells),'median total ms',median(x['pause_total_ms'] for x in cells))
