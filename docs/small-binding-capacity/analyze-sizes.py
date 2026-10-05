from pathlib import Path
import re,json
root=Path(__file__).parent
expected=json.loads((root/'expected.json').read_text());warm=[]
for n in [1,2]:
 s=(root/f'sizes-{n}.log').read_text()
 for key,value in expected.items():assert re.findall(r'^'+key+r' (.*)',s,re.M)==[value]*6
 assert not (root/f'sizes-{n}.err').read_text()
 records=re.findall(r'^BINDING_SIZE sample=(\d+) calls=(\d+) custom=(\d+) failures=(\d+) overflow=(\d+) histogram=(\[.*\])$',s,re.M);assert len(records)==6
 for record in records:
  sample,calls,custom,failures,overflow=map(int,record[:5]);hist=json.loads(record[5]);assert sum(hist)+custom+failures+overflow==calls
  if sample>=2:
   row={'run':n,'sample':sample,'calls':calls,'custom':custom,'failures':failures,'overflow':overflow,'lengths':{i:c for i,c in enumerate(hist) if c}};warm.append(row)
(root/'sizes-summary.json').write_text(json.dumps(warm,indent=2)+'\n')
for row in warm:print(json.dumps(row))
