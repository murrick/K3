# Indexed intersection of resolved candidates

`-Dkanger.experiment.indexedIntersection=true` enables a per-rotator, lazy Rule-ID
to bucket-position table. When resolved.size is smaller than the topology
bucket, selection probes resolved IDs, deduplicates/sorts retained positions,
and returns the original bucket's Rule references. Otherwise it uses the old
linear intersection. Tables are discarded with the rotator and do not cache
runtime resolved results.

The singleton-branch and nonempty-topology gates are unchanged.
findByResolvedDomain still executes exactly once at the original point, with
its existing semantic effects. Factory-verify compares the indexed intersection
with the old pure intersection using that same returned resolved list: no second
side-effecting lookup is made. Count, exact identity and order must agree.

## Observed work: six fixtures / 16 operations

| Counter | Linear intersection | Indexed intersection |
|---|---:|---:|
| Selection calls | 10737 | 10737 |
| Multidomain early returns | 8463 | 8463 |
| Empty-topology early returns | 4 | 4 |
| Returned resolved IDs | 4970 | 4970 |
| Linear bucket slots inspected | 12156 | 1094 |
| Retained candidates | 4896 | 4896 |
| Bucket slots indexed once | 0 | 2402 |
| Resolved-ID probes | 0 | 3804 |

The last three work categories have different costs. Their counts do not give
a speedup factor; map allocation and position sorting also cost time. Empty
resolved lists return before either intersection path, as in the reference.

For native `?$x male(x);`, linear slots fall from 7168 to 560, plus 1382 indexed
slots and 2297 probes. Both paths retain 2857 candidates from 2895 resolved IDs.

## Qualification

All 123 corpus cases and lifecycle checks pass with factoryCandidates and
indexedIntersection enabled in factory-verify. The complete 16-operation state
output matches the previous run after removing candidate-selection counters.
The replacement is therefore qualified for this corpus, not proven for all
possible runtime states.

Counters are query-local LinkerStatistics state with normal reset/copy/aggregate
behavior. Print them with `kanger.experiment.traceSelection=true`. Their array
order is the table order above. Instrumentation measures work counts, not CPU
time of individual selection stages.

## Timing observation and decision

Separate JVMs after qualification, three warmups and seven measured iterations,
factory mode and factoryCandidates enabled in both. Median milliseconds:

| Case | Linear compile | Indexed compile | Linear query | Indexed query |
|---|---:|---:|---:|---:|
| natives | 84.571 | 79.798 | 105.894 | 111.951 |
| 100 singleton facts | 17.253 | 14.662 | 23.578 | 21.522 |

Rows, domain-pair counts and unification counts match all paired samples. There
is no established general speedup: native query median is worse, singleton
median is better, and the sample is too small for a strong timing conclusion.
Keep the option disabled by default. This is a measured reduction of linear
intersection work, not a demonstrated end-to-end optimization.

The evidence suggests these small-corpus selection loops are not by themselves
enough to explain total query cost. Next profiling should attribute time or
allocations to selection versus unification, variable rotation, functions and
database effects before further tuning lookup details. Larger sparse/dense
active sets remain important and were not added in this checkpoint.

Raw completed state, corpus, lifecycle and benchmark outputs are under
`latent-substitution-evidence/intersection/`. An incomplete initial state capture
was discarded; the separately completed capture is the compared evidence.
Local environment remains ECJ targeting Java 8 on OpenJDK 17. Full Maven and
canonical Java 8/21 qualification remain outstanding.
