from pathlib import Path
import re

folder = Path(__file__).parent
raw, final = set(), set()
for index in (1, 2):
    log = folder / ('profile-%d.log' % index)
    content = log.read_text()
    assert not log.with_suffix('.err').read_text()
    assert len(re.findall(r'^SAMPLE \d+ .*raw=18 optimized=6 solutions=0 values=0$', content, re.M)) == 6
    raw.update(re.findall(r'^RAW .*$', content, re.M))
    final.update(re.findall(r'^OPTIMIZED .*$', content, re.M))
    rows = (folder / ('profile-%d.tsv' % index)).read_text().splitlines()
    header = rows[0]
    cpu = int(re.search(r'execution_samples=(\d+)', header).group(1))
    allocated = int(re.search(r'allocation_weight_bytes=(\d+)', header).group(1))
    assert 'phases=4' in header and 'truncated=0' in header and 'allocation_truncated=0' in header
    counts = {}
    for row in rows[1:]:
        label, count, name = row.split('\t', 2)
        counts[(label, name)] = int(count)
    print('PROFILE', index, 'warm_execution_samples', cpu, 'allocation_weight_MB', allocated / 1e6)
    for name in ['org.kanger.Mind.isUnitDeleted', 'org.kanger.primitives.ArgumentsList.equalsBase',
                 'org.kanger.units.Predicate.getName', 'org.kanger.units.TVariable.activeMind']:
        count = counts.get(('INCLUSIVE', name), 0)
        print('INCLUSIVE', name, count, 'sample_share=%.2f%%' % (100 * count / cpu))
    for name in ['org.kanger.units.Predicate.getName', 'org.kanger.storage.Escalera.find', 'org.kanger.storage.Escalera.findCandidates',
                 'org.kanger.units.TVariable.setMind']:
        count = sum(value for (label, site), value in counts.items()
                    if label == 'ALLOCATION_SITE_WEIGHT' and site.startswith(name + ':'))
        print('ALLOCATION_WEIGHT', name, count, 'weight_share=%.2f%%' % (100 * count / allocated))
    count = sum(value for (label, site), value in counts.items()
                if label == 'ROUTE' and site.startswith('CURRENT_LOOKUP\t'))
    print('CURRENT_LOOKUP_SAMPLES', count, 'sample_share=%.2f%%' % (100 * count / cpu))
control = Path('docs/resident-snapshots-evidence/snapshot-3-true.log').read_text()
assert raw == set(re.findall(r'^RAW .*$', control, re.M))
assert final == set(re.findall(r'^OPTIMIZED .*$', control, re.M))
assert len(raw) == len(final) == 1
print('PROFILE_HYPOTHESES_EQUAL samples=12 stderr=empty')
