from pathlib import Path
import hashlib,json,subprocess
root=Path(__file__).parent
logs=[];hashes={}
for mode in ['reference','classes']:
    classes='../build/resolved-candidate-'+mode
    subprocess.run(['java','-jar','../tooling/ecj.jar','-1.8','-nowarn','-cp',classes,'-d',classes,str(root/'ResolvedCandidateWitness.java')],check=True,timeout=30)
    prefix=root/('witness-'+mode)
    with prefix.with_suffix('.log').open('w') as out,prefix.with_suffix('.err').open('w') as err:
        subprocess.run(['java','-Xmx512m','-cp',classes+':kanger/resources:kanger-udf/src:lib/jline-3.13.0.jar','org.kanger.ResolvedCandidateWitness'],stdout=out,stderr=err,check=True,timeout=30)
    assert not prefix.with_suffix('.err').read_bytes()
    logs.append(prefix.with_suffix('.log').read_bytes())
    hashes[mode]=hashlib.sha256(Path(classes+'/org/kanger/ResolvedCandidateWitness.class').read_bytes()).hexdigest()
assert logs[0]==logs[1]
assert len(set(hashes.values()))==1
(root/'witness-class-sha256.json').write_text(json.dumps(hashes,indent=2)+'\n')
print(logs[0].decode(),end='')
