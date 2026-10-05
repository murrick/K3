from pathlib import Path
import json, re, gzip

root = Path(__file__).parent
expected = json.loads((root / 'expected.json').read_text())
warm = []
for n in [1, 2]:
    s = gzip.decompress((root / ('profile-' + str(n) + '.log.gz')).read_bytes()).decode()
    assert not (root / ('profile-' + str(n) + '.err')).read_text()
    assert re.findall(r'^SAMPLE (\d+) ', s, re.M) == list(map(str, range(6)))
    for key, value in expected.items():
        assert re.findall(r'^' + key + r' (.*)', s, re.M) == [value] * 6
    rows = re.findall(r'^HASH_PROBES sample=(\d+) calls=(\d+) small=(\d+) empty=(\d+) singleton=(\d+) multiple=(\d+) owners=(\d+) distinct=(\d+) hits=(\[.*?\])$', s, re.M)
    assert len(rows) == 6
    for row in rows:
        sample, calls, small, empty, singleton, multiple, owners, distinct = map(int, row[:8])
        hits = json.loads(row[8])
        assert calls == empty + singleton + multiple
        assert 0 <= small <= calls and len(hits) == 5
        assert all(0 <= hit <= calls - small for hit in hits)
        if sample >= 2:
            warm.append(dict(run=n, sample=sample, calls=calls, small=small, empty=empty, singleton=singleton, multiple=multiple, owners=owners, distinct=distinct, hits=hits))
    frequency_rows = re.findall(r'^HASH_FREQUENCIES sample=(\d+) values=\[(.*)\]$', s, re.M)
    assert len(frequency_rows) == 6
    for sample, values in frequency_rows:
        pairs = [(int(k), int(v)) for k, v in re.findall(r'(-?\d+)=(\d+)', values)]
        totals = rows[int(sample)]
        assert len(pairs) == int(totals[7]) and sum(v for k, v in pairs) == int(totals[1])
        assert sum(v for k, v in pairs if -128 <= k <= 127) == int(totals[2])
assert len(warm) == 8
comparison = [{k:v for k,v in row.items() if k != 'run'} for row in warm]
assert comparison[:4] == comparison[4:]
assert len({(row['calls'], row['small'], row['owners']) for row in warm}) == 1
summary = dict(base='1c943d02d39bd33ebd91fabb7c4190112ab49459', snapshots_verified=12, identical_warm_sample_pairs=4, sizes=[1,4,16,64,256], warm_rows=warm)
(root / 'summary.json').write_text(json.dumps(summary, indent=2) + '\n')
print(json.dumps(comparison[:4], indent=2))
for i, size in enumerate(summary['sizes']):
    values = [row['hits'][i] for row in warm]
    print('SIMULATED_KEY_CACHE', size, 'hits_range', [min(values), max(values)], 'percent_range', [round(100 * min(values) / warm[0]['calls'], 2), round(100 * max(values) / warm[0]['calls'], 2)], 'nominal_bytes_range_at_16_byte_Integer', [16 * min(values), 16 * max(values)])
