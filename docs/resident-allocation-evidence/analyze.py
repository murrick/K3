from pathlib import Path
import re
import statistics

folder = Path(__file__).parent
rows = []
raw, final = set(), set()
for index, mode in enumerate(['reference', '00', '10', '01', '11', '11', '01', '10', '00', 'reference'], 1):
    path = folder / ('combined-%d-%s.log' % (index, mode))
    content = path.read_text()
    samples = re.findall(r'^SAMPLE (\d+) query_ns=(\d+) optimize_ns=(\d+) optimize_cpu_ns=(\d+) optimize_allocated_bytes=(\d+) raw=18 optimized=6 solutions=0 values=0$', content, re.M)
    assert [s[0] for s in samples] == [str(i) for i in range(6)], path
    assert not path.with_suffix('.err').read_text(), path
    before = re.findall(r'^RAW .*$', content, re.M)
    after = re.findall(r'^OPTIMIZED .*$', content, re.M)
    assert len(before) == len(after) == 6, path
    raw.update(before); final.update(after)
    row = tuple(statistics.median(int(s[i]) for s in samples[2:]) / div
                for i, div in [(2, 1e9), (3, 1e9), (4, 1e6)])
    rows.append(row)
    print(path.name, 'wall_s=%.3f cpu_s=%.3f allocated_MB=%.1f' % row)
assert len(raw) == len(final) == 1
for label, off, on in [('name_forward', 1, 2), ('name_reverse', 8, 7),
                       ('snapshot_forward', 1, 3), ('snapshot_reverse', 8, 6),
                       ('combined_forward', 1, 4), ('combined_reverse', 8, 5),
                       ('combined_vs_reference_forward', 0, 4), ('combined_vs_reference_reverse', 9, 5),
                       ('snapshot_vs_combined_forward', 3, 4), ('snapshot_vs_combined_reverse', 6, 5)]:
    print(label, 'wall_reduction=%.2f%% cpu_reduction=%.2f%% allocation_reduction=%.2f%%' %
          tuple((1 - rows[on][i] / rows[off][i]) * 100 for i in range(3)))
print('RESIDENT_ALLOCATION_HYPOTHESES_EQUAL samples=60')
