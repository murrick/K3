from pathlib import Path
import subprocess,re
root=Path(__file__).parent
base=['java','-Xmx512m','-cp','../build/rule-read-classes:kanger/resources:kanger-udf/src:lib/jline-3.13.0.jar']
for run in [1,2]:
 p=root/('profile-%d'%run)
 with p.with_suffix('.log').open('w') as out,p.with_suffix('.err').open('w') as err:subprocess.run(base[:2]+['-Dbench.samples=6']+base[2:]+['org.kanger.DomainBindingProfileRunner'],stdout=out,stderr=err,check=True)
 s=p.with_suffix('.log').read_text()
 assert re.findall(r'^SAMPLE (\d+) ',s,re.M)==[str(i) for i in range(6)]
 assert len(re.findall(r'^BINDING_ROUTE ',s,re.M))>=6
 assert not p.with_suffix('.err').read_text()
 print('DONE',p,flush=True)
p=root/'witness'
with p.with_suffix('.log').open('w') as out,p.with_suffix('.err').open('w') as err:subprocess.run(base+['org.kanger.DomainBindingWitness'],stdout=out,stderr=err,check=True)
assert 'DOMAIN_BINDING_WITNESS_OK scenarios=3 checks=21' in p.with_suffix('.log').read_text()
assert not p.with_suffix('.err').read_text()
print('WITNESSES_COMPLETE',flush=True)
