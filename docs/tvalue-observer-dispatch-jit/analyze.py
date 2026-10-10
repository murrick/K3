from pathlib import Path
from statistics import median
import csv,json,gzip,hashlib,xml.etree.ElementTree as ET,collections
p=Path(__file__).parent;results=list(p.glob('*.result.json'));assert len(results)==75
per=[];native=None;jit={};nonzero=0
for file in sorted(results):
 r=json.loads(file.read_text());label=file.name.removesuffix('.result.json');assert r['exit_code']==0 and not (p/(label+'.err')).read_bytes()
 assert b'MINIMAL_OBSERVER_OK' in gzip.decompress((p/(label+'.log.gz')).read_bytes())
 current=(p/(label+'.native.txt')).read_bytes()
 if native is None:native=current
 else:assert native==current
 rows=list(csv.DictReader((p/(label+'.csv')).open()));assert len(rows)==128
 if not r['jit']:nonzero+=sum(int(x['allocated_bytes'])>0 for x in rows)
 for workload in ('bridge','native'):
  samples=[x for x in rows if x['workload']==workload];assert [int(x['iteration']) for x in samples]==list(range(64))
  assert all(int(x['operations'])==65536 and int(x['wall_ns'])>0 and int(x['allocated_bytes'])>=0 for x in samples)
  per.append({'mode':r['mode'],'repetition':r['repetition'],'jit':r['jit'],'workload':workload,'ns':median(int(x['wall_ns'])/65536 for x in samples),'bytes':median(int(x['allocated_bytes'])/65536 for x in samples),'first_half_ns':median(int(x['wall_ns'])/65536 for x in samples[:32]),'second_half_ns':median(int(x['wall_ns'])/65536 for x in samples[32:])})
 if r['jit']:
  root=ET.fromstring(gzip.decompress((p/(label+'.xml.gz')).read_bytes()));evidence=collections.Counter()
  for task in root.iter('task'):
   classes={n.attrib['id']:n.attrib.get('name','') for n in task.iter('klass')}
   methods={n.attrib['id']:classes.get(n.attrib.get('holder'),'')+'::'+n.attrib.get('name','') for n in task.iter('method')}
   for parse in task.iter('parse'):
    last=None
    for node in parse:
     if node.tag=='call':last=methods.get(node.attrib.get('method'),'')
     elif node.tag in ('inline_success','inline_fail') and last:
      if 'TValueObservation' in last or 'MinimalObserverRunner$Counter' in last:
       evidence[(last,node.tag,node.attrib.get('reason',''))]+=1
      last=None
  jit[r['mode']]=[{'method':k[0],'outcome':k[1],'reason':k[2],'count':v} for k,v in sorted(evidence.items())]
  assert jit[r['mode']],r['mode']
metrics=[]
for workload in ('bridge','native'):
 row={'workload':workload}
 for mode in ('slow-disabled','slow-attached','fast-disabled','fast-attached','direct-disabled','direct-attached'):
  cells=[x for x in per if not x['jit'] and x['mode']==mode and x['workload']==workload];assert len(cells)==12
  row[mode]={'ns':median(x['ns'] for x in cells),'bytes':median(x['bytes'] for x in cells),'JVM_range_ns':[min(x['ns'] for x in cells),max(x['ns'] for x in cells)]}
 for mode in ('fast','direct'):
  ratios=[]
  for rep in range(12):
   cells={x['mode']:x['ns'] for x in per if not x['jit'] and x['workload']==workload and x['repetition']==rep}
   ratios.append(cells[mode+'-attached']/cells['slow-attached'])
  row[mode+'_slow_attached_paired_ratio']=median(ratios);row[mode+'_ratio_range']=[min(ratios),max(ratios)]
 metrics.append(row)
sha=lambda f:hashlib.sha256(f.read_bytes()).hexdigest()
b=json.loads((p/'build-validation.json').read_text());assert b['source_sha256']==sha(p/'MinimalObserverRunner.java')
assert all(sha(Path('../build/tvalue-observer-dispatch-jit')/k)==v for k,v in b['class_sha256'].items())
summary={'JVMs':75,'timing_JVMs':72,'JIT_JVMs':3,'timing_batches':9216,'nonzero_allocation_timing_batches':nonzero,'all_batches_check_exact_callback_count_and_payload_checksum':True,'native_controls_identical':True,'metrics':metrics,'jit_evidence':jit,'diagnostic_journals_enabled':False,'diagnostic_export_qualified':False,'production_changed':False,'end_to_end_inference_speedup_qualified':False,'exclusive_host':False}
(p/'per-JVM-medians.json').write_text(json.dumps(per,indent=2)+'\n');(p/'summary.json').write_text(json.dumps(summary,indent=2)+'\n');print(json.dumps(summary,indent=2))
