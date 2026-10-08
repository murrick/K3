# Diagnostic delete/flush admission guard

Parent: `82cf4a2cad984d62bea22187031c4b6701085828`.

## Result

The diagnostic per-Base guard now covers native deleteAll (and delete through delegation) and flush, in addition to add/update. Physical observation refuses during either operation and after an exceptional body exit. Failed delete/flush remains refused across an empty new journal session and subsequent explicit native cache reload, within the same recorder activation. A completed head delete and a completed flush remain physically admissible. No native state is repaired by the guard.

132 fresh JVM cases pass on the first run: 60 new delete/flush cases and the complete preceding 72-case regression matrix. Independent replay validates the same 42 successful journal traces and 225 authority views; every successful regression trace is byte-identical. All 15 denied regression prefixes, failure errors, physical proofs, and native outcome rows are also unchanged. The new fixtures add 168 read-only active-operation refusal checks; with the 228 repeated upsert checks, the matrix includes 396 checks inside native mutation bodies.

| Operation / throw point | Indexed head | Manifest head | Cached head | Pending WAL | Physical admission |
| --- | --- | --- | --- | --- | --- |
| Head delete completes | Removed | Removed | Removed | Yes | Remaining two-record chain |
| Delete after WAL | Present | Present | Removed | Yes | Refused |
| Delete after index | Removed | Present | Removed | Yes | Refused |
| Delete after data | Removed | Present | Removed | Yes | Refused |
| Delete after integrity | Removed | Removed | Removed | Yes | Refused |
| Flush completes | Present | Present | Present | No | Original three-record chain |
| Flush after index/data/integrity | Present | Present | Present | Yes | Refused |
| Flush after checkpoint | Present | Present | Present | No | Refused despite empty pending WAL |

## Native sequencing

Native Base.delete delegates to deleteAll. The latter returns for null/empty collections, otherwise invalidates every target's resident cache entry before acquiring the storage locker. Under the locker it prepares delete recovery, hits after-WAL, removes the index record, hits after-index, removes data, hits after-data, removes the integrity entry, hits after-integrity, and finally invalidates endpoints when records were removed. A throw at any tested point leaves the original known root/tail fields intact. Cache invalidation has already occurred even at the WAL point.

Native Base.flush increments flushCount, acquires its locker, and flushes index, data, integrity, and recovery checkpoint in that order, with a fault point after each. At flush-after-checkpoint, native pending recovery is already empty, but the body has not returned normally. The guard conservatively retains that failure: an empty recovery queue does not establish successful completion of the observed operation.

The new wrappers encompass these complete original bodies. Removing them recovers the preceding Base overlay byte-for-byte. Existing upsert and get/materialization wrappers remain unchanged. Native deleteAll still invalidates caches outside its locker; this remains a reason why the locks alone cannot prove concurrency safety.

## Guard extension

RecordedLinks exposes beginMutation(base, operation)/endMutation(token, success). Existing beginUpsert/endUpsert delegate to the generalized gate with the same upsert label, preserving all previous diagnostics. A gate records the current active operation and the first failed operation. A frame restores its prior active label on exit, and a later successful operation does not clear an earlier failure. Nested operation entry conservatively marks failure; general nested/reentrant mutation is not qualified by this matrix.

The temporary Base overlay wraps flush and deleteAll with a diagnostic token, an initially false success flag, the unmodified body, and a finally hook. Success is marked only after the entire native body returns. The hooks operate on diagnostic thread-local state, do not examine or iterate the delete collection, invoke semantic getters/callbacks, access disk, hydrate owners, or mutate Base fields. The existing reader's requireSettled call now reports active/failed native delete or flush as appropriate, before empty-state, manifest, endpoint, or witness admission.

The physical reader itself, native Data decode overlay, factory update wrapper, and dirty journal are not modified. The failure marker belongs to the explicit recorder activation, not to a journal session or native recovery file. Disabling/reactivating recording is not a qualified recovery or fault-reset procedure. Process restart/recovery and reuse of an earlier failed Base remain outside scope.

## New fixtures

Each of the 60 new cases uses actual native User/Mind/Base/Sapato/TValue instances. The fixture stores IDs 40, 7, 91 through native Base.add and warms the complete saved physical chain 91 → 7 → 40 with native reads before arming the probe. Recording is explicitly active before decode. Recovery is still pending, providing a meaningful flush checkpoint control. Known native endpoints are resolved before the tested operation.

