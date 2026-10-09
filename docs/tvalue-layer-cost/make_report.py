from pathlib import Path
import json,hashlib
from statistics import median
p=Path(__file__).parent;s=json.loads((p/'summary.json').read_text());a=s['aggregated_metrics'];rows=[]
for size in (32,128,512):
 for count in (1,8,size):
  cells=[x for x in a if x['size']==size and x['buckets']==count]
  rows.append('| %d | %d | %.3f | %.3f | %.3f–%.3f |'%(size,count,median(x['memo_to_old_time'] for x in cells),median(x['memo_to_old_allocation'] for x in cells),min(x['time_ratio_min'] for x in cells),max(x['time_ratio_max'] for x in cells)))
Path('docs/tvalue-layer-cost.md').write_text("""# Paired bucket-reading cost

## Question and outcome

The preceding observation-scoped memo improved baseline construction, while sparse journal update timings varied between separate JVMs. This stage isolates the exact old and memoized bucket algorithms within each JVM and measures elapsed time and thread allocation. It does not alter either algorithm, the journal, native engine, optimization defaults or production sources.

The original bucket method builds the root Layer once for the target and again in recursive collect, for every bucket. ObservationLayers caches that qualified Layer by factory identity for one lexical observation. Its construction and close are included in measured memo time and allocation. Thus even one root bucket avoids one complete extraction; additional buckets reuse the same extraction.

## Protocol and correctness

81 sequential clean-native JVMs: sizes 32/128/512 × buckets 1/8/all × three repetitions × on/off/verify. Each has 48 warmup pairs and 64 measured pairs, alternating old-first and memo-first order. A real TValue setter alternates between two existing Terms before each pair. Timed batches are consumed through a volatile checksum. There is one value per variable, initialized native indexes, one root and no child or persistent storage. No mutation occurs during either timed batch. No concurrent benchmark processes run.

Before/after fingerprints, canonical value references/order, selected buckets against a freshly reconstructed independent full authority view, and all-variable final views are checked outside timed intervals. There are 5,184 measured pairs and 5,184 fresh full authority controls. All 81 JVMs passed with empty stderr and equal final views across sizes' flags, bucket counts and repetitions. A separate pre-measurement control asserts one extracted root Layer per memo batch. Inherited closed-frame and child-lifetime qualifications remain in the parent evidence; this stage does not rerun them or broaden storage qualification.

The environment had been pruned. The exact published parent was cloned and a standalone clean runtime rebuilt with ECJ 3.33.0, Java 17, target Java 8 and 512 MiB heap. 651 compiled inherited runtime/qualification classes match prior clean class hashes. Source and class hashes for the native compilation and exact inherited helper implementations are recorded in build-validation.json. No earlier committed evidence was regenerated or overwritten.

## Results

Numbers below aggregate the three flag cells; full 27-cell results and all per-JVM samples are retained. Lower ratios mean less time or allocation for the memo.

| Variables | Buckets | Memo / old time | Memo / old allocated bytes | Individual JVM time-ratio range |
|---|---|---:|---:|---:|
"""+'\n'.join(rows)+"""

Each JVM ratio is the median of its 64 paired sample ratios; each flag cell is the median of its three JVM ratios. The compact table takes medians across flags and ranges across their nine JVMs. Ratios of separately aggregated duration medians need not equal these values. Raw ns and allocated bytes, order-separated ratios and first/second-half ratios remain in the CSV evidence.

Allocation is measured by HotSpot ThreadMXBean thread-allocated bytes, not retained heap or RSS. It includes result lists and frame overhead, excluding correctness checks, full authority, fingerprinting, serialization and report writing. Elapsed times include any GC/JIT interruption during the interval. Alternating order and within-JVM pairing reduce inter-process confounding but do not make these stabilized JMH results. The fixed case order, JVM warmup and possible order effects remain limitations.

This supports reduced repeated Layer extraction in these direct memory bucket reads. It does not establish journal or inference throughput, total application allocation, persistent storage performance, uninitialized indexes, large buckets, child ancestry, concurrent reads, or a production default change. A complete observation still pays for any full oracle, comparison and reporting outside these measured batches. Complete corpus semantics remain unqualified and earlier rejected attempts remain retained.

## Evidence

All samples, native assertion logs, exact commands, analyses and hashes are under docs/tvalue-layer-cost/. Parent: 80ea72adbd17dac2324eb444cea0888938d01b77. The change is docs-only and is published on experiment/3.8.0-tvalue-layer-cost.
""")
files=[Path('docs/tvalue-layer-cost.md'),*[q for q in sorted(p.rglob('*')) if q.is_file() and q.name!='evidence-sha256.json']]
(p/'evidence-sha256.json').write_text(json.dumps({str(q):hashlib.sha256(q.read_bytes()).hexdigest() for q in files},indent=2)+'\n');print('LAYER_COST_REPORT_OK',len(files))
