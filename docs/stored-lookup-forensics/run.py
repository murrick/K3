from pathlib import Path
import gzip
import re
import subprocess

root = Path(__file__).parent
flags = ['preserveTValueIndex', 'versionedSolveSync', 'resolvedCauseWeights', 'compactCauseWeights',
         'candidateMembershipFilter', 'singleTValueLookup', 'residentBaseComparison', 'compactFindSnapshots']
for sequence in [1, 2]:
    command = ['java', '-Xmx512m'] + ['-Dkanger.experiment.'+flag+'=true' for flag in flags]
    command += ['-Dbench.samples=6', '-cp', '../build/stored-lookup-classes:kanger/resources:kanger-udf/src:lib/jline-3.13.0.jar',
                'org.kanger.StoredLookupProfileRunner']
    prefix = root / ('profile-%d'%sequence)
    with prefix.with_suffix('.log').open('w') as out, prefix.with_suffix('.err').open('w') as err:
        subprocess.run(command, stdout=out, stderr=err, check=True, timeout=300)
    data = prefix.with_suffix('.log').read_bytes()
    assert re.findall(rb'^SAMPLE (\d+) ', data, re.M) == [str(n).encode() for n in range(6)]
    assert not prefix.with_suffix('.err').read_bytes()
    prefix.with_suffix('.log.gz').write_bytes(gzip.compress(data, mtime=0))
    prefix.with_suffix('.log').unlink()
    print('DONE',sequence,flush=True)
