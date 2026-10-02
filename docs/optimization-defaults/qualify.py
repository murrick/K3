"""Compare real inference/state behavior with unset, explicit ON and OFF flags."""
import argparse
import os
from pathlib import Path
import re
import subprocess

FLAGS = ['preserveTValueIndex', 'versionedSolveSync', 'resolvedCauseWeights',
         'compactCauseWeights', 'candidateMembershipFilter', 'singleTValueLookup',
         'residentBaseComparison']
parser = argparse.ArgumentParser()
parser.add_argument('--output', required=True)
parser.add_argument('--classpath', help='Direct Java invocation; otherwise use Maven exec')
args = parser.parse_args()
output = Path(args.output)
output.mkdir(parents=True, exist_ok=True)
snapshots = []
for mode in ['defaults', 'on', 'off']:
    env = os.environ.copy()
    env['JAVA_TOOL_OPTIONS'] = '-Xmx512m -Dbench.samples=2'
    if mode != 'defaults':
        env['JAVA_TOOL_OPTIONS'] += ''.join(' -Dkanger.experiment.' + flag + '=' +
                                           ('true' if mode == 'on' else 'false') for flag in FLAGS)
    runners = {'ResidentBaseComparisonRunner': 'RESIDENT_BASE_COMPARISON_OK checks=48 custom_reads=3',
               'SingleTValueLookupRunner': 'SINGLE_LOOKUP_BOUNDARIES_OK checks=15',
               'TValueIndexBoundaryRunner': 'TVALUE_INDEX_BOUNDARY_PASS',
               'TValueIndexReopenRunner': 'TVALUE_INDEX_REOPEN_PASS',
               'LatentSubstitutionCorpusRunner': 'LATENT_CORPUS_PASS',
               'LatentSolveSyncTransactionRunner': 'LATENT_SOLVE_TRANSACTIONS_PASS operations=20',
               'SonProfileRunner': 'SAMPLE 1 '}
    for runner, marker in runners.items():
        runner_args = []
        if runner == 'LatentSolveSyncTransactionRunner':
            runner_args = [str(output / (mode + '.state')), str(output / (mode + '.work'))]
        if args.classpath:
            command = ['java', '-cp', args.classpath, 'org.kanger.' + runner] + runner_args
        else:
            command = ['mvn', '--batch-mode', '--no-transfer-progress', '-f',
                       'kanger-qualification/pom.xml', '-Dexec.mainClass=org.kanger.' + runner,
                       '-Dexec.classpathScope=test', '-Dexec.args=' + ' '.join(runner_args), 'exec:java']
        log = output / (mode + '-' + runner + '.log')
        with log.open('w') as stream:
            subprocess.run(command, env=env, stdout=stream, stderr=subprocess.STDOUT, check=True)
        content = log.read_text()
        assert marker in content, log
        if runner == 'SonProfileRunner':
            raw = re.findall(r'^RAW .*$', content, re.M)
            optimized = re.findall(r'^OPTIMIZED .*$', content, re.M)
            assert len(raw) == len(optimized) == 2, log
            assert len(re.findall(r'raw=18 optimized=6 solutions=0 values=0', content)) == 2, log
            snapshots.append((raw, optimized))
        print('PASS', mode, runner, flush=True)
states = [(output / (mode + '.state')).read_bytes() for mode in ['defaults', 'on', 'off']]
assert states[0] == states[1] == states[2]
assert snapshots[0] == snapshots[1] == snapshots[2]
print('OPTIMIZATION_DEFAULTS_PASS modes=3 hypothesis_samples=6 transaction_states=equal')
