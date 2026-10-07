# Native upsert boundary throw probes with saved-link recording

Parent: `79b5fe1da42a523d5f56c8b32101ff32248a0064`.

## Result

The existing post-update wrapper refuses all four tested native storage-boundary failures before journal finish. However, the standalone physical reader still admits a complete old cached chain before the failing record's manifest entry is replaced. After the manifest replacement it refuses that old node's stale saved-link witness. Saved-link provenance alone is therefore not admission proof that a native write has settled. No reader or recorder implementation is changed in this stage; the intermediate admission evidence is intentionally retained as a negative boundary for a future native-write guard.

30 fresh JVMs (no fault plus four fault points × clean/shadow × flags ON/OFF/VERIFY) pass their expectations on the first run. Independent replay validates three successful traces with nine authority views. Twelve denied failure prefixes remain separate and are not successful traces. All clean/shadow/flag native outcome rows and physical proofs agree per fault point.

| Throw at second upsert | Middle value on disk | Middle manifest entry | Old cached middle | Physical reader | Journal |
| --- | --- | --- | --- | --- | --- |
| No fault | b | New | Replaced by b | Admits new chain | Success |
| after WAL | a | Old | a | Admits old chain | Refused |
| after data | b | Old | a | Admits old chain | Refused |
| after index phase | b | Old | a | Admits old chain | Refused |
| after integrity | b | New | a | Refuses stale witness | Refused |

## Fault mechanism and fixture

Native `DumbFaultInjector.hit()` uses `Runtime.halt(86)` when `kanger.dumb.fault.haltAt` matches. A halted JVM cannot finish its in-process journal. This experiment uses a separate diagnostic throw probe at the same existing call sites. The temporary injector overlay adds `StorageFaultRunner.hit(point)` before the original halt body. Removing that one line recovers the original source byte-for-byte, including its halt property, stderr marker, and exit code. The actual halt path is neither selected nor executed in this matrix.

The probe is armed only for the tested native TValueFactory.update, counts visits to the selected upsert point, and throws `InjectedStorageFailure` on the second visit. It performs no native reads, semantic callbacks, state repair, or payload mutation. A clean run and a shadow run receive the same diagnostic exception class/message; the wrapper preserves it. This is controlled in-process throw injection, not a claim of equivalence to operating-system I/O failure, process death, or successful crash recovery.

The fixture uses real native User/Mind/DB/Base/TValueFactory/Sapato/TValue. Three TValue records initially remain in the factory's memory layer. Before the journal starts, native Base.add stores physical Sapato copies, flushes, resolves endpoints, and warms all three cached records while saved-link recording is active. These fixture writes intentionally preserve the factory's actual memory layer; they do not seed or repair journal state. A real held Mind lease remains live.

The middle memory TValue is then changed from a to b through its public native scalar setter, while the independent stored/cached copy still contains a. The journal baselines the factory memory authority at b. Native factory update rewrites the first record normally and decodes it, then reaches the selected point on the second rewrite. There are exactly two write attempts on fault paths and three on success. The failed second Base.add has not reached its cache invalidation. Its cached middle object therefore remains the original a. The first successful add already invalidated native endpoints; both fields remain null throughout physical observation.

This tests an existing-record, same-shape payload rewrite at native upsert phases. It does not separately establish the relocation/new-index-offset path. The `upsert-after-index` call site is reached after the native index phase even when relocation does not require changing that record's offset. Append, relocation, flush/delete faults, and crash recovery require different fixtures.

## Reader and journal observations

Before any uncached disk control, the physical reader snapshot is taken with a full native storage/cache/owner fingerprint before and after. Every positive or refused observation preserves that fingerprint and leaves native endpoints unresolved.

After WAL/data/index-phase failures, the manifest entry identity for the middle record is unchanged and its old cached Sapato still matches the existing witness. The reader accepts the complete cached chain:

```
[2:0:4:0, 1:0:1:0, 0:0:3:0]
```

After the integrity-phase failure, the entry identity is new but the cached node is old; the reader refuses `stale saved-link witness`. The native cache invalidation normally follows the injector point, so it did not execute on the injected exception.

The reader was intentionally designed to prove recorded links and current resident identity, not byte equality of every mutable TValue payload with disk. This finding does not relabel legitimate resident scalar mutations as corrupt or claim the unchanged links themselves were different. It demonstrates that the current witness cannot independently establish settled native storage after a failed write. Its documented exclusion of unhandled partial storage failures remains necessary.

In all 12 shadow fault cases, the journal's active update frame is null after the exception, and `unsupported incomplete native factory update` is already recorded before finish. The saved failure prefix contains only BASELINE/VIEW, with no TOUCH, CHANGE, or success footer: the first record's pending registration was discarded.

Before manifest replacement, finish also reports the actual cached-authority mismatch:

```
shadow={0=[0:3:0,1:2:0,2:4:0]} authority={0=[0:3:0,1:1:0,2:4:0]}
```

After manifest replacement, finish instead also reports the stale witness refusal. Both are subordinate to the unconditional incomplete-update error. No full-oracle result repairs the incremental view, and no failure session is accepted. A distinct empty fresh journal session can close after each rejection.

Only after the candidate and journal outcomes are fixed does the fixture use native Index/Data.getUncached to inspect the stored middle record. It returns a at the WAL boundary and b at the data/index/integrity boundaries. Thus the observed disk change precedes the manifest replacement in the two critical cases; the old cached a was not inferred from a disk read by the diagnostic reader. The Data overlay's existing hook preserves the native disk-read return even when a CRC disagreement is recorded internally.

The no-fault control publishes three parent materialization touches, observes the unchanged canonical b baseline, and finishes with three views and zero changes. All flags produce byte-identical successful traces. The fault datasets are isolated temporary fixtures and intentionally left quarantined with their held lease until JVM exit; no flush, release, retry, close, or recovery is used to heal a fault before reporting it. Their files are not production data.

## Scope and next action

Only the diagnostic injector overlay and fixture runner are newly compiled. Saved-link recorder, physical reader, native Data decode overlay, native Base materialization wrapper, factory update wrapper, and dirty journal remain exactly the preceding runtime classes. Their actual hashes and all 696 prior native class hashes per build are verified. Parent saved-link and serialization-failure evidence manifests are unchanged.

The earlier 30-JVM saved-link and 12-JVM serialization-failure matrices are retained, not rerun under the throw injector. Production source, defaults, and develop are untouched. The two rejected concurrent corpora remain unqualified. No performance or full-frontier claim is made.

The next concrete change is a diagnostic native-write admission guard: a physical reader must refuse while an upsert is active or after it failed, before relying on an unchanged manifest entry/witness. The guard must preserve the original native write/exception behavior and must not repair native metadata or clear a failure because a later decode appears valid. That change needs both this negative matrix and the previously successful final-publication controls rerun. Crash/IO/relocation/concurrency/reentrancy remain separate boundaries.

## Reproduction

From the worktree root:

1. `python docs/tvalue-storage-faults/build.py`
2. `python docs/tvalue-storage-faults/run.py`
3. `python docs/tvalue-storage-faults/analyze.py`

Requires frozen prior overlays under `../build`, Java 17, ECJ 3.33.0 targeting Java 8, and native resources. Committed evidence includes runner/build/run/analyzer sources, class hashes, 30 full JVM result records and stdout/stderr, 30 physical proofs, three successful replayed traces, 12 denied prefixes/errors, independent summary, and SHA-256 manifest. No preliminary JVM failure occurred.
