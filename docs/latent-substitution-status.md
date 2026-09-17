# Latent substitution experiment: current status

2026-09-16. Experimental branch only; all optimization switches remain off by default.
Live develop rechecked on 2026-09-17 at `74654935b78465ade2043e40b6ff147b43ad8147`; historical experiment base remains `3ad50f1e5253304f6b530de11c078f332ea4db89`. The new commit changes command/console code, a timezone test and Version; studied inference/cache classes are unchanged. No rebase or merge performed.

## Architectural findings

The native fixture has 128 rules, 116 succedent domains and 43 antecedent domains: 492 compatible pairs out of 4,988 (9.86%). Adjacency mean 4.241, p50 3, p95/p99/max 8. This is evidence for sparse topology on this fixture, not a universal density claim.

The implemented factory index stores ordered domain occurrence rows keyed by rule and predicate/polarity, rather than materializing the entire pair graph. Publication and storage hydration build derived rows; accepted productions use the same publication path. Transaction lifecycle and generation replacement are qualified. Runtime still performs value, context, collision and semantic checks with existing code. Both Linker directions remain.

See [factory lifecycle checkpoint](latent-substitution-factory-checkpoint.md). Native domain visits fell from 14,282 to 3,251 with 3,251 unifications unchanged, but this alone did not establish an end-to-end speedup. Initial packed/array estimates concern the initial adjacency snapshot, not retained heap of the implemented factory maps; actual retained memory remains outstanding.

## Independent solve synchronization finding

Repeated synchronization of the solve index proved a separate source of work. Version-based skipping avoids rescanning unchanged groups. Public mutable-map exposure and Mind subclasses retain reference scanning; clear resets synchronization state. This optimization does not depend on latent topology.

This checkpoint adds `kanger.experiment.verifySolveSync=true`: when versioned skipping is enabled, every proposed skip runs the reference synchronization and checks that indexed counts and indexed identity-set size remain unchanged. Existing `factory-verify` behavior is preserved. The new switch permits verification with latent indexing entirely off.

Standalone qualification used only `versionedSolveSync=true`, with all other optimization flags omitted:

| Check | Result |
| --- | --- |
| Existing 123-case corpus, actual skipping | PASS |
| Same corpus, standalone reference verification | PASS |
| 16-operation state runner | Byte-identical to old-path baseline |
| 20-operation nested transaction runner, actual skipping | Byte-identical to old-path baseline |
| Same transactions, standalone reference verification | Byte-identical to old-path baseline |
| Focused solve-sync safety runner with verification | PASS |

Evidence is in [standalone qualification](latent-substitution-evidence/solve-standalone/). Transaction state comparisons include results, values, solutions, hypotheses, rules, pass actions and effects. Safety checks cover new tuples, deduplication, mutable aliases, aliases retained across clear and overridden getters. No standalone timing claim is made by these runs.

Previous measurements with other experiment flags enabled found native query median JVM medians of 114.855 to 96.357 ms (about 16% faster), but facts regressed from 22.607 to 24.599 ms and scaled edge fixtures showed no consistent gain. Those timings must not be presented as standalone solve-sync performance. See [replication](latent-substitution-solve-sync-replication.md), [group work](latent-substitution-solve-sync-work.md), [shape sensitivity](latent-substitution-solve-shapes.md), and [transaction qualification](latent-substitution-solve-transactions.md).

## Boundaries and next gates

Pair-result memoization by current argument projection was rejected: identical projections can have different binding-history effects. One-hop rule scheduling missed tuple-producing visits; full closure removed the expected savings. Do not enable either as an optimization. Optional membership filtering, argument plans and candidate/intersection optimizations have no established general end-to-end gain.

Standalone OFF/ON timings are now complete: [measurement report](latent-substitution-solve-standalone-timing.md). Native query median JVM medians improve 117.378 to 96.566 ms (17.7%), while singleton facts regress 5.8% and the two scaled fixtures differ in direction. All 168 measured semantic fingerprints and counters agree within each fixture. This supports an independent native-workload benefit, not general default enablement.

Actual factory occurrence graph accounting is now available for six fixtures: [footprint report](latent-substitution-factory-footprint.md). Native reachable size is 41,616 bytes (38,544 without boxed IDs); row reconstruction is about 0.11 ms in two JVMs. These are neither exclusive retained heap nor full compilation/storage-open overhead. Parent/child sharing, incremental publication/open cost and broader corpora remain measurement gaps.

Full publication and warm storage-reopen measurements now cover two small chain-rule databases: [operation report](latent-substitution-publication-open.md). All 60 measured samples preserve rule fingerprints and expected query outcomes. Timing is mixed: publication slows on 10 seed facts and improves on 30, while reopen varies. This measures total operation cost, not isolated index overhead; larger/cold databases remain unmeasured.

Full Maven and canonical Java 8/21 qualification are still outstanding: this environment uses OpenJDK 17 and ECJ with Java 8 source target. The baseline cannot compile `labyrinth.k` here; it remains an explicit exclusion, not a passing fixture.

The next direct-access opportunity is now measured: [occurrence materialization observation](latent-substitution-occurrence-reuse.md). Within a rotator, 344,680 of 437,729 corpus Domain-resolution slots repeat; no ordered-reference changes were observed. All lookups still execute. Step/Sapato lookup also calls setMind, rebinding Domain/TVariables, so an audit of that effect must precede any actual skipped lookup. Scoped list reuse remains only a candidate.

