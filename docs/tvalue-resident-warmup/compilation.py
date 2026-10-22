from pathlib import Path
import csv,json,gzip,xml.etree.ElementTree as ET
p=Path(__file__).parent;results=[]
for clock_file in sorted(p.glob('*-jit.clock.json')):
 label=clock_file.name.removesuffix('.clock.json');clock=json.loads(clock_file.read_text());origin=(clock['nano_before']+clock['nano_after'])/2-clock['uptime_ms']*1e6
 rows=list(csv.DictReader((p/(label+'.csv')).open()));ranges=[(a,b,(int(rows[a]['start_ns'])-origin)/1e9,(int(rows[b-1]['end_ns'])-origin)/1e9) for a,b in [(0,3),(3,9),(12,24),(24,36),(36,48),(48,60)]]
 xml=ET.fromstring(gzip.decompress((p/(label+'.jit.xml.gz')).read_bytes()));methods={};compilations=[];deopts=[]
 for node in xml.iter('nmethod'):
  method=node.attrib.get('method','');methods[node.attrib['compile_id']]=method
  if not method.startswith('org.kanger.'):continue
  stamp=float(node.attrib['stamp']);cycle=next((int(r['iteration']) for r in rows if (int(r['start_ns'])-origin)/1e9<=stamp<=(int(r['end_ns'])-origin)/1e9),None)
  compilations.append({'method':method,'stamp':stamp,'level':node.attrib.get('level'),'compiler':node.attrib.get('compiler'),'compile_id':node.attrib['compile_id'],'cycle':cycle,'window':next((f'{a}:{b}' for a,b,start,end in ranges if start<=stamp<=end),None)})
 for node in xml.iter('make_not_entrant'):
  method=methods.get(node.attrib.get('compile_id'))
  if method and method.startswith('org.kanger.'):deopts.append({**node.attrib,'method':method})
 results.append({'label':label,'clock_uncertainty_ns':clock['nano_after']-clock['nano_before']+1000000,'windows':[{'cycles':f'{a}:{b}','start_s':start,'end_s':end,'kanger_nmethod_completions':sum(start<=x['stamp']<=end for x in compilations)} for a,b,start,end in ranges],'compilations':compilations,'made_not_entrant':deopts})
assert len(results)==4
(p/'compilation-summary.json').write_text(json.dumps(results,indent=2)+'\n')
for x in results:print(x['label'],[(w['cycles'],w['kanger_nmethod_completions']) for w in x['windows']])
