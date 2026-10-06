from pathlib import Path
import gzip
import json
import re
import subprocess

root = Path(__file__).parent
flags = ['preserveTValueIndex', 'versionedSolveSync', 'resolvedCauseWeights',
         'compactCauseWeights', 'candidateMembershipFilter', 'singleTValueLookup',
         'residentBaseComparison', 'compactFindSnapshots']
classes = '../build/current-empty-reference'
subprocess.run(['java', '-jar', '../tooling/ecj.jar', '-1.8', '-nowarn', '-cp', 'lib/jline-3.13.0.jar',
                '-d', classes, '@docs/resident-base-comparison-evidence/sources.txt'], check=True)
subprocess.run(['java', '-jar', '../tooling/ecj.jar', '-1.8', '-nowarn', '-cp', classes,
                '-d', classes, 'kanger-qualification/src/org/kanger/SonProfileRunner.java'], check=True)
reference = Path(classes)
diagnostic = Path('../build/current-empty-classes')
changed = sorted(str(p.relative_to(reference)) for p in reference.rglob('*.class')
                 if (diagnostic / p.relative_to(reference)).exists()
                 and p.read_bytes() != (diagnostic / p.relative_to(reference)).read_bytes())
assert changed == ['org/kanger/factory/TValueFactory.class', 'org/kanger/units/TVariable.class'], changed
(root / 'class-differences.json').write_text(json.dumps(changed, indent=2) + '\n')
for i, mode in enumerate(['reference', 'diagnostic', 'diagnostic'], 1):
    folder = classes if mode == 'reference' else str(diagnostic)
    driver = 'SonProfileRunner' if mode == 'reference' else 'CurrentEmptyProfileRunner'
    command = ['java', '-Xmx512m'] + ['-Dkanger.experiment.' + flag + '=true' for flag in flags]
    command += ['-Dbench.samples=6', '-cp', folder + ':kanger/resources:kanger-udf/src:lib/jline-3.13.0.jar', 'org.kanger.' + driver]
    prefix = root / ('run-%d-%s' % (i, mode))
    print('START', prefix, flush=True)
    with prefix.with_suffix('.log').open('w') as out, prefix.with_suffix('.err').open('w') as err:
        subprocess.run(command, stdout=out, stderr=err, check=True, timeout=300)
    data = prefix.with_suffix('.log').read_bytes()
    assert re.findall(rb'^SAMPLE (\d+) ', data, re.M) == [str(n).encode() for n in range(6)]
    assert not prefix.with_suffix('.err').read_bytes()
    prefix.with_suffix('.log.gz').write_bytes(gzip.compress(data, mtime=0))
    prefix.with_suffix('.log').unlink()
    print('DONE', prefix, flush=True)
