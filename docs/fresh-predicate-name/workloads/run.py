from pathlib import Path
import gzip
import re
import subprocess

root = Path(__file__).parent
flags = ['preserveTValueIndex', 'versionedSolveSync', 'resolvedCauseWeights', 'compactCauseWeights',
         'candidateMembershipFilter', 'singleTValueLookup', 'residentBaseComparison', 'compactFindSnapshots']
for workload in ['son', 'set']:
    for sequence, mode in enumerate(['clean', 'ON', 'ON', 'clean'], 1):
        classes = '../build/fresh-name-reference' if mode == 'clean' else '../build/fresh-name-classes'
        command = ['java', '-Xmx512m'] + ['-Dkanger.experiment.' + f + '=true' for f in flags]
        if mode == 'ON': command += ['-Dkanger.experiment.freshPredicateName=true']
        command += ['-Dbench.samples=6', '-Dbench.workload=' + workload, '-cp',
                    '../build/fresh-name-workload-classes:' + classes + ':kanger/resources:kanger-udf/src:lib/jline-3.13.0.jar',
                    'org.kanger.test.FreshNameWorkloadRunner']
        prefix = root / ('%s-%d-%s' % (workload, sequence, mode))
        log, err = prefix.with_suffix('.log'), prefix.with_suffix('.err')
        with log.open('w') as out, err.open('w') as error:
            subprocess.run(command, stdout=out, stderr=error, check=True, timeout=300)
        content = log.read_bytes()
        assert not err.read_bytes(), prefix
        text = content.decode('utf-8')
        assert re.findall(r'^' + workload.upper() + r'_SAMPLE (\d+) ', text, re.M) == list(map(str, range(6))), prefix
        prefix.with_suffix('.log.gz').write_bytes(gzip.compress(content, mtime=0))
        log.unlink()  # Compression happens after the JVM exits, outside all intervals.
        print('DONE', workload, sequence, mode, flush=True)
