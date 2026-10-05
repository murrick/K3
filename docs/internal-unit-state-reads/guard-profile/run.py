from pathlib import Path
import subprocess, re, gzip

root = Path(__file__).parent
flags = ['preserveTValueIndex', 'versionedSolveSync', 'resolvedCauseWeights', 'compactCauseWeights', 'candidateMembershipFilter', 'singleTValueLookup', 'residentBaseComparison', 'compactFindSnapshots']
for runner, name, marker in [('DeletionVisibilityWitness', 'witness', 'EMPTY_DELETION_BOUNDARIES_OK checks=27'), ('RepeatedDeletionWitness', 'repeated-witness', 'REPEATED_DELETION_WITNESS_OK scenarios=2 checks=8'), ('ExposedSetDeletionWitness', 'exposed-set-witness', 'EXPOSED_SET_DELETION_WITNESS_OK scenarios=2 checks=11')]:
    prefix = root / name
    cmd = ['java', '-Xmx512m', '-cp', '../build/unit-state-guard-classes:kanger/resources:kanger-udf/src:lib/jline-3.13.0.jar', 'org.kanger.' + runner]
    with prefix.with_suffix('.log').open('w') as out, prefix.with_suffix('.err').open('w') as err:
        subprocess.run(cmd, stdout=out, stderr=err, check=True, timeout=60)
    assert marker in prefix.with_suffix('.log').read_text()
    assert not prefix.with_suffix('.err').read_text()
for n, mode in enumerate(['false','true','true','false'], 1):
    prefix = root / ('profile-' + str(n))
    cmd = ['java', '-Xmx512m', '-Dbench.samples=6', '-Dkanger.experiment.internalUnitStateReads='+mode] + ['-Dkanger.experiment.' + f + '=true' for f in flags]
    cmd += ['-cp', '../build/unit-state-guard-classes:kanger/resources:kanger-udf/src:lib/jline-3.13.0.jar', 'org.kanger.DeletionCallerProfileRunner']
    with prefix.with_suffix('.log').open('w') as out, prefix.with_suffix('.err').open('w') as err:
        subprocess.run(cmd, stdout=out, stderr=err, check=True, timeout=300)
    assert not prefix.with_suffix('.err').read_text()
    assert re.findall(r'^SAMPLE (\d+) ', prefix.with_suffix('.log').read_text(), re.M) == list(map(str, range(6)))
    prefix.with_suffix('.log.gz').write_bytes(gzip.compress(prefix.with_suffix('.log').read_bytes(), mtime=0))
    prefix.with_suffix('.log').unlink()
    print('PROFILE_COMPLETE', n, flush=True)
