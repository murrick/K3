from pathlib import Path
import gzip
import json
import re

root = Path(__file__).parent
expected = json.loads((root.parent / 'expected.json').read_text())
observations = {}
for mode in ['false', 'true']:
    text = gzip.decompress((root / ('probes-' + mode + '.log.gz')).read_bytes()).decode()
    assert re.findall(r'^SAMPLE (\d+) .* raw=18 optimized=6 solutions=0 values=0$', text, re.M) == list(map(str, range(6)))
    for label, value in expected.items():
        assert re.findall(r'^' + label + r' (.*)$', text, re.M) == [value] * 6
    variables = re.findall(r'^EMPTY_VARIABLE (\d+) (\[.*\])$', text, re.M)
    rows = re.findall(r'^EMPTY_ROW (\d+) (\d+) (\[.*\])$', text, re.M)
    assert [int(n) for n, _ in variables] == list(range(6))
    assert [(int(n), int(row)) for n, row, _ in rows] == [(n, row) for n in range(6) for row in range(4)]
    observations[mode] = [{'sample': int(n), 'variables': json.loads(value),
                           'rows': [json.loads(row) for sample, _, row in rows if sample == n]}
                          for n, value in variables]
for off, on in zip(observations['false'], observations['true']):
    assert off['sample'] == on['sample']
    assert off['variables'] == on['variables'] == [4911387, 0, 1307, 4910080]
    assert off['rows'][0] == on['rows'][0] == [0] * 7
    assert off['rows'][1] == on['rows'][1]
    assert off['rows'][2] == [4911387, 1307, 4910080, 4911387, 4911387, 0, 0]
    assert off['rows'][3] == [4910080, 0, 4910080, 4910080, 4910080, 4910080, 0]
    assert on['rows'][2] == [0, 0, 0, 0, 0, 4911387, 1307]
    assert on['rows'][3] == [0] * 7
    probes = lambda observation: sum(row[0] + row[5] for row in observation['rows'])
    assert probes(off) - probes(on) == 9820160
summary = {'snapshots_verified': 12, 'scope': 'diagnostic main-thread optimizeHypothesis only; timing excluded',
           'probes_removed_per_optimization': 9820160, 'outside_variable_counts_equal_at_every_sample': True,
           'observations': observations}
(root / 'summary.json').write_text(json.dumps(summary, indent=2) + '\n')
print('PROBE_CENSUS_OK snapshots=12 probes_removed_per_optimization=9820160')
