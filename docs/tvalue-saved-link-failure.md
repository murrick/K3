# Incomplete native factory updates with recorded saved links

Parent: `f6ef43a7348bf270ae196db389c033306f305702`.

## Result

Recorded saved links do not turn the tested incomplete native TValueFactory update into an accepted dirty-journal session. The physical reader now correctly admits the one record already persisted and decoded before the native serialization failure. The existing update wrapper independently records `unsupported incomplete native factory update` before journal finish, clears its active update frame, and discards pending materialization registrations. Finish then also detects the actual partial authority mismatch. Neither rejection is suppressed or used to repair the projection.

12 fresh JVMs (success/failure × clean/shadow × flags ON/OFF/VERIFY) pass on their first run. Independent replay validates three successful traces with 51 authority views. Three denied failure prefixes are retained separately; they are not counted as successful traces. All three successful traces and all three failure prefixes are byte-identical to the original update-boundary stage. Native outcome rows match that stage in all 12 runs. No runtime implementation changed in this stage: only a new fixture runner is compiled.

| Observation | Successful update | Serialization failure on second record |
| --- | --- | --- |
| Native original result | Returns normally | Original IllegalStateException and message |
| Physical recorded chain | Three resident stored values | One resident stored value |
| Native endpoints after diagnostic snapshot | Both remain null | Both remain null |
| Materialization registrations | Three parent registrations | None |
| Native pack callback | Ordinary native serialization | Exactly one failing callback |
| Journal outcome | Success, 17 views | Refused before finish; oracle also reports mismatch |
| Native partial-write evidence | Full write | First written, second attempted, partial factory root retained |

## Fixture and failure evidence

The runner copies the original `UpdateBoundaryRunner` and explicitly activates the unchanged thread-local `RecordedLinks` before native work, clearing it in finally. The successful case retains the parent stored identity, child memory masking, subsequent child generation reset, scalar setter routing, and detached-old-alias controls. A new physical snapshot assertion immediately after native factory update proves complete recorded resident coverage while endpoint fields remain null.

The failure case starts with three ordinary native memory TValue records and a real held lease. It begins and baselines the journal before replacing the middle Step's data with the existing `FailingValue` fixture. Only the native serializer calls its overridden pack, which throws `IllegalStateException("native fixture serialization failure")`. Exactly one callback executes. The unchanged native update writes and decodes the first ordinary TValue, then attempts the second native write and throws. Native Base writeCount advances by two, and the factory's partial root is retained as the first record. These native effects are checked before cleanup.

With saved-link recording active, the new read-only physical snapshot succeeds for exactly that first stored value: `[0:0:4:0]`. The entire native Base/storage/cache/owner fingerprint is unchanged across this snapshot; raw rootId/topId remain null. Thus journal rejection no longer depends on the old physical reader's unresolved-endpoint guard.

Before finish, the fixture inspects the existing diagnostic session: its update frame is already null and its errors already include `unsupported incomplete native factory update`. The failure prefix still consists solely of the original BASELINE/VIEW pair, with no TOUCH, CHANGE, or successful footer. This proves pending materialization objects were not published into the incremental registry on failure.

All three shadow finish errors are identical:

```
journal errors [unsupported incomplete native factory update, java.lang.AssertionError:dirty projection mismatch ctx=1 reason=session-end shadow={0=[0:4:0,1:1:0,2:5:0]} authority={0=[0:4:0]}]
```

The dirty journal retains its original three-value baseline while the full oracle now observes the actual one-value partial persistent authority. That mismatch is expected negative evidence after a native partial failure, not an accepted trace, an oracle waiver, or a repaired projection. The earlier unresolved-endpoint error is absent because the physical chain is now qualified from a native decode witness. The unconditional incomplete-update error remains independent of this oracle check.

An explicit empty fresh journal session closes after the rejected session, showing it was removed. Only then does fixture cleanup restore the original middle unit, explicitly resolve native endpoints, and release the lease. Native cleanup leaves zero reservations. This cleanup does not erase a failure or make the failed session successful.

## Implementation and scope

The runner uses exactly the previous stage's diagnostic Data overlay, saved-link recorder, and physical reader, plus exactly the existing native Base materialization and factory post-update wrappers/journal. Actual compiled hashes are checked for all those overlays and all 696 clean/shadow native classes. Production source, default flags, and develop are unchanged. All parent saved-link, original update-boundary, and topology-counterexample evidence manifests are checked unchanged.

The original 30-JVM/36-trace saved-link matrix and the original 83-JVM update-boundary regression matrix are retained, not rerun. This stage reruns the original 12-case success/controlled-serialization-failure matrix with the new Data/reader/recorder classpath and additional physical-admission/failure-state assertions. Native exception class/message and callback count are preserved; no byte-identical stack-trace claim is made.

This qualifies the controlled second-record serialization failure in a quiescent, trusted, single-thread diagnostic activation. It does not qualify arbitrary I/O failures, crash recovery, failures between data/index/integrity updates, concurrent or reentrant writes, worker-thread decoding, or production use of the recorder. The two older rejected overlapping concurrent corpora remain retained and unqualified. No performance gain or full inference-frontier completeness is claimed.

The next useful boundary is native storage failure after data or index changes but before integrity completion: saved-link provenance must be invalidated or refused when native generation metadata has not settled. That requires a separate fault-injection fixture rather than extrapolation from this serialization callback.

## Reproduction

From the worktree root:

1. `python docs/tvalue-saved-link-failure/build.py`
2. `python docs/tvalue-saved-link-failure/run.py`
3. `python docs/tvalue-saved-link-failure/analyze.py`

Requires frozen prior compiled overlays in `../build`, Java 17, ECJ 3.33.0 targeting Java 8, and existing native resources. The evidence directory includes the new runner/scripts, class hashes, 12 full invocation/result records, stdout/stderr, three successful traces, three denied prefixes/errors, six physical partial-chain proofs, independent replay summary, and SHA-256 artifact manifest. No preliminary JVM failure occurred in this stage.
