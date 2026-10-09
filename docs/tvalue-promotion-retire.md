# TValue promotion, child completion and retirement

Experiment parent: `383145d2a6bb2e3d3fe37b8371b4edf03e7942ea`.
Native development baseline: `5d5f6aff271f1abf371fbde55d637744c53f0bc3`, freshly compiled by the preceding SMART-base qualification.

This docs-only fixture exercises native typed `TValueFactory.commit(childFactory)`, child factory clear, native `Mind.release(child)` and explicit diagnostic retirement. The accepted old/stream diagnostic implementation and native engine sources remain unchanged.

## Result

In six child cycles, three typed factory promotions preserve canonical TValue identity and native substitution order while changing the promoted units' owner ID to the parent. Three child clears discard the unpromoted local additions and rebase the child to current parent membership. Direct native ID lookup confirms discarded IDs do not appear in the parent later.

An older sibling retains its captured original membership after promotion; a newly constructed child sees the current promoted parent values. Conservative promotion notifications do not manufacture changes in that older sibling. A later promoted-value term rewrite changes only the live parent's view.

Native child release consumes its reservation. After diagnostic retirement, calls to observe/reset/touch for that child create no rows or reads; subsequent promotion and metadata fanout exclude it, and finish scans only the remaining live parent. Retirement during an unfinished journal checkpoint or settlement is rejected even when that boundary is subsequently balanced.

Releasing the final sibling reaches native root quiescence and invokes `pack()`/update. This artificial fixture has no active rules supporting its TValue terms, so pack removes all 20 remaining values. Explicit root-pack notifications produce four removed buckets and an empty final authority. Native reservation count is zero at completion.

## Evidence

- Nine retained final JVMs: three selected promoted variables × flags ON/OFF/VERIFY. All pass.
- Eighteen old/stream traces, byte-identical within each pair and across flags for each selected variable; independent replay checks 1,188 authority views and 15,264 serialized entries.
- Per trace: 14 contexts, 13 retirements, 66 observations, 53 bucket changes, 36 added entries, 32 removed entries and one term rewrite. No reset, deletion-bit transition, reorder, deferred context or map invalidation.
- 1,584 native context-variable controls verify enumeration membership, exact object references/order, direct canonical ID lookup, pure reader agreement and full authorities. Native enumeration is outside observer purity checks.
- 27 expected negative cases: missing promotion notification fails in parent at observation and finish; premature retirement during checkpoint or settlement fails identically in both journals.
- Exact dependency source/class hashes from the fresh 730-class development runtime are verified before build and after runs. Only the new fixture is compiled separately.

A preliminary excluded fixture missed final root-pack invalidation; both journals refused its stale parent view. The source, build proof, failed result/error/log and reason are retained under `preliminary-missing-pack/`. The final fixture explicitly models that native boundary; no oracle was disabled, weakened or used to repair projection. Final evidence counts describe the retained matrix, not exploratory executions.

Scripts, raw traces/logs, error controls, summary and hash manifest are under `docs/tvalue-promotion-retire/`. With the preceding exact development runtime rebuilt, run this stage's `build.py`, `run.py`, then `analyze.py` from repository root.

## Limits and continuation

Promotion here is the native typed TValue factory splice, deliberately isolated from whole `Mind.commit` policy and the other factories. Terms and variables are already available in the parent. This does not qualify complete multi-factory atomic commit, inference acceptance/rejection, fault rollback, SMART persistent publication/reopen or native journal hooks. Metadata notifications and retirement remain explicit fixture bridges.

The native last-reservation packing result concerns this unsupported-term fixture; it is not a claim that production pack removes values supported by active rules. No inference speedup or full-corpus result follows from these correctness runs. The diagnostic baseline remains `StreamAuthorityJournal.java`.

The next qualification should exercise real composite Mind settlement and its exception paths on this exact development baseline, before backend-specific SMART publication/reopen and full inference measurements.
