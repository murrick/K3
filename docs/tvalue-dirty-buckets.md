# Shadow observer for dirty TValue buckets

The new shadow observer selects TValue variable buckets from completed mutation hooks and native visibility-map changes, then rereads only those buckets. A separate full snapshot verifies the resulting ordered projection at every completed observation. Full snapshots never repair a missing dirty key or overwrite the shadow state. Deterministic native gates and inference oracles pass; two randomized corpus attempts are rejected by the previous complete-corpus oracle because a different native publication order wins. Complete randomized-corpus equivalence is therefore unqualified.

Base: `experiment/3.8.0-tvalue-journal` at `c90c3619ef3b9cf2267af0b618bb60c452516913`. This branch adds standalone diagnostics and evidence under `docs/` only. Production sources, qualification-module sources and CI workflows remain unchanged. The full Linker traversal retains control; there is no frontier consumer, default flag change or performance claim.

## Mutation selection and native boundaries

Temporary hooks follow successful `TValueFactory.add`, each completed physical pack removal, typed factory promotion and `Mind.setUnitDeleted`. Hooks record identity metadata and select a variable bucket in the owning context and its live descendants. They record mutation attempts rather than claiming each call is a canonical change: duplicate adds and repeated deletion setters can select a bucket without emitting a projection delta.

At a completed observation, the shadow rereads selected buckets through native `forEach` using unregistered variable shells. It retains untouched buckets from its prior projection. Baseline and generation reset use a complete native seed. Canonical TValue IDs, Term IDs, logical deletion status and native enumeration order remain authoritative, including logically deleted identities.

Open local checkpoints and composite settlement defer observations. Dirty keys survive an inner commit and an outer release, because native rollback is category-specific. Rereading them after completion distinguishes vanished additions from surviving deletion/restoration marks. It also avoids publishing provisional effects while a parent commit is still being revalidated or rolled back.

The observer compares native TValue deletion/restoration ID maps across the context's ancestry at each boundary. Effective mark changes select previously known value buckets. This covers direct edits through the exposed maps as well as composite merge/restore/clear paths, whose native implementation does not call individual setters. Redundant explicit restoration removal may select a bucket without changing logical visibility. Unknown IDs have no recorded bucket until baseline or add/promotion metadata establishes one; any resulting projection gap is rejected by the full oracle rather than silently repaired.

The original Mind commit/release and deletion-setter bodies, Linker kernels, factory algorithms and direction-specific donor universes remain intact in temporary source copies. Reversing the wrappers/hooks recovers all three original source files byte-for-byte. All 663 prior native class hashes match the clean rebuild. Instrumentation changes the same eight shared classes as the preceding observer: Linker plus four anonymous classes, Mind plus one anonymous class, and TValueFactory.

## Native gates

The main gate retains the previous 62 checks and adds 21, for 83 assertions in each of ON/OFF/VERIFY. Its three complete logs/traces agree. Each trace has 33 contexts, 128 complete observations, two deferred observations, 25 net bucket changes and one reset; 80 touch records and 13 visibility-map invalidations produce 46 dirty bucket rereads plus 34 baseline/reset seeds.

| Additional boundary | Native result checked |
| --- | --- |
| Delete existing value inside local checkpoint, then release | Provisional addition disappears; logical deletion survives and produces the corresponding delta |
| Resurrect existing value inside local checkpoint, then release | Same canonical identity remains restored; resurrection is recorded |
| Raw deletion-map insert/clear | Native visibility changes are reflected without a setter hook |
| Raw child restoration and ancestor deletion clear | Child override and inherited visibility agree with native reads |
| Accepted child deletion with another child held live | Parent emits the deletion while the value remains enumerated; the live sibling sees the inherited mark |
| Final held-child release | Native quiescent root pack physically removes the accepted deleted identity |

A separate negative gate runs in three fresh JVMs. It changes an existing TValue's Term via the public `setValue` metadata setter, deliberately outside this prototype's hooks. The independent full oracle detects the stale shadow projection; session finish rejects it, leaves the native changed value intact and relinquishes its ThreadLocal session. All three gates terminate successfully only after verifying this expected rejection. This is evidence of an unsupported mutation channel, not coverage for arbitrary public metadata writes.

The main gate also retains accepted/rejected/user-rejected/exceptional child publication, nested addition rollback/commit, duplicate adds, logical deletion, resurrection, physical removal and reset. All settlement paths preserve native reservations and transient current bindings.

## Complete qualification

The expanded matrix contains 35 fresh JVMs: three main gates, three expected-gap gates, twenty clean/journal × ON/OFF existing native contract runners, four clean/journal ON SON/corpus runs, one ON journal SON repeat, and four clean/journal × ON/OFF ordered-publication gates. One additional corpus retry brings the recorded total to 36. All complete natively with exit zero, completion markers and empty stderr, but two randomized instrumented-corpus attempts do not match the frozen complete-corpus oracle. They are excluded from accepted equivalence results: 34 JVMs meet their scoped oracles.

