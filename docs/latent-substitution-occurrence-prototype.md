# Scoped occurrence-list reuse with rebinding

2026-09-16. Default-off experiment: `kanger.experiment.reuseOccurrenceLists=true`, effective only with factory or factory-verify latent mode.

## Change

One rotator owns an identity-keyed Rule map of predicate/polarity lists. The first request uses the existing factory lookup. Repeated requests borrow that same ordered list and eagerly call Domain.setMind on every occurrence, including duplicates, before entering semantic pair checkpoints. The map is discarded when the rotator returns. The factory index remains ID-only; no persistence, manager, global cache, Rule scheduling or substitution logic changes.

Factory-verify additionally performs the canonical factory lookup on every reuse and asserts exact count and ordered object identities, then retains the existing exhaustive Rule-tree check. This reference lookup can itself restore context, so verification mode alone cannot qualify removal of its side effects. Actual skipping is tested independently below. Existing profileOccurrences counters count requested slots when combined with reuse, not actual hydrated slots; the earlier no-reuse observations retain their original interpretation.

## Qualification

Both factory and factory-verify, with reuse enabled and other optimizations omitted:

- Existing 123-case corpus: PASS in both modes.
- 16-operation state output: byte-identical to old-path baseline in both modes.
- 20-operation nested-transaction direct state output: byte-identical to old-path baseline in both modes.
- 13-stage lifecycle including storage reopen and generation replacement: PASS in both modes.

The dedicated LatentOccurrenceReuseRunner invokes the actual reuse branch with verification disabled. It first materializes a variable-bearing list in child context, switches the variable to root through its factory, and repeats selection. It asserts the same list instance is returned and both Domain and variable return to child context. A fresh rotator map produces a distinct list with identical canonical elements. This directly tests the previously found split-context failure without an oracle lookup repairing it.

These tests establish corpus equivalence, not a universal guarantee for arbitrary custom factory behavior, concurrent mutations or untested lifecycle changes. No canonical Maven/Java 8/21 qualification has been performed here.

## End-to-end timing

OpenJDK 17, 512 MiB heap, three independent JVMs per mode, sequential OFF/ON, ON/OFF, OFF/ON pairs. Factory mode enabled in both; only list reuse changes. Three warmups and seven measured samples per fixture/JVM, fresh Minds, fingerprints outside query timing. No verification or profiling during timing. This is a different comparison from standalone solve-sync timing; versionedSolveSync is off.

| Fixture | OFF JVM medians ms | ON JVM medians ms | Median of medians OFF → ON |
| --- | --- | --- | --- |
| Native | 127.137, 122.374, 108.366 | 113.429, 143.491, 121.310 | 122.374 → 121.310 |
| 100 singleton facts | 22.923, 28.041, 23.943 | 22.839, 25.605, 23.653 | 23.943 → 23.653 |

All 84 measured fingerprints, row counts, domain-pair counts and unification counts agree within each fixture. Native improves in one paired JVM and regresses in two; its 0.9% median reduction does not establish a repeatable speedup. Facts improve slightly in all three pairs, but the small/noisy series does not justify default enablement. Avoided factory resolution and list allocation may be offset by memo work and retained rebinding; this is a hypothesis, not measured attribution.

Evidence and logs: [occurrence prototype](latent-substitution-evidence/occurrence-prototype/). Reproduce timing with LatentSubstitutionBenchmarkRunner, factory mode, benchFingerprint=true and a direct benchOutput CSV, toggling reuseOccurrenceLists across fresh JVMs. Qualification uses the same toggle with factory/factory-verify; the focused runner selects actual factory mode internally.

The prototype stays disabled. Do not remove reference traversal or claim the earlier 78.7% repeated-slot count translates into query acceleration. Further work should measure the costs retained by rebinding and compare temporary live-reference retention before enlarging the cache scope or simplifying context behavior.
