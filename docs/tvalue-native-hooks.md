# Automatic native diagnostic hooks for memory Mind settlement

Experiment parent: `cd4aa7d83165d0b18bf2bab4cb54085d58d13235`.
Native revision: `5d5f6aff271f1abf371fbde55d637744c53f0bc3`.

This docs-only stage generates a separate native diagnostic build with automatic callbacks and compares it with a freshly compiled clean build. The positive fixture no longer explicitly invalidates routes, brackets native settlement or retires children. The unchanged old/stream journal implementations receive callbacks from native operations through a shared diagnostic adapter. Production sources/defaults are untouched.

## Result

Clean and hooked builds give exactly the same native outcome rows for all seven preceding settlement scenarios: acceptance, technical rejection, live user rejection, analyzer failure, partial checkpoint completion failure, COMMITTED finalization failure and REJECTED finalization failure. They also match the preceding explicit-bridge fixture's native outcomes.

Automatic callbacks correctly publish the accepted TValue delta, omit rejected/rolled-back TValue effects, preserve the live user-rejected child until native release and retire consumed children on normal and exceptional completion. A diagnostic observation attempted within each native settlement bracket is deferred. No provisional view or row for that probe is emitted.

The automatic constructor hooks also cover compiler-created technical children. Each positive session now observes 40 contexts, with 33 retired and seven live parent roots; this is wider lifecycle coverage than the preceding driver's 14 explicit contexts. No rows occur after retirement.

## Generated hook map

| Native location | Diagnostic callback | Scope exercised here |
| --- | --- | --- |
| Completed Mind construction | constructed | Root, explicit child, sibling and compiler technical children |
| Mind commit/release wrapper | begin/end settlement, probe, retire on consumed reservation | Seven normal/failure outcomes and live user rollback |
| TValueFactory transaction/rebase | reset | Native construction initialization |
| TValueFactory mark/commit/release | mark/complete | Composite checkpoint completion and rollback |
| TValueFactory add | route touch | Native parent/child additions |
| TValueFactory typed commit | promoted | Automatic parent/descendant invalidation |
| TValue metadata setters in finally | metadata | Construction and promotion identity/owner callbacks |
| Native visibility and pack removal | touch | Inserted, but deletion/removal paths are not exercised by this retained-term fixture |

Metadata wrappers preserve original setter bodies and invoke callbacks after return or partial failure. This fixture does not yet qualify automatic term rewriting, unsupported ID/variable mutation, explicit clear, storage materialization or persisted ownership changes. Pack retains the supported terms in these scenarios.

Settlement retirement uses the native parent reservation count before/after the call, rather than assuming every thrown exception consumes the child. This is qualified for these sequential memory cases; concurrent settlement and SMART pre-publication validation are outside scope.

## Evidence

- Eighteen final JVMs: three term-name variants × flags ON/OFF/VERIFY × clean/hooked.
- 63 positive full native settlement cases per build; native canonical reference/order/ID checks and all eight composite checkpoint resource gates retained.
- Eighteen old/stream hooked traces independently replayed: 2,610 full authority views and 3,114 serialized value entries. Pairs and flag variants are byte-identical for each term-name variant.
- Each trace: 40 baselines, 145 observations, 34 deferred native probes, 33 retirements and 16 added entries. Fourteen additions are the seven initial parent/child values; two are the surviving accepted parent deltas. No removals, deletion transitions, rewrites, reordering or resets of already observed contexts.
- 594 native context-variable controls across both builds and expected negative cases.
- 27 expected gap controls: deliberately suppressing only the native promotion callback after acceptance or COMMITTED failure is detected at after-settlement and finish; premature retirement inside settlement is rejected. Error files exactly match the prior bridge stage. The fresh oracle never repairs projection.
- Fresh clean compilation reproduces all 730 preceding class hashes. Clean/hooked builds differ only in `Mind.class`, `Mind$1.class`, `TValueFactory.class` and `TValue.class`; all other class hashes match.
- Every original native source is recovered byte-for-byte by reversing the recorded literal edits. The native algorithm bodies, accepted journals and readers are preserved.

Eighteen successful preliminary JVMs without an in-bracket observation probe are archived in `preliminary-no-bracket-probe.tar.gz`. The retained final matrix adds that positive deferral probe. This stage makes no timing claims; the archive is excluded from final evidence totals.

Scripts, adapter, notification-free positive runner, generated native source copies, literal edit map, structural proof, class/source hashes, logs/traces, native outcomes, gap errors, counts and summary are under `docs/tvalue-native-hooks/`. With a clean detached `../K3-smart-native` at the recorded head and the recorded ECJ tool available, run build, run and analyze scripts in order from the experiment repository root. Each build is compiled from source without a prior native runtime on its compilation classpath.

## Limits and continuation

These are temporary diagnostic source copies, not a production integration. Native settlement TValue compatibility and resource closure are qualified for the exercised cases; arbitrary multi-factory payload atomicity, rollback failures, constructor failures, concurrent use, federation inference, SMART storage, publication/reopen and full corpus parity remain unqualified. The accepted journal baseline remains `StreamAuthorityJournal.java`.

Next, exercise automatic ordinary mutation, clear/pack and ancestry callbacks, then qualify backend-specific SMART read/materialization/publication boundaries. Full inference and performance qualification still precede production discussion.
