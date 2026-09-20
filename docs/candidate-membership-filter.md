# Candidate membership filter

Opt-in extraction from experiment/3.8.0-post-cause-profile at 5068dda onto
develop/3.8.0 at 9d74bb06c93dc27d416eee16fcffb238be577c87.

RuleCandidateIndex.collectResolvedLocal currently copies and unions exact,
wildcard and fallback ID buckets for each constrained argument position. The
alternative looks up those three buckets once per position and tests each ID
in the independently owned selected snapshot. It preserves the order of that
LinkedHashSet and reuses each Long ID without unboxing/reboxing.

All bucket reads remain inside the existing metadata write-lock region. Bucket
references are local, never returned or retained, and selection never mutates
stored buckets. The initial signature snapshot, returned result ownership,
resolution outside the lock, hydration/batching effects, journals and versioning
retain their existing behavior. No new cache or persistence format is introduced.
collectLocal and Linker are unchanged.

## Modes

Both properties default to false. Enable with:

```text
-Dkanger.experiment.candidateMembershipFilter=true
```

Add the following to compare ordered candidate IDs against the original
copy/union algorithm after every constrained argument position:

```text
-Dkanger.experiment.verifyCandidateMembershipFilter=true
```

Verification requires the first flag too. Leave verification OFF when measuring
performance. The original algorithm remains available with both flags OFF.

## Evidence

Source executable fce6fd52de2ffaefbb1cd5853a5893b9ccab8020 passed
[Java 8/21 qualification](https://github.com/murrick/K3/actions/runs/35442067771):
four jobs crossing JVM version with compact cause weighting OFF/ON. Each runs
membership OFF/ON/verify through the existing 123-case corpus and 20-operation
transaction fixture with byte-identical state comparisons, 39 ordered boundary
checks and three persistent-DUMB four-thread verification scenarios. TValue
preservation and versioned solve sync are ON in this dedicated matrix.

Boundaries cover empty/exact/wildcard/fallback results, positional intersection,
insertion order, caller-result mutation, nested journal commit/release, independent
child-index merge, unindex/clear and IDs 0/Long.MAX_VALUE. The boundary fixture
isolates positional selection; end-to-end fixtures exercise batching and runtime
effects. The isolated branch must pass its own CI before integration.

Earlier source-experiment Java 17 measurements: warmed set_08_02 whole-method
medians improved 5.7% and 6.9% in opposite OFF/ON orders. Separate main-thread
cumulative allocation medians fell 921,174,616 -> 739,126,200 bytes (19.8%). These
are not retained heap, worker allocations, cold-start guarantees or Java 8/21
performance measurements. Both modes had TValue preservation, versioned solve
sync and compact cause weighting ON. Methodology and raw evidence remain in
docs/post-cause-profile.md and docs/post-cause-evidence/ on the source branch.

This extraction changes one production file and carries its boundary runner,
CI matrix and this document. Profiling helpers and raw experimental data remain
on the experimental branch. No merge or default activation is included.
