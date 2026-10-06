from pathlib import Path
import gzip
import json
import re

root = Path(__file__).parent
expected = json.loads((root / 'expected.json').read_text())
observations = []
for i, mode in enumerate(['reference', 'diagnostic', 'diagnostic'], 1):
    text = gzip.decompress((root / ('run-%d-%s.log.gz' % (i, mode))).read_bytes()).decode()
    samples = re.findall(r'^SAMPLE (\d+) .* raw=18 optimized=6 solutions=0 values=0$', text, re.M)
    assert samples == [str(n) for n in range(6)]
    for label, value in expected.items():
        assert re.findall(r'^' + label + r' (.*)$', text, re.M) == [value] * 6
    if mode == 'diagnostic':
        variables = re.findall(r'^EMPTY_VARIABLE (\d+) (\[.*\])$', text, re.M)
        rows = re.findall(r'^EMPTY_ROW (\d+) (\d+) (\[.*\])$', text, re.M)
        assert [int(n) for n, _ in variables] == list(range(6))
        assert [(int(n), int(row)) for n, row, _ in rows] == [(n, row) for n in range(6) for row in range(4)]
        for n, value in variables:
            sample_rows = [json.loads(row) for sample, _, row in rows if sample == n]
            for row in sample_rows:
                assert row[0] == row[1] + row[2]
                assert row[0] >= row[3] >= row[4]
                assert row[5] >= row[6]
            variable = json.loads(value)
            assert variable[0] == variable[2] + variable[3]
            observations.append({'jvm': i, 'sample': int(n), 'variables': variable, 'rows': sample_rows})
warm = [row for row in observations if row['sample'] >= 2]
for n in range(6):
    left, right = [row for row in observations if row['sample'] == n]
    assert left['rows'] == right['rows'] and left['variables'] == right['variables']
assert all(row['variables'] == warm[0]['variables'] for row in observations)
for observation in observations:
    rows = observation['rows']
    assert rows[0] == [0] * 7
    assert rows[2][2] == rows[3][0] == rows[3][5]
    assert all(row[0] == row[3] for row in rows)
    assert all(row[6] == 0 for row in rows)
    assert rows[2][0] == rows[2][4] and rows[3][0] == rows[3][4]
totals = [sum(row[0] for row in observation['rows']) for observation in warm]
gets = [sum(row[5] for row in observation['rows']) for observation in warm]
duplicate = warm[0]['rows'][3][0]
summary = {'base': '1c943d02d39bd33ebd91fabb7c4190112ab49459',
           'snapshots_verified': 18, 'diagnostic_samples': 12, 'warm_samples': 8,
           'counts_identical_between_jvms_at_each_sample': True,
           'variable_counts_identical_all_samples': True,
           'warm_contains_range': [min(totals), max(totals)],
           'warm_map_get_range': [min(gets), max(gets)],
           'variable_duplicate_contains': duplicate,
           'variable_duplicate_share_of_contains_percent': [100 * duplicate / max(totals), 100 * duplicate / min(totals)],
           'potential_single_probe_variable_map_probes_removed': 2 * duplicate,
           'potential_share_of_map_probes_percent': [100 * 2 * duplicate / max(a+b for a,b in zip(totals,gets)),
                                                    100 * 2 * duplicate / min(a+b for a,b in zip(totals,gets))],
           'representative': warm[0], 'observations': observations,
           'scope': 'main-thread optimizeHypothesis only; instrumented timing excluded'}
(root / 'summary.json').write_text(json.dumps(summary, indent=2) + '\n')
print(json.dumps({key: value for key, value in summary.items() if key != 'observations'}, indent=2))
