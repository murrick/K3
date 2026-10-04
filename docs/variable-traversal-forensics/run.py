from pathlib import Path
import subprocess, re
root=Path(__file__).parent
for run in [1,2]:
    p=root/('profile-%d'%run)
    with p.with_suffix('.log').open('w') as out,p.with_suffix('.err').open('w') as err:
        subprocess.run(['java','-Xmx512m','-Dbench.samples=6','-cp','../build/variable-traversal-classes:kanger/resources:kanger-udf/src:lib/jline-3.13.0.jar','org.kanger.VariableTraversalProfileRunner'],stdout=out,stderr=err,check=True)
    s=p.with_suffix('.log').read_text()
    assert re.findall(r'^SAMPLE (\d+) ',s,re.M)==[str(i) for i in range(6)]
    assert re.findall(r'^VARIABLE_OUTSIDE_DELETION (\d+) ',s,re.M)==[str(i) for i in range(6)]
    assert not p.with_suffix('.err').read_text()
    print('DONE',p,flush=True)
