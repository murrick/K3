# Empty deletion layer shortcut

**Disposition: retain as a negative experiment; do not integrate.** The
larger repeat below did not reproduce the initial apparent gain. Candidate
source remains default OFF in the isolated experimental branch for archaeology.

Experimental base: `develop/3.8.0` at
`1828d7164159cb04df00dedd5c27e18d556af4ea`. Java 17.0.20, ECJ Java 8 target,
`-Xmx512m`. All six previously integrated optimizations ON; baseValueLocals
and directCurrentLookup OFF throughout this experiment.

## Measurement

Single-thread diagnostic counters covered compilation and two complete son
queries/hypothesis optimizations. They observed 95,032,377 visited deletion
visibility layers; all 95,032,377 had both restored/deleted maps empty.
Both diagnostic hypothesis snapshots match the baseline. Counters add work;
their timings are not performance evidence. This fixture contains no deletion
markers; the separate qualification deliberately exercises nonempty maps.

## Production candidate

`kanger.experiment.emptyDeletionLayerShortcut`, default OFF. Inside each
existing synchronized layer, skip the two keyed map lookups if both maps are
empty. Continue to the parent under the original loop; no chain caching,
metadata maintenance, new allocation, manager, or persistence change.
Nonempty layers retain restoration-first precedence and the original lookups.
The original unit type/id reads and monitor acquisition/release remain.
Exposed getDeleted/getRestored maps are still authoritative on every call.

## Sequential performance

Fresh JVMs OFF/ON/ON/OFF, three samples each, first excluded from warm median.
No sampling or competing benchmark. All 12 raw and optimized snapshots match;
unknown result, zero solutions/values, 18 raw and six final hypotheses.

| Order | Mode | First optimize (s) | Warm optimize median (s) |
|---|---|---:|---:|
| 1 | OFF | 12.381 | 11.190 |
| 2 | ON | 14.696 | 10.953 |
| 3 | ON | 14.612 | 10.104 |
| 4 | OFF | 13.004 | 10.832 |

Warm paired reductions: 2.1% and 6.7%. Both first ON samples were slower than
paired OFF. Two warm samples and noisy environment limit confidence; this is
an exploratory small gain, not an established universal or cold-start speedup.
Do not merge or enable by default based on these timings alone.

## Qualification

EmptyDeletionLayerRunner checks 27 boundaries per mode, including empty
ancestors above a deletion, nearest deletion/restoration, both markers in one
layer, direct map insert/remove/clear, empty/null sets, unrelated IDs and the
setUnitDeleted API. Existing regression corpus and 20 transaction operations
are also run in OFF/ON; mandatory final markers are required.
All checks passed in both modes, with byte-identical full transaction state
projections. This was repeated after removing the earlier comparison prototype
and freshly recompiling the branch's production sources.

Raw timing/diagnostic/qualification logs and scripts are in
`base-operands-evidence/`. The earlier baseValueLocals candidate was disabled
for every deletion run and subsequently archived; only Mind's seven-line
guarded change remains as the active production delta. Cross-JDK results
follow below; aggregate substitution-pair traces were not collected.

## Cross-JDK qualification and larger repeat

Production/qualification commit `611e5856d8b3d3eba05a3d921826e81032250e11`
passed all three triggered workflows: KANGER CI, qualification isolation,
and the dedicated empty deletion layer matrix. The dedicated matrix has
six successful jobs: Java 8/21/26 crossed with all six prior optimizations
OFF/ON. Each job runs this candidate OFF and ON, checks the mandatory corpus,
27 boundary and 20 transaction markers, compares transaction states, checks
partial mark/commit/rollback failure isolation, and runs three concurrency
iterations with the candidate enabled.

Dedicated run: https://github.com/murrick/K3/actions/runs/36856886081

Larger local repeat: six fresh JVMs, OFF/ON/ON/OFF/OFF/ON, four samples each.
First excluded, median of three warm samples per JVM. Same Java 17.0.20,
512 MB heap and all six prior flags ON. No concurrent local benchmark.

| Order | Mode | First optimize (s) | Warm optimize median (s) |
|---|---|---:|---:|
| 1 | OFF | 12.267 | 10.562 |
| 2 | ON | 12.702 | 10.716 |
| 3 | ON | 11.530 | 10.507 |
| 4 | OFF | 14.116 | 10.214 |
| 5 | OFF | 13.371 | 10.664 |
| 6 | ON | 13.901 | 10.748 |

ON is slower in all three warm pairings: 1.46%, 2.87%, 0.78%. These numbers
do not establish universal regression under every JVM/workload, but they
fail to reproduce the initial improvement and do not justify integration.
All 24 raw/final textual hypothesis snapshots match, with unknown result,
zero solutions/values and empty stderr. Machine-readable data and assertions
are in deletion-repeat-summary.json and analyze-deletion-repeat.py.

The shortcut still traverses all ancestors and acquires all original monitors.
Two avoided empty-map lookups alone have not delivered a repeatable gain.
Do not spend further qualification or manual laptop testing on this variant
without new evidence. Further profiling should examine repeated collection
construction/traversal in the replay path rather than promote this shortcut.
