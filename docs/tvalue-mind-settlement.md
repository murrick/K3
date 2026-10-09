# Full Mind settlement at the TValue diagnostic boundary

Experiment parent: `f2d16aa77fd151cfdc6c73c676eb603f4888f433`.
Native development baseline: `5d5f6aff271f1abf371fbde55d637744c53f0bc3`, freshly compiled in the SMART-base stage.

This docs-only stage calls the real composite `Mind.commit(child)` / `commitUserTransaction(child)` instead of isolated factory promotion. It verifies canonical TValue state, all eight composite checkpoint resources, reservations, irreversible exception outcomes and equivalence of the unchanged old/stream diagnostic journals. Native engine sources and defaults are untouched.

## Native outcomes

| Case | Child TValue in parent | Reservation immediately after target call | Result |
| --- | --- | --- | --- |
| Accepted native commit | Present, same canonical reference/order | 0 | Returns true |
| Native semantic rejection | Absent | 0 | Returns false |
| User transaction rejection | Absent | 1 | Returns false; child stays live until explicit release |
| Analyzer exception after provisional factory merge | Absent | 0 | Injected analyzer failure propagates |
| Factory checkpoint completion exception | Absent | 0 | Injected TVar cache commit failure propagates |
| Finalization exception after accepted commit | Present, same canonical reference/order | 0 | TransactionSettlementException: COMMITTED |
| Finalization exception after rejected commit | Absent | 0 | TransactionSettlementException: REJECTED |

The post-settlement fault is injected into native FValue cache update after root packing reaches finalization. The exception's semantic-applied flag, exact outcome, consumed-reservation flag and injected cause are asserted. A thrown COMMITTED exception is not rejection; the outcome log's `accepted` field records only whether the call actually returned true and remains false when the call throws.

For non-sequenced cases a real sibling publication changes the parent RuleFactory sequence anchor. Semantic rejection uses a stored contradictory `keep(a)` rule, not an injected false result. Analyzer failure and partial checkpoint completion use controlled fixture injections; the completion case uses a successful analyzer so the intended failure location is reached. Proxied auxiliary caches and substituted analyzers are restored before native enumeration and observer checks. TValue cache/readers are never replaced.

All eight composite cache stacks and auxiliary factory stacks are empty after each target call. User rejection retains exactly one child reservation, which explicit native release consumes. The TValue verification does not imply that every other factory's payload is atomically rolled back after partial checkpoint completion.

## Diagnostic behavior and evidence

The explicit fixture bridge brackets each target call with diagnostic begin/end settlement. An observation inside that bracket is deferred, and no provisional view is published. After native return or exception, notifications for the two known canonical values invalidate the parent route; the fresh full oracle independently verifies the resulting projection. Retired children are excluded from subsequent reads.

- Nine retained final JVMs: three term-name variants × optimization flags ON/OFF/VERIFY.
- 63 positive full Mind settlement cases, covering all seven outcomes in each JVM.
- Eighteen paired traces: 918 full authority views and 1,170 serialized entries replayed independently. Old/stream pairs and flag variants are byte-identical for each term-name variant.
- Each positive trace: 14 contexts, seven retired children, 51 observations, seven deferred observations and exactly two added entries (ordinary acceptance and COMMITTED finalization failure). No removed entries, deletion transition, term rewrite, reordered identity or reset.
- 333 native context-variable controls across positive and negative cases check exact references/order, canonical direct lookup, fresh authorities and pure reader agreement.
- 27 expected negative cases: omitted notification after ordinary acceptance or COMMITTED finalization failure is detected at after-settlement and finish; premature retirement inside settlement is rejected. Both journals report identical errors and never repair projection.
- Exact native dependency source and 730 compiled class hashes verified before build and after execution; only the fixture is newly compiled.

One preliminary fixture was excluded because parent rules created before child construction retained a sequenced RuleFactory anchor despite sibling commit. The explicit branch assertion refused it. Its source/build proof/log/error/result are retained in `preliminary-sequenced-fixture/`. The final fixture follows the existing native safety-fixture construction: registered owner without parent rule insertion, then sibling publication of stored `keep(a)`. Evidence totals describe the final retained matrix.

Build/run/analyze scripts, raw compressed traces/logs, outcome rows, negative errors, summary and hash manifest are in `docs/tvalue-mind-settlement/`. Rebuild the preceding exact-development runtime, then run this stage's build, run and analyze scripts from repository root.

## Limits and next work

This qualifies the TValue observer boundary of full native memory Mind settlement, plus checkpoint/reservation closure for the injected paths. It does not qualify arbitrary multi-factory payload atomicity, rollback failures, persistent durability, SMART publication/reopen, federation inference or automatic native diagnostic hooks. The notifications remain explicit post-call fixture bridges; no internal provisional-state oracle is substituted for native settlement.

No full inference corpus or timing comparison was run. The accepted diagnostic baseline remains `StreamAuthorityJournal.java`. Next, qualify actual hook placement on this exact native revision, including exception paths, then separately adapt/read SMART storage and run publication/reopen and inference measurements before production discussion.