Four source-keyed candidate replays each agree with all 47 frozen source answers and the small/external answer vectors. Clean/journal native contract rows, full 20-operation transaction state/work files and lifecycle/dependency/donor assertions agree. Nine complete SON snapshots agree with the frozen oracle. All nonconcurrent corpus rows agree separately after timing removal, including in both rejected attempts. This scoped comparison explicitly excludes the three concurrent sections and does not substitute for complete-corpus equivalence.

Every complete baseline/reset/delta/view/retirement stream from the twelve unchanged noncorpus native-runner traces also matches the preceding full-snapshot observer exactly after removing the new invalidation records and diagnostic total row. The dedicated main gate has extra native cases and is checked separately. Eighteen current traces replay independently against 33,212 full native views; that count includes the current randomized corpus whose complete semantic oracle is rejected. Excluding it leaves 24,592 views in accepted scoped runs. The archived rejected trace also replays correctly. The analyzer reconstructs dirty sets, consumed bucket-read counts, seed counts and visibility/touch record totals from every trace.

## Publication-order finding and retained failure

The unchanged `KangerTest.set_08_03` creates three children and commits them from three threads. Two contain conflicting operands. The frozen comparison expects six retained Rules and one particular losing process. Both instrumented attempts instead retain seven Rules with the other process rejected; the native test itself reports six solutions and success. These results are retained, not relabeled as complete-corpus passes, and the original normalization/expected-six oracle is unchanged.

`TValueOrderedPublicationRunner` uses the same native operands and holds all three children open, then publishes them in each of the six explicit orders. It uses native query/commit/pack/classification and asserts closed reservations and six final solutions. The four clean/journal × ON/OFF JVMs pass 90 assertions each and produce identical complete native payloads for every order. Orders with child 1 winning retain six Rules; orders with child 2 winning retain seven. This demonstrates native order-dependent outcomes and equivalence under these controlled orders, without proving transparency for arbitrary concurrent execution.

An earlier uninstrumented native optimization-flag corpus log also contains the seven-Rule/six-solution outcome. Its exact excerpt and source identity are preserved under `rejected-attempts/`; it is evidence of an earlier occurrence, not a same-baseline causal control. The randomized corpus trace also differs from the preceding observer's fixed-order trace and is excluded from the twelve exact trace comparisons.

Exact counts and per-run bucket inspection metrics are recorded in `summary.json`. Dirty bucket reads and seed buckets may be compared with full authority buckets to describe inspection scope, but the diagnostic still performs both reads and a full oracle scan. These counts are not timing, allocation or end-to-end performance results.

| ON diagnostic scope | Dirty bucket reads | Complete-seed buckets | Full authority buckets |
| --- | ---: | ---: | ---: |
| Main native gate | 46 | 20 | 97 |
| Three SON samples | 63,144 | 21,144 | 194,116 |
| Six explicit publication orders | 24 | 0 | 48 |

Local runtime: Java 17.0.20; ECJ 3.33.0 with Java 8 target. ON/OFF explicitly sets all eight integrated flags; VERIFY additionally enables the TValue-index and solve-sync verifiers. No new Java 8/21 CI result is claimed for this docs-only branch. Prior production donor CI remains documented in the inherited dependency report.

## Limits and next implementation boundary

This is incremental **bucket selection with authoritative bucket rereads**, not reconstruction from a causal write stream. Net round trips between observations remain invisible. Visibility-map comparison is a conservative boundary mechanism rather than incremental observation of map writes. Typed promotion scans child metadata; it is not claimed to be proportional only to newly created values.

Full snapshots, retained objects, diagnostic rows and ThreadLocal sessions make this unsuitable as a production overhead model. Reads can hydrate TValue objects through the native cache. Arbitrary storage updates, custom Mind/factory implementations, concurrent worker threads and untracked TValue metadata setters are not qualified. The negative gate proves that one real missing channel is detected, not that every possible extension gap will be detectable.

The next useful refinement is to account explicitly for every canonical write/visibility channel before removing the full shadow oracle, and to qualify randomized publication with an oracle that preserves the actual commit order. The existing frozen complete-corpus comparison remains a failed gate for these two attempts. A scheduler still needs current bindings, domain stamps, FValues, raw aliases, callbacks, hypotheses and the remaining dependencies described in the lifecycle map, or conservative full traversal. Neither a clean dirty bucket nor absence of a TValue delta permits skipping an arbitrary rule or callback.

## Reproduction and evidence

From the checkout root, with ECJ at `../tooling/ecj.jar`:

```sh
python docs/tvalue-dirty-buckets/build.py
python docs/tvalue-dirty-buckets/verify_structure.py
python docs/tvalue-dirty-buckets/run.py
python docs/tvalue-dirty-buckets/analyze.py
```

The builder recreates temporary clean/instrumented class directories at `../build/tvalue-dirty-buckets-*`, so stale classes are excluded. Complete logs/traces, native state/work vectors, hashes and scripts are committed beside the report. Preliminary probes are archived separately and excluded from the accepted matrix. The first rejected corpus attempt is archived; the second occupies the current matrix's corpus slot and has an explicit failed status in `summary.json`. The analyzer verifies recorded snapshot consistency and reports complete-corpus failure; its own successful exit is not a blanket inference-equivalence result. `manifest-sha256.json` covers the report and evidence, excluding itself.
