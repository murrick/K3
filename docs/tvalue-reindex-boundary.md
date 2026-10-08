# Reindex boundary: physical validity versus published generation

Diagnostic DUMB experiment; parent `c64805f7535dcc384439e935605fea1e1075784e`. Production, defaults, `develop`, and DUMB2 are unchanged. No whole-copy guard was added.

## Result and decision

A physically valid resident chain is not a certificate that a multi-record reindex completed or that its generation is authoritative. A one- or two-record standalone destination can pass the physical reader after an injected copy exception. This is a valid physical prefix, not evidence of storage corruption or a transactional guarantee violated by native reindex.

The real native `DB.reindex` builds a separate temporary generation. Controlled exceptions after copy 1, 2, or 3 leave the original Base in the live DB registry and preserve the original `.index`, `.store`, and `.integrity` bytes. Even a complete three-record temporary copy is not published after an exception. Successful DB reindex closes both old Base identities and installs a complete new generation.

The existing composite reader already checks exact generation identity: `ResidentTValueRead.activeBase` requires `User.storage[tvalues]` and native `DB.bases[tvalues]` to designate the same exact Base owned by the same User. During native copying this check selects the source. After success, the old owner's binding is refused until upper bootstrap replaces the bindings. Physical admission of the new Base alone does not bypass that check. Therefore this stage retains the existing gate rather than adding a new general mutation wrapper.

## Native source paths

`Base.reindex` iterates native index entries, gets each stored step, sets its Mind through native `getData`, then calls destination `add`. It has no whole-copy transaction. The diagnostic source adds two disarmed fixture callbacks immediately after native `base.add(stored)`. Removing those two lines recovers the preceding complete Base source byte for byte. Prior upsert, delete, flush, clear/close, and get wrappers remain unchanged.

`DB.reindex` acquires `name-temporary`, copies each Base, closes both generations, replaces generation files, then `use(name)`. Its catch closes a still-open temporary context and preserves/reopens the original as appropriate. The controlled throws here occur during copying, before publication. This experiment does not test file-install exceptions, rollback failures, or crash atomicity.

Successful `DB.reindex` leaves a fresh lazy Base registry. The fixture reacquires schemas through native `getBase` in the exact order declared by `User.openClosedStorage`: Dictionary, Domain, Function, Predicate, Rule, TVariable, Library, TValue, FValue, Comment. This is an explicit physical bootstrap control; it neither changes fields nor pretends the old Mind was semantically rebound. The old composite binding is deliberately still refused.

## Two different destination caches

| Test path | Destination configuration | Physical observation |
|---|---|---|
| standalone Base copy | caller enables resident Base cache and sets Data offset cache to zero | explicit native decode provides target-bound resident nodes; prefixes of 1/2/3 records are admitted |
| native DB temporary generation | temporary User and its native default Data cache remain unchanged | target's native `get` reuses a source-bound cached node; physical reader refuses `invalid resident node` |
| newly installed DB generation | fresh ordered schema acquisition, then explicit native warming | complete three-record physical chain admitted; stale owner's composite identity still refused |

The temporary-cache refusal is not a new guard and is not converted to an admitted case. Native `Data.set` stores the supplied `IStep` in its offset cache; `Data.get` can return that same object, whose Sapato Base binding remains the source. This explains the binding refusal using native source plus observed refusal. No cache settings are changed on the temporary User.

Native `get`, `getRoot`, schema acquisition, and flushing are explicit controls outside read-only observation. Each physical snapshot is surrounded by the existing native fingerprint; its refusal or result must leave that fingerprint unchanged. Source getData ownership effects belong to native copying and are not claimed absent. The fixture keeps arbitrary persistent term/variable references so it tests physical rows, not semantic hydration.

## Final matrix and regression

| Scope | Scenarios, each clean/shadow × ON/OFF/VERIFY | Fresh JVMs |
|---|---|---:|
| standalone native Base reindex | success; injected failure after copies 1/2/3 | 24 |
| real native DB reindex | success; injected failure after copies 1/2/3, plus live/stale composite identity checks | 24 |
| focused inherited regression | successful delete, flush, clear, close, upsert, saved-link qualification scope | 36 |
| **Final total** | all assertions pass | **84** |

All native outcomes and physical proofs in focused regression match their preceding evidence. All three successful regression journal traces, containing nine authority views, are byte-identical and pass independent replay. The preceding 42-trace matrix is retained and checksum-verified, not reported as a newly rerun full matrix. Earlier negative corpus attempts remain unqualified.

The 24 standalone runs and 36 regression runs were executed before the final DB identity-check extension. Build hashes verify their Base, standalone fixture, and inherited runtime families are unchanged; only the DB fixture family changed, which those runs did not activate. All 24 DB identity scenarios were then rerun. The prior successful physical DB matrix is separately archived.

## Retained attempts and fixture corrections

100 preliminary/debugging JVM result records are archived: 96 successful standalone runs and four failed DB fixture/debugging runs. An additional 24 successful DB physical-control runs are archived before identity checks. One debugging startup reused an existing user.home, failed authentication before setup, and is documented separately. Total executed fixture/startup JVMs: 209; the final qualification total remains 84.

1. The initial standalone 24-case proof passed.
2. The first DB fixture attempt ran 24 standalone cases successfully, then incorrectly required the native default temporary Data cache to yield target-bound nodes. The physical reader correctly refused `invalid resident node`. A fresh-home debugging replay exposed that exact reason. A separate reused-home startup failed before setup.
3. The next DB fixture attempt correctly preserved that refusal, but acquired `tvalues` first from the new lazy registry after success. This violated the documented schema/baseCode acquisition order and read the wrong physical partition. The failed fixture and its preceding 24 successes are retained.
4. The subsequent fixture attempted full semantic Mind reopening of rows carrying artificial term/variable IDs. Native semantic qualification correctly failed during hydration. This fixture error and its preceding 24 successes are retained. Physical rows with arbitrary references are not a semantic reopening fixture.
5. Final physical bootstrap uses native schema acquisition in the declared order, without semantic query/commit. That full 48-case matrix passed. Its 24 DB cases were then extended with the existing composite identity checks and rerun successfully.

No rejected observation was waived: native temporary nodes remain physically rejected, stale owner bindings remain composite-rejected, and full semantic reopen remains unqualified. Data, journal, recorder, lookup helper, and DB source are unchanged throughout.

## Next useful step

The next step is to select a small, explicit admitted context lifecycle and measure the journal's cost against the full oracle on that same lifecycle. Generation identity and declared rebaseline must be part of that scope. This should produce evidence for a useful optimization rather than further generic storage guards.

Not qualified: general reindex inputs, semantic reopening of these artificial rows, real I/O failures, publication rollback, crashes, concurrency, arbitrary reentrancy, or journal certification of a partial standalone copy. No production performance claim is made by this stage.

Evidence: `tvalue-reindex-boundary/summary.json`, build validation, per-JVM commands/logs, physical proofs, independent replay/analyzer, and SHA-256 manifest.
