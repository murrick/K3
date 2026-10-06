from pathlib import Path
import subprocess
root=Path(__file__).parent
for mode in ['reference','classes']:
    classes='../build/hypothesis-phase-'+mode
    runner='KangerCompletedHypothesisContractRunner'
    subprocess.run(['java','-jar','../tooling/ecj.jar','-1.8','-nowarn','-cp',classes+':lib/jline-3.13.0.jar','-d',classes,'kanger-qualification/src/org/kanger/'+runner+'.java'],check=True,timeout=30)
    prefix=root/('contract-'+mode)
    with prefix.with_suffix('.log').open('w') as out,prefix.with_suffix('.err').open('w') as err:
        subprocess.run(['java','-Xmx512m','-Duser.home='+str(Path('../build/hypothesis-phase-home-'+mode).resolve()),'-cp',classes+':kanger/resources:kanger-udf/src:lib/jline-3.13.0.jar','org.kanger.'+runner],stdout=out,stderr=err,check=True,timeout=300)
    assert not prefix.with_suffix('.err').read_bytes()
    assert 'COMPLETED_HYPOTHESIS_CONTRACT_OK' in prefix.with_suffix('.log').read_text()
    print('CONTRACT_OK',mode,flush=True)
assert (root/'contract-reference.log').read_bytes()==(root/'contract-classes.log').read_bytes()
