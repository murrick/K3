from pathlib import Path
import subprocess, re, gzip

root = Path(__file__).parent
flags = ['preserveTValueIndex', 'versionedSolveSync', 'resolvedCauseWeights', 'compactCauseWeights', 'candidateMembershipFilter', 'singleTValueLookup', 'residentBaseComparison', 'compactFindSnapshots']
for n in [1, 2]:
    prefix = root / ('profile-' + str(n))
    cmd = ['java', '-Xmx512m', '-Dbench.samples=6'] + ['-Dkanger.experiment.' + f + '=true' for f in flags]
    cmd += ['-cp', '../build/hash-probe-classes:kanger/resources:kanger-udf/src:lib/jline-3.13.0.jar', 'org.kanger.HashProbeProfileRunner']
    with prefix.with_suffix('.log').open('w') as out, prefix.with_suffix('.err').open('w') as err:
        subprocess.run(cmd, stdout=out, stderr=err, check=True, timeout=300)
    assert not prefix.with_suffix('.err').read_text()
    assert re.findall(r'^SAMPLE (\d+) ', prefix.with_suffix('.log').read_text(), re.M) == list(map(str, range(6)))
    prefix.with_suffix('.log.gz').write_bytes(gzip.compress(prefix.with_suffix('.log').read_bytes(), mtime=0))
    prefix.with_suffix('.log').unlink()
    print('PROFILE_COMPLETE', n, flush=True)
