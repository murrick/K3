from pathlib import Path
import gzip, json, re

root = Path(__file__).parent
expected = json.loads((root / 'expected.json').read_text())
warm = []
for run in [1, 2]:
    s = gzip.decompress((root / ('profile-' + str(run) + '.log.gz')).read_bytes()).decode()
    assert not (root / ('profile-' + str(run) + '.err')).read_text()
    assert re.findall(r'^SAMPLE (\d+) ', s, re.M) == list(map(str, range(6)))
    for key, value in expected.items():
        assert re.findall(r'^' + key + r' (.*)', s, re.M) == [value] * 6
    maximum = re.findall(r'^DELETION_MAX_DEPTH sample=(\d+) value=(\d+)$', s, re.M)
    assert len(maximum) == 6 and all(int(depth) < 63 for sample, depth in maximum)
    parsed = re.findall(r'^DELETION_CHAIN sample=(\d+) type=(\w+) totals=(\[.*?\]) depths=(\[.*?\])$', s, re.M)
    for sample in range(6):
        groups = {}
        for number, type, totals, depths in parsed:
            if int(number) != sample: continue
            t, d = json.loads(totals), json.loads(depths)
            assert len(t) == 8 and len(d) == 64 and not d[0] and not d[-1]
            assert t[0] == sum(d) == t[5] + t[6] + t[7]
            assert t[1] == sum(i * value for i, value in enumerate(d))
            assert t[3] == t[1] - t[5]
            assert t[2] <= t[1] and t[4] <= t[3] and t[5] <= t[2] and t[6] <= t[4]
            assert type not in groups
            groups[type] = dict(calls=t[0], levels=t[1], restored_set_present=t[2], deleted_probes=t[3], deleted_set_present=t[4], restored_hits=t[5], deleted_hits=t[6], not_found=t[7], depths={i:v for i,v in enumerate(d) if v})
        assert groups
        if sample >= 2: warm.append(dict(run=run, sample=sample, max_depth=int(maximum[sample][1]), groups=groups))
assert len(warm) == 8
assert [{k:v for k,v in row.items() if k != 'run'} for row in warm[:4]] == [{k:v for k,v in row.items() if k != 'run'} for row in warm[4:]]
assert all(row['groups'] == warm[0]['groups'] and row['max_depth'] == warm[0]['max_depth'] for row in warm)
rows = []
for row in warm:
    groups = row['groups']
    calls = sum(g['calls'] for g in groups.values())
    levels = sum(g['levels'] for g in groups.values())
    depths = {i:sum(g['depths'].get(i,0) for g in groups.values()) for i in range(1,row['max_depth']+1)}
    rows.append(dict(run=row['run'],sample=row['sample'],calls=calls,levels=levels,ancestor_levels=levels-calls,average_depth=levels/calls,depths=depths,restored_probes=levels,deleted_probes=sum(g['deleted_probes'] for g in groups.values()),restored_set_present=sum(g['restored_set_present'] for g in groups.values()),deleted_set_present=sum(g['deleted_set_present'] for g in groups.values()),restored_hits=sum(g['restored_hits'] for g in groups.values()),deleted_hits=sum(g['deleted_hits'] for g in groups.values())))
assert 'EMPTY_DELETION_BOUNDARIES_OK checks=27' in (root/'witness.log').read_text()
assert 'REPEATED_DELETION_WITNESS_OK scenarios=2 checks=8' in (root/'repeated-witness.log').read_text()
assert not (root/'witness.err').read_text() and not (root/'repeated-witness.err').read_text()
summary = dict(base='1c943d02d39bd33ebd91fabb7c4190112ab49459',snapshots_verified=12,matching_warm_sample_pairs=4,identical_warm_counters=8,warm_rows=warm,aggregates=rows)
(root/'summary.json').write_text(json.dumps(summary,indent=2)+'\n')
print(json.dumps(rows[:4],indent=2))
for type in warm[0]['groups']:
    values=[row['groups'][type] for row in warm]
    print(type,'calls_range',[min(v['calls'] for v in values),max(v['calls'] for v in values)],'levels_range',[min(v['levels'] for v in values),max(v['levels'] for v in values)])
