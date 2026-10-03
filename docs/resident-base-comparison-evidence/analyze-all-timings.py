from pathlib import Path
import re

folder = Path(__file__).parent
logs = sorted(folder.glob('resident-base-comparison-*.log'))
logs += sorted(folder.glob('three-way-*.log'))
assert len(logs) == 10
raw, final = set(), set()
total = 0
for path in logs:
    content = path.read_text()
    samples = re.findall(
        r'^SAMPLE (\d+) query_ns=\d+ optimize_ns=\d+ optimize_cpu_ns=\d+ '
        r'optimize_allocated_bytes=\d+ raw=18 optimized=6 solutions=0 values=0$',
        content, re.M)
    assert samples == [str(i) for i in range(6)], path
    assert not path.with_suffix('.err').read_text(), path
    raw_lines = re.findall(r'^RAW .*$', content, re.M)
    final_lines = re.findall(r'^OPTIMIZED .*$', content, re.M)
    assert len(raw_lines) == len(final_lines) == 6, path
    raw.update(raw_lines)
    final.update(final_lines)
    total += len(samples)
assert total == 60 and len(raw) == len(final) == 1
print('ALL_TIMING_HYPOTHESES_EQUAL samples=60 jvms=10 stderr=empty')
