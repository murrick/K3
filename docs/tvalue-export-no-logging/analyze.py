from pathlib import Path
from statistics import median
import ast,csv,gzip,json,hashlib,subprocess
p=Path(__file__).parent
raw=Path('docs/tvalue-dirty-buckets/analyze.py').read_text();tree=ast.parse(raw)
for name in ('parseview','parsevalues','checkview','replay'):
 node=next(n for n in tree.body if isinstance(n,ast.FunctionDef) and n.name==name);body=ast.get_source_segment(raw,node)
 if name=='replay':body=body.replace("'order_changes':0","'order_changes':0,'term_changes':0").replace('assert old[id][1]==new[id][1];',"kinds['term_changes']+=old[id][1]!=new[id][1];")
 exec(body)
files=sorted(p.glob('*.result.json'));assert len(files)==48
per=[]
phases=('total','open','baseline','mutate_b','changed','mutate_a','restored','finish','close')
for f in files:
 r=json.loads(f.read_text());label=f.name.removesuffix('.result.json');assert r['exit_code']==0 and not (p/(label+'.err')).read_bytes()
 assert b'EXPORT_PHASE_OK' in gzip.decompress((p/(label+'.log.gz')).read_bytes())
 assert not any(any(flag in arg for flag in ('LogCompilation','LogFile=','-Xlog:','PrintCompilation','PrintInlining')) for arg in r['command'])
 assert ('-Xbatch' in r['command'])==(r['driver']=='batch')
 old=Path('docs/tvalue-observer-fast-disabled/cost')/f"{r['size']}-0-on-fast-attached"
 assert (p/(label+'.native.txt')).read_bytes()==Path(str(old)+'.native.txt').read_bytes()
 trace=p/(label+'.export.trace.gz');assert trace.read_bytes()==Path(str(old)+'.export.trace.gz').read_bytes();replay(trace)
 rows=list(csv.DictReader((p/(label+'.csv')).open()));assert len(rows)==128*9
 for phase in phases:
  cells=[x for x in rows if x['workload']==phase];assert [int(x['iteration']) for x in cells]==list(range(128))
  assert all(int(x['operations'])==8 and int(x['wall_ns'])>=0 and int(x['cpu_ns'])>=0 and int(x['allocated_bytes'])>=0 for x in cells)
  per.append({'size':r['size'],'profile':r['driver'],'repetition':r['repetition'],'module':r['module'],'phase':phase,**{k:median(int(x[col])/8 for x in cells) for k,col in [('ns','wall_ns'),('bytes','allocated_bytes'),('cpu_ns','cpu_ns')]}})
metrics=[]
for profile in ('default','batch'):
 for size in (32,128):
  for phase in phases:
   row={'profile':profile,'size':size,'phase':phase}
   for module in ('baseline','candidate'):
    cells=[x for x in per if (x['profile'],x['size'],x['phase'],x['module'])==(profile,size,phase,module)];assert len(cells)==6
    for key in ('ns','cpu_ns','bytes'):row[module+'_'+key]=median(x[key] for x in cells)
   ratios=[];savings=[]
   for rep in range(6):
    pair={x['module']:x for x in per if (x['profile'],x['size'],x['phase'],x['repetition'])==(profile,size,phase,rep)}
    ratios.append(pair['candidate']['ns']/pair['baseline']['ns']);savings.append(pair['baseline']['bytes']-pair['candidate']['bytes'])
   row.update(paired_ratios=ratios,paired_ratio=median(ratios),paired_saved_bytes=median(savings),pairs_slower=sum(x>1 for x in ratios));metrics.append(row)
sha=lambda f:hashlib.sha256(Path(f).read_bytes()).hexdigest()
b=json.loads(Path('docs/tvalue-export-phase-study/build-validation.json').read_text());assert all(sha(f)==v for f,v in b['classes'].items())
for stage in ('tvalue-journal-context-snapshots','tvalue-journal-touch-strings'):
 b=json.loads(Path('docs',stage,'build-validation.json').read_text());assert sha('../build/'+stage+'.jar')==b['jar_sha256']
assert subprocess.check_output(['git','-C','../K3-observer-native','rev-parse','HEAD'],text=True).strip()=='2422f7f6d9e1af7608203b1566df64b5f29fe344'
assert not subprocess.check_output(['git','-C','../K3-observer-native','status','--porcelain'])
summary={'JVMs':48,'export_cycles_measured':48*128*8,'trace_replays_identical':48,'native_controls_identical':True,'logging_enabled':False,'exact_JIT_compilation_levels_verified':False,'production_changed':False,'metrics':metrics}
(p/'summary.json').write_text(json.dumps(summary,indent=2)+'\n');(p/'per-JVM-medians.json').write_text(json.dumps(per,indent=2)+'\n')
for x in metrics:
 if x['phase'] in ('total','mutate_b','mutate_a'):print(x['profile'],x['size'],x['phase'],round(x['paired_ratio'],4),'saved',round(x['paired_saved_bytes'],2),'slower',x['pairs_slower'])
