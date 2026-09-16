# Latent substitution experiment: current status

2026-09-16. Experimental branch only; all optimization switches remain off by default.
Live develop rechecked at `3ad50f1e5253304f6b530de11c078f332ea4db89`.

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

Nothing here authorizes default enablement, removing the old traversal, or merge/release/tag/deploy.
