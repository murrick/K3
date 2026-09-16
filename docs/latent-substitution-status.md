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

Next useful measurements are isolated solve-sync OFF/ON query timings with fingerprints, plus retained memory and build cost for the actual factory representation on a broader corpus. Full Maven and canonical Java 8/21 qualification are still outstanding: this environment uses OpenJDK 17 and ECJ with Java 8 source target. The baseline cannot compile `labyrinth.k` here; it remains an explicit exclusion, not a passing fixture.

Nothing here authorizes default enablement, removing the old traversal, or merge/release/tag/deploy.
