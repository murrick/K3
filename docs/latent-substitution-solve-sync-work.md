# Actual work inside solve-index synchronization

Parent `591b1c5`. This checkpoint adds optional counters only; no new skipping
condition or inference behavior. Enable `kanger.experiment.profileSolveSync`.

## What the counters measure

The reference scan traverses every ruleSolves group and checks its list length.
It visits tuple slots only from the previously indexed length onward. Therefore
many retained tuples do not automatically imply many repeated tuple visits.

Eight counters: ordinary group visits, newly visited tuple slots, sum of list
lengths seen at ordinary group visits, unchanged-length group visits, verifier
group visits, verifier newly visited slots, peak group count and peak total
list length in a completed reference scan. The list-length sum is **not** a
count of visited tuple objects; it is repeated snapshot size accounting.

Verification work is separate. Peaks include completed ordinary or verification
scans. Statistics reset/copy all counters; aggregation sums the first six and
takes maxima for the last two. Interrupted scans are not recorded. Default-off
profiling does not collect these counts or change candidate selection.

Benchmark CSV appends these fields, preceded by the existing four sync counts,
only with profileSolveSync. CSV is the last exported invocation (CHECKTRUE for
these queries). traceInvocations additionally reports each invocation separately.

## Four fixtures, exported CHECKTRUE

One fresh-Mind diagnostic sample per mode/fixture, no warmup: these are operation
counts, not performance estimates. Factory mode with factoryCandidates and
indexedIntersection enabled; versionedSolveSync OFF vs ON. Direct CSV file output
was checked for complete fixture/sample coverage.

| Fixture | Peak groups | Peak tuples | Ordinary group visits OFF -> ON | New tuple slots OFF = ON |
|---|---:|---:|---:|---:|
| natives | 30 | 184 | 181519 -> 413 | 184 |
| 100 facts | 1 | 100 | 300 -> 1 | 100 |
| 10 symmetric edges | 3 | 60 | 5146 -> 47 | 60 |
| 30 symmetric edges | 3 | 180 | 38686 -> 127 | 180 |

Reference unchanged-length visits are respectively 181463, 299, 5123 and 38623.
Versioned counts are 357, 0, 24 and 64. Changed-version scans still inspect
unchanged groups as well as changed groups; this prototype is not an append
queue and does not perform eager tuple indexing.

Native CHECKFALSE adds 62304 -> 306 group visits, with 178 newly visited slots
in both paths and peaks of 29 groups / 178 tuples. Together, the two native
checks visit groups 243823 -> 719 times and visit 362 new slots in their separate
indexes. The other three fixtures have no CHECKFALSE sync calls. These counts
are not distinct tuples shared across both query contexts.

The paired CSVs agree on semantic fingerprints, values-store row counts,
domain-pair counts, unification counts, new slots and both peaks.

## What this explains, and what it does not

Native and the 30-edge example have nearly the same peak tuple count (184 vs
180), but very different grouping (30 vs 3). Native CHECKTRUE performs about
4.7 times as many group visits as the larger symmetric example, despite fewer
sync requests. This supports the previous timing observation: native benefits
more from skipping unchanged group scans. The cost also depends on key width,
hashing and the rest of inference; these counters are not a causal timing proof.

Simply increasing facts/edges mostly grows tuples within a few groups. A better
next stress fixture varies the number of distinct TVariableSet groups separately
from tuples per group, then exercises publication/rollback contexts. Keep the
flag disabled by default pending that coverage; no new speed claim here.

## Verification and checkpoint

All 123 corpus cases pass with factory-verify, versionedSolveSync and the work
observer. All 16 actual-skipping state projections match the prior reference
stdout byte-for-byte. A separate verifying native sample reports 413 ordinary
group visits plus 181106 verifier-only visits, exactly the reference 181519;
the verifier visits **zero new tuple slots**. Facts similarly report 1 + 299
group visits and zero verifier slots. Thus verifier work is not being credited
as ordinary optimized work.

Evidence: `latent-substitution-evidence/solve-sync-work/`. One diagnostic stderr
capture omitted the reference 30-edge query's trace; authoritative CHECKTRUE
counts for every fixture are the complete direct-file CSVs. Stderr traces are
used only for the present native CHECKFALSE data, not to infer missing records.
No incomplete capture is treated as zero work.

OpenJDK 17.0.20, ECJ 3.33 Java 8 target. Full Maven / canonical Java 8 and 21
qualification remains outstanding. Live develop stays at
`3ad50f1e5253304f6b530de11c078f332ea4db89`; no merge/release/tag/deploy.