Context rebinding now has an [executable counterexample](latent-substitution-domain-context.md): Domain may still point to child while its TVariable has switched to root through a separate factory lookup. Identical pointers and a Domain-only context guard are insufficient. Any scoped list-reuse prototype must preserve rebinding and separately qualify canonical lookup/hydration behavior.

The [scoped list-reuse prototype](latent-substitution-occurrence-prototype.md) now preserves eager setMind and passes both actual-skip and reference-verified corpus/lifecycle/transaction checks, plus a targeted split-context test. It is default-off. Three paired JVMs show no repeatable native speedup (median 122.374 → 121.310 ms, two pairs regress); 84 measured fingerprints/counters agree. Avoiding repeated hydration alone has not produced a convincing general gain.

The retained reuse-loop [rebinding cost](latent-substitution-rebinding-cost.md) is small in one diagnostic JVM: native 0.523 ms/query, median sample share 0.445%; facts 0.052 ms, 0.133%. This does not measure all setMind callers, but gives no reason to weaken context semantics or further optimize that loop now. List reuse remains disabled.

An enlarged [50/100-edge profile](latent-substitution-larger-topology.md) exposes a filtering limit: factory removes only 3.31%/1.72% of last-invocation domain visits, with 52,599/205,199 unifications unchanged. Whole-query profiles point mainly to retained linkDomains work. This homogeneous-signature fixture is not evidence for globally sparse topology. Larger heterogeneous signatures and internal semantic-pair cost are the next useful comparisons.

The [ten-predicate shared-output comparison](latent-substitution-partitioned-topology.md) removes 16.81%/10.17% of visits at 50/100 edges, but retains 89,110/318,210 unifications and heavier overall work. Extra rules converging on path mean predicate partitioning alone does not establish useful sparsity. linkDomains remains the main measured component; its internal phases are the next attribution target.

Internal [pair-phase attribution](latent-substitution-pair-phases.md) now places most measured compatible-pair time in the substitution loop: 2.389/8.696 seconds at 50/100 partitioned edges. Preparation and guards are much smaller. Next split canonical TValue work from Domain.setUsed history checks; the current measurement does not distinguish them. Fingerprints and the state baseline still match.

The [substitution subprofile](latent-substitution-value-lookup.md) localizes most measured work to explicit TValue.find: 1.734/7.064 seconds at 50/100 edges, versus 0.079/0.292 seconds for paired setUsed calls. Escalera.release unconditionally invalidates indexes; subsequent ensureIndex walks the chain. Rebuild attribution is the next hypothesis to measure, not yet a demonstrated cause or a safe optimization.

The [TValue rebuild measurement](latent-substitution-tvalue-rebuilds.md) confirms substantial repeated full-chain indexing: 35,088 rebuilds, 131.5 million walked steps and 8.625 seconds at 100 partitioned edges. All measured releases restore the same root; 35,088 invalidate a coherent ready index. A TValue-only guarded preservation experiment is now justified for investigation, pending mutation audit and independent index-map verification. Release semantics remain unchanged at this checkpoint.

The default-off [guarded TValue index-preservation prototype](latent-substitution-tvalue-preservation.md) passes actual/verified corpus, lifecycle, state and transaction checks. It requires an unchanged mutation counter as well as coherent index and identical root. First unprofiled paired JVMs at 100 partitioned edges improve 11.379 → 3.838 seconds; a separate diagnostic drops rebuilds from 35,088 to 1. This is a first-run result pending reversed-order replication and other workloads, not default-promotion evidence.

Preservation [replication](latent-substitution-tvalue-replication.md) confirms the large gain with reversed JVM order: 11.149 → 3.576 seconds, versus the preceding 11.379 → 3.838. Native also improves in all three fresh JVM pairs (median medians 140.295 → 94.236 ms); facts remain noisy/mixed. All 90 new measured fingerprints/counters agree. Standalone latent-off qualification and canonical build/runtime gates remain next steps.

Standalone [preservation qualification](latent-substitution-tvalue-standalone.md) now passes the corpus/lifecycle/state/transaction checks with latent mode and all other optimizations off, both with actual preservation and independent verification. One standalone large-workload pair gives 13.900 → 3.632 seconds with identical 354,220 domain visits and 318,210 unifications. This is an independent index-maintenance improvement, not evidence of latent-index speedup. Default remains off; storage mutation boundaries and canonical build/runtime gates remain.

Focused [TValue mutation-boundary qualification](latent-substitution-tvalue-boundaries.md) passes OFF/ON/verified with ten byte-identical state projections. Materialization, interior persistent deletion with unchanged root, batch deletion and factory child-chain transfer exercise preservation and fallback. This is raw-cache protocol evidence; a synthetic full-reopen extension failed already in OFF and is not counted as qualification. A normal-inference focused reopen fixture and canonical build/runtime gates remain outstanding.

The [normal-inference TValue reopen fixture](latent-substitution-tvalue-reopen.md) now passes OFF/ON/verified with byte-identical projections: eight TValue entries survive two close/open cycles, then twelve survive a committed-child close/open. Rollback and rejected-collision query results also agree. Source inspection attributes the earlier synthetic expectation failure to checkpoint packing of values whose terms lack active-rule references. Canonical Maven/Java 8/21 gates remain outstanding; no default promotion is implied.

Nothing here authorizes default enablement, removing the old traversal, or merge/release/tag/deploy.
