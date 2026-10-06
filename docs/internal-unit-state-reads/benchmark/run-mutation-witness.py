from pathlib import Path
import subprocess

root = Path(__file__).parent
classes = '../build/unit-state-boundary-classes'
subprocess.run(['java', '-jar', '../tooling/ecj.jar', '-1.8', '-nowarn',
                '-cp', '../build/unit-state-read-classes', '-d', classes,
                str(root / 'ClosedMapMutationWitness.java')], check=True)
for mode in ['false', 'true']:
    command = ['java', '-Dkanger.experiment.internalUnitStateReads=' + mode,
               '-cp', classes + ':../build/unit-state-read-classes:kanger/resources:kanger-udf/src:lib/jline-3.13.0.jar',
               'org.kanger.ClosedMapMutationWitness']
    out_path = root / ('mutation-witness-' + mode + '.log')
    err_path = root / ('mutation-witness-' + mode + '.err')
    with out_path.open('w') as out, err_path.open('w') as err:
        subprocess.run(command, stdout=out, stderr=err, check=True, timeout=60)
    assert out_path.read_text().strip() == 'CLOSED_MAP_MUTATION_WITNESS_OK scenarios=3 checks=24'
    assert not err_path.read_text()
    print('PASS mutation witness ' + mode)