The delete fixture calls native Base.delete(91), removing only the physical head. No predecessor rewrite or middle-chain repair is needed. A successful delete yields a complete two-record chain 7 → 40; the reader infers those endpoints locally while native rootId/topId remain null. A successful flush keeps the original three-record chain and its known native endpoint fields.

For the four delete and four flush points, a separate diagnostic throw probe runs at the original native fault call site. It first attempts a physical snapshot and requires an active-operation refusal with an identical direct native Base/storage/cache/owner fingerprint. It then throws InjectedMutationFailure only at the selected point. The no-fault controls traverse all four checkpoint probes and return normally. Each fault's exact diagnostic exception class/message is preserved by the wrapper in clean and shadow builds.

The temporary injector retains the previous StorageFaultRunner hook for regression and adds the new DeleteFlushRunner hook; both are inert unless their fixture is explicitly armed. Removing both hooks recovers the original native Runtime.halt injector source. This matrix does not select or execute native halt(86), perform crash recovery, or model arbitrary operating-system I/O failures.

After a failed operation, the physical reader must refuse the matching failed operation and preserve its full direct native fingerprint. The original known root/tail fields are asserted unchanged. In shadow fixtures an explicitly empty fresh journal session begins and finishes; the physical refusal remains. Native cache clear and reload of the unaffected two records likewise do not clear it. These are explicit fixture controls after the primary failure outcome, not reader repairs. They do not claim that a persistent journal context can successfully observe a failed Base; the preceding regression already verifies that such observations fail for upsert.

Only after primary admission/refusal checks does the fixture query native index presence, integrity membership, cache membership, pending recovery, and flushCount. These native controls establish the table's partial effects. Flush has exactly one additional native attempt. Fault datasets are isolated temporary files and remain quarantined until JVM exit; no close, retry, or recovery heals a failed result before reporting it.

The new physical-only fixtures emit complete physical proof records rather than journal traces. In particular, this does not qualify automatic journal delta routing for an arbitrary direct Base.delete or a multi-record factory delete. Those are distinct lifecycle/identity questions. The 42 counted successful journal traces come entirely from the freshly rerun publication/update regression fixtures.

## Regression and evidence

All 72 cases from the upsert-guard stage are rerun with the new Base/recorder/injector overlays: composite held-sibling accept/reject/live-user-reject/original analyzer exception, accepted final quiescent publication without endpoint repair, saved-link scope refusals, native serialization callback failure, and all four upsert throw phases. Native rows, physical proofs, and failure errors match the preceding stage. Successful traces are independently replayed and byte-identical. Fifteen denied prefixes remain separate and byte-identical. New journal-session errors on an already failed upsert Base also remain byte-identical.

The build verifies the original delete/flush bodies and prior wrappers by exact source recovery, frozen Data/reader/journal/factory runtime hashes, and all 696 original native class hashes per clean/shadow mode. New overlays supersede Base, RecordedLinks, and the diagnostic injector at runtime; their prior files remain unchanged on disk. Parent upsert-guard, saved-link, and update-boundary evidence manifests are checked unchanged. No preliminary JVM failure occurred in this stage.

## Limits and next boundary

The qualified new scope is single physical-head deletion via native delete/deleteAll delegation and flush, under trusted instrumentation, explicit activation, and caller-enforced quiescence. Bulk delete iteration, predecessor rewrites, arbitrary collections/callbacks, concurrent or worker-thread mutation, generalized reentrancy, append relocation, durable fault reset, process recovery, and full inference-frontier completeness are not qualified. The two older rejected overlapping concurrent corpora remain retained and unqualified.

Base.clear and close can still mutate native resources outside the wrappers' complete operation boundaries. Flush coverage inside clear/close does not establish coverage of their remaining bodies. The next review should examine those sequences before extending admission or declaring whole-storage lifecycle coverage. Production sources, default flags, and develop are unchanged; no performance claim is made.

## Reproduction

From the worktree root:

1. `python docs/tvalue-delete-flush-guard/build.py`
2. `python docs/tvalue-delete-flush-guard/run.py`
3. `python docs/tvalue-delete-flush-guard/analyze.py`

Requires frozen prior overlays under ../build, Java 17, ECJ 3.33.0 targeting Java 8, and native resources. Committed evidence includes generalized recorder and new fixture source, generated-overlay build commands/hashes, 60 new physical result records/stdout/stderr/proofs, 72 fresh regression records, 42 successful traces, 15 denied prefixes/errors, new-session negative errors, independent replay summary, and SHA-256 manifest.
