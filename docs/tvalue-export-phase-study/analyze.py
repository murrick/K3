from pathlib import Path
from statistics import median
import ast,csv,gzip,json,hashlib,subprocess
p=Path(__file__).parent
source=Path('docs/tvalue-journal-touch-strings/analyze.py').read_text()
# Same authority replay parser used in the qualified preceding stages.
tree=ast.parse(Path('docs/tvalue-dirty-buckets/analyze.py').read_text());raw=Path('docs/tvalue-dirty-buckets/analyze.py').read_text()
for name in ('parseview','parsevalues','checkview','replay'):
 node=next(n for n in tree.body if isinstance(n,ast.FunctionDef) and n.name==name);body=ast.get_source_segment(raw,node)
 if name=='replay':body=body.replace("'order_changes':0","'order_changes':0,'term_changes':0").replace('assert old[id][1]==new[id][1];',"kinds['term_changes']+=old[id][1]!=new[id][1];")
 exec(body)
files=list(p.glob('*.result.json'));assert len(files)==24
per=[]
for f in files:
 r=json.loads(f.read_text());label=f.name.removesuffix('.result.json');assert r['exit_code']==0 and not (p/(label+'.err')).read_bytes()
 assert b'EXPORT_PHASE_OK' in gzip.decompress((p/(label+'.log.gz')).read_bytes())
 old=Path('docs/tvalue-observer-fast-disabled/cost')/f"{r['size']}-0-on-fast-attached"
 assert (p/(label+'.native.txt')).read_bytes()==Path(str(old)+'.native.txt').read_bytes()
 trace=p/(label+'.export.trace.gz');assert trace.read_bytes()==Path(str(old)+'.export.trace.gz').read_bytes();replay(trace)
 rows=list(csv.DictReader((p/(label+'.csv')).open()));assert len(rows)==128*9
 for phase in ['total','open','baseline','mutate_b','changed','mutate_a','restored','finish','close']:
  cells=[x for x in rows if x['workload']==phase];assert [int(x['iteration']) for x in cells]==list(range(128))
  assert all(int(x['operations'])==8 and int(x['wall_ns'])>=0 and int(x['cpu_ns'])>=0 and int(x['allocated_bytes'])>=0 for x in cells)
  per.append({'size':r['size'],'repetition':r['repetition'],'module':r['module'],'phase':phase,**{k:median(int(x[col])/8 for x in cells) for k,col in [('ns','wall_ns'),('bytes','allocated_bytes'),('cpu_ns','cpu_ns')]}})
metrics=[]
for size in (32,128):
 for phase in ['total','open','baseline','mutate_b','changed','mutate_a','restored','finish','close']:
  row={'size':size,'phase':phase}
  for module in ('baseline','candidate'):
   cells=[x for x in per if (x['size'],x['phase'],x['module'])==(size,phase,module)];assert len(cells)==6
   for k in ('ns','cpu_ns','bytes'):row[module+'_'+k]=median(x[k] for x in cells)
  for key in ('ns','cpu_ns'):
   ratios=[]
   for rep in range(6):
    pair={x['module']:x for x in per if (x['size'],x['phase'],x['repetition'])==(size,phase,rep)}
    if pair['baseline'][key]>0:ratios.append(pair['candidate'][key]/pair['baseline'][key])
   row[key+'_paired_ratios']=ratios;row[key+'_paired_ratio']=median(ratios) if ratios else None
  metrics.append(row)
sha=lambda f:hashlib.sha256(Path(f).read_bytes()).hexdigest();build=json.loads((p/'build-validation.json').read_text())
assert sha(p/'ExportPhaseRunner.java')==build['source_sha256'];assert all(sha(f)==h for f,h in {**build['classes'],**build['jars']}.items())
for module,path in [('baseline','docs/tvalue-journal-context-snapshots'),('candidate','docs/tvalue-journal-touch-strings')]:
 record=json.loads(Path(path+'/build-validation.json').read_text());jar='../build/'+Path(path).name+'.jar';assert sha(jar)==record['jar_sha256']
assert not subprocess.check_output(['git','diff','aac2437d055b146a765fc0efc2849a209c10cef7','--','kanger','kanger-qualification','docs/tvalue-journal-touch-strings','docs/tvalue-journal-context-snapshots'])
assert subprocess.check_output(['git','-C','../K3-observer-native','rev-parse','HEAD'],text=True).strip()=='2422f7f6d9e1af7608203b1566df64b5f29fe344'
assert not subprocess.check_output(['git','-C','../K3-observer-native','status','--porcelain'])
summary={'JVMs':24,'export_cycles_measured':24*128*8,'phase_cells_measured':24*128*8,'traces_identical_and_replayed':24,'production_changed':False,'metrics':metrics}
(p/'summary.json').write_text(json.dumps(summary,indent=2)+'\n');(p/'per-JVM-medians.json').write_text(json.dumps(per,indent=2)+'\n')
for row in metrics:print(row['size'],row['phase'],round(row['baseline_ns']),round(row['candidate_ns']),round(row['ns_paired_ratio'],3),row['baseline_bytes'],row['candidate_bytes'])
