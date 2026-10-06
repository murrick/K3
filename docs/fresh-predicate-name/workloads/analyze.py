from pathlib import Path
import gzip
import json
import re
import statistics

root = Path(__file__).parent
oracle = json.loads((root.parent / 'expected.json').read_text())
expected_pairs = sorted([str(x) + ':' + str(group * 1000 + x) for x in range(164) for group in range(2, 5)] + ['3:1003'])
expected_set = 'SET_ROWS [' + ', '.join(expected_pairs) + ']'
alternate_pairs = sorted([str(x) + ':' + str(group * 1000 + x) for x in range(164) for group in [1, 3, 4]] + ['2:7002'])
alternate_set = 'SET_ROWS [' + ', '.join(alternate_pairs) + ']'
assert len(expected_pairs) == len(alternate_pairs) == 493
output = {'samples_per_jvm': 6, 'discard_first': 2, 'workloads': {}}
for workload in ['son', 'set']:
    rows = []
    for sequence, mode in enumerate(['clean', 'ON', 'ON', 'clean'], 1):
        prefix = root / ('%s-%d-%s' % (workload, sequence, mode))
        text = gzip.decompress(prefix.with_suffix('.log.gz').read_bytes()).decode('utf-8')
        assert not prefix.with_suffix('.err').read_bytes(), prefix
        pattern = (r'^SON_SAMPLE (\d+) wall_ns=(\d+) main_cpu_ns=(\d+) process_cpu_ns=(\d+) main_allocated_bytes=(\d+) raw=18 optimized=6 solutions=0 values=0$'
                   if workload == 'son' else
                   r'^SET_SAMPLE (\d+) wall_ns=(\d+) main_cpu_ns=(\d+) process_cpu_ns=(\d+) solutions=493 values=493 hypotheses=0$')
        samples = re.findall(pattern, text, re.M)
        assert [int(r[0]) for r in samples] == list(range(6)), prefix
        if workload == 'son':
            final = re.findall(r'^FINAL .*$', text, re.M)
            stats = re.findall(r'^STATS .*$', text, re.M)
            assert len(final) == len(stats) == 6, prefix
            assert set(final) == {'FINAL ' + oracle['OPTIMIZED']}, prefix
            assert set(stats) == {'STATS ' + oracle['POST_OPTIMIZE_LAST_LINKER_STATS']}, prefix
        else:
            pairs = re.findall(r'^SET_ROWS .*$', text, re.M)
            assert len(pairs) == 6 and set(pairs).issubset({expected_set, alternate_set}), prefix
            outcomes = re.findall(r'^SET_OUTCOME (WRITER_[12]_ROLLBACK)$', text, re.M)
            assert len(outcomes) == 6, prefix
            assert all((outcome == 'WRITER_1_ROLLBACK') == (pair == expected_set) for outcome, pair in zip(outcomes, pairs)), prefix
        row = {'sequence': sequence, 'mode': mode, 'wall_s': statistics.median(int(r[1]) for r in samples[2:]) / 1e9,
               'process_cpu_s': statistics.median(int(r[3]) for r in samples[2:]) / 1e9}
        if workload == 'son':
            row['main_cpu_s'] = statistics.median(int(r[2]) for r in samples[2:]) / 1e9
            row['main_allocated_MB'] = statistics.median(int(r[4]) for r in samples[2:]) / 1e6
        if workload == 'set':
            row['outcome_counts'] = {label: outcomes.count(label) for label in ['WRITER_1_ROLLBACK','WRITER_2_ROLLBACK']}
            row['warm_outcome_counts'] = {label: outcomes[2:].count(label) for label in ['WRITER_1_ROLLBACK','WRITER_2_ROLLBACK']}
        rows.append(row)
        print(workload, sequence, mode, json.dumps(row, sort_keys=True))
    comparisons = []
    for label, clean, on in [('forward', 0, 1), ('reverse', 3, 2)]:
        fields = ['wall_s', 'process_cpu_s'] + (['main_cpu_s', 'main_allocated_MB'] if workload == 'son' else [])
        reduction = {field: (1 - rows[on][field] / rows[clean][field]) * 100 for field in fields}
        comparisons.append({'pair': label, 'reduction_percent': reduction})
        print(workload, label, 'reduction_percent', json.dumps(reduction, sort_keys=True))
    output['workloads'][workload] = {'rows': rows, 'comparisons': comparisons, 'samples_verified': 24}
assert not (root / 'progress.err').read_bytes()
(root / 'summary.json').write_text(json.dumps(output, indent=2) + '\n')
print('VERIFIED son_final_snapshots=24 set_exact_493_row_snapshots=24')
