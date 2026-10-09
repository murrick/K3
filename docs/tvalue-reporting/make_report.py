from pathlib import Path
import json,hashlib
from statistics import median
p=Path(__file__).parent;s=json.loads((p/'summary.json').read_text());a=s['aggregated_metrics'];rows=[]
for size in (32,128,512):
 for dirty in (1,8):
  c=[x for x in a if (x['size'],x['buckets'])==(size,dirty)]
  rows.append('| %d | %d | %.3f | %.3f | %.3f |'%(size,dirty,median(x['wall_ratio'] for x in c),median(x['allocation_ratio'] for x in c),median(x['reporting_ratio'] for x in c)))
Path('docs/tvalue-reporting.md').write_text("""# Equivalent journal reporting with fewer temporary objects

## Result

This stage changes only full-view encoding and change-key enumeration in the memoized diagnostic journal. Projection, qualification, independent fresh full authority and full equality checks remain unchanged. Trace content and ordering remain exact.

Thread allocation per complete observation fell by about 5–8% across the size/dirty/flag cells. Reporting phase times improved in the aggregated scenarios. Whole observation time moved little: cell ratios span 0.938–1.027, while individual JVM medians span 0.881–1.086. Some cells are slower. This is an allocation reduction with a modest, noisy time result, not an established overall inference speedup.

## Change

The old encoder builds a list of one temporary string per variable and joins it. The new encoder appends identical content to one StringBuilder, preserving braces, separators, numeric keys and value strings, including an empty map.

The old change reporter builds a TreeSet union of all previous/current keys, then compares values. The new private changedKeys helper merges two natural-order map iterators and returns only the differing keys in the same ascending order. Existing CHANGE formatting remains untouched, including absent-key defaults of [] and all before/after values. Both actual journal views are privately constructed natural-order TreeMaps; arbitrary caller comparators are outside this helper's admitted scope. The algorithm still examines the complete maps and does not use the dirty set to hide a change.

Full VIEW rows are still serialized after every successful observation. No trace schema, oracle sampling, validation frequency, reset/seed behavior, dirty routing, runtime hook or production default changes.

The reference source is an exact class rename of the preceding memo journal. Reversing the two declared reporting edits and class rename recovers that source exactly. The benchmark runner is also recovered by class rename only, retaining the same native setter and explicit metadata-bridge protocol. The exact inherited clean runtime class hashes remain verified.

## Validation

57 final JVMs passed without preliminary fixture failures: 54 timing and three scope runs, all with empty stderr. There are 3,456 measured paired updates, 114 successful traces and 7,188 replayed authority views. Both traces in every timing pair are byte-identical to the preceding stage's memo trace for that label. Scope traces are identical within each pair.

Each of the three scope JVMs compares changed-key lists and encodings on 1,028 deterministic natural-order map pairs: empty, inserted, removed, changed, interleaved, extreme long keys and explicit [] values, with input-map preservation. These pure map controls supplement real native-memory transition controls; they are not substituted for them.

Real transitions cover an empty baseline, insertion of low/high keys, an unchanged observation, a checked native term setter, context deletion/restoration, removal of all keys, a new key after clear and explicit reset. Clear is a direct comparison model: actual native factory clear followed by explicit invalidation of both known routes. This does not qualify the native clear hook or storage integration.

Three negative scenarios per scope JVM change state without notifying either journal: native setter, native add and native clear. Both full oracles refuse every gap with identical complete error messages. Thus the new reporting helpers retain mismatch detection and error formatting. Before/after native fingerprints remain equal around the observers.

## Paired cost

The inherited protocol uses 32/128/512 variables with one value each, one/eight dirty buckets, three repetitions and on/off/verify. Each JVM has 48 warmup pairs and 64 measured pairs, alternating reference-first/candidate-first order. All JVMs run sequentially, without a concurrent benchmark workload.

The compact table aggregates medians across the three flag cells; lower ratios favor the new reporter. Full 18-cell and per-JVM data, raw wall/allocation/phase samples, order-separated and first/second-half ratios are retained.

| Variables | Dirty buckets | New / previous whole time | New / previous allocation | New / previous reporting time |
|---|---|---:|---:|---:|
"""+'\n'.join(rows)+"""

Each JVM ratio is the median of 64 paired sample ratios and each cell takes the median of three JVM ratios. Aggregated duration medians are separate and need not reproduce paired ratios. The unchanged oracle's cell ratios span 0.968–1.084, illustrating variation on the scale of the small whole-time difference. This finite-warmup, fixed-order JVM benchmark includes GC/JIT effects and shared allocation pressure; it is not stabilized JMH or application throughput.

Whole intervals include observe and phase-profile append. Native setter and explicit metadata notifications, fingerprints, construction, baseline/finish wall time, file I/O and independent replay remain outside them. ThreadMXBean reports allocated bytes, not retained heap or RSS. No hook-integrated, persistent, child-context, concurrent or end-to-end inference performance is qualified. Complete corpus semantics remain unqualified and earlier rejected attempts remain retained in parent history.

## Evidence

Declared edits, source recovery, exact commands, all logs/traces/errors, analyses, samples and hashes are under docs/tvalue-reporting/. Parent: 06360fbd7b2920a1b926bd5e704015f5523823f6. Branch: experiment/3.8.0-tvalue-reporting. Production sources, defaults and develop are unchanged.
""")
files=[Path('docs/tvalue-reporting.md'),*[q for q in sorted(p.rglob('*')) if q.is_file() and q.name!='evidence-sha256.json']]
(p/'evidence-sha256.json').write_text(json.dumps({str(q):hashlib.sha256(q.read_bytes()).hexdigest() for q in files},indent=2)+'\n');print('REPORTING_REPORT_OK',len(files))
