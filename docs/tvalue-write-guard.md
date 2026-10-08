# Diagnostic physical admission guard for active and failed native upserts

Parent: `9e4e683f2eb3449c896ccc0a639c1208fcc10205`.

## Result

The diagnostic physical reader now refuses during a native Base upsert and after that upsert failed, before trusting an unchanged manifest entry or saved-link witness. The previous boundary that admitted old cached records after an incomplete data/index-phase write is closed within the explicitly activated, caller-quiescent recorder scope. Native writes, returns, exceptions, and partial effects remain observable and unchanged; the guard does not repair storage.

72 final JVM cases pass: 30 composite/scope cases, 12 serialization success/failure cases, and 30 native upsert throw-probe cases. Independent replay validates 42 successful traces and 225 authority views. Every successful trace is byte-identical to its corresponding pre-guard trace. Fifteen denied failure prefixes remain byte-identical and are not counted as successful traces. The fault matrix performs 228 read-only refusal checks inside actual native upsert bodies.

| Boundary | Before guard | With guard |
| --- | --- | --- |
| Fully successful recorded root update | Qualified | Same successful traces |
| Reader called inside native upsert | No write-phase gate | Refused: active native upsert |
| Upsert fails after WAL/data/index phase | Old cached chain could qualify physically | Refused: failed native upsert |
| Upsert fails after integrity | Refused by stale witness | Refused by failed write gate before witness admission |
| Native serialization callback throws | Factory journal refused; physical partial base could qualify | Same native exception; both factory journal and physical admission refuse |
| Later native decode after failure | Could produce another witness | Does not clear failed gate |
| New journal session, same failed Base | Recorder witnesses survive session boundaries | Failed gate also survives; observation/finish refused |

## Implementation

`RecordedLinks.State` gains an identity-keyed per-Base gate with an active count and sticky failure bit. New beginUpsert/endUpsert hooks operate only on that diagnostic thread-local state. They do not inspect the IStep argument, invoke native getters or semantic callbacks, serialize, access disk, or change Base fields. Inactive recording remains inert. A nested upsert conservatively marks the gate failed, but general reentrancy is not qualified.

The temporary diagnostic Base overlay retains the previous native get/materialization wrapper. It adds a thin wrapper around native Base.add:

```
Object token = RecordedLinks.beginUpsert(this);
boolean success = false;
try { guardedAddBody(one); success = true; }
finally { RecordedLinks.endUpsert(token, success); }
```

The complete prior native add body is moved unchanged into guardedAddBody. Its writeCount increment, WAL/data/index/integrity operations, original fault points, endpoint invalidation, and cache invalidation remain in their original sequence. The wrapper marks success only after the body, including cache invalidation outside the storage locker, returns normally. Native Base.update already delegates to add and is covered without an additional wrapper. Removing the upsert wrapper recovers the prior materialization Base source exactly; removing the unchanged get wrapper additionally recovers the tracked native Base source exactly.

EndUpsert decrements the active count and retains failure on any exceptional body exit. A later successful write does not reset this bit. Guard bookkeeping problems are recorded diagnostically rather than deliberately substituting an exception for the native body result. There is no clear-failure or metadata-repair API. Ordinary memory/resource exhaustion and hostile modification of instrumentation are outside these tests.

The new physical reader calls requireSettled after its exact Base/open Data checks and before examining integrity entries, empty-state admission, known endpoints, or resident witnesses. Active and failed gates take precedence over recorder errors, and recorder errors also refuse admission before known-endpoint paths. This covers both known and unresolved native endpoint states. The existing full resident/link/identity/witness traversal remains unchanged after admission. The reader never writes native endpoints or indices.

The guard is keyed by native Base identity and lives through journal session end/begin within the same explicit RecordedLinks activation. It is thread-local bookkeeping, not a storage lock or durable recovery marker. Disabling recording discards its state; doing so is not evidence of native recovery and is not a qualified way to read a failed Base. Reusing a faulted Base after recorder reactivation, process restart, or native recovery requires separate validation. No general fault-reset/reopen admission is claimed.

## Qualification matrix

The composite runner and saved-link scope fixture are copied byte-for-byte from the qualified saved-link stage. All 24 composite clean/shadow/flag cases and all six scope cases pass. Held-sibling acceptance, native non-sequenced rejection, live user rejection, original analyzer exception, final quiescent root update, fresh sessions, native endpoint non-repair, native lookup identity, unsaved links, stale manifest entries, incomplete residency, inactive/late activation, and closed Base retain their previous outcomes. All 36 successful traces remain byte-identical.

The original serialization runner is reused with its post-failure physical expectation tightened: the already decoded first record no longer admits the failed Base. The original IllegalStateException/message, exactly one native pack callback, two native write attempts, retained partial root, discarded pending materialization, original failure prefix, and held-lease cleanup are preserved. Three success traces remain byte-identical, contributing 51 views. All three shadow failure errors retain the pre-finish unconditional incomplete factory-update error and now additionally report failed native upsert instead of full-oracle partial projection mismatch. No failure is suppressed or converted into a positive trace.

The storage fault fixture retains the native no-fault and four existing-record upsert phases: after WAL, data, index phase, and integrity. A temporary injector adds the same separate diagnostic throw probe at native hit call sites; removing it recovers the original process-halt injector source. Actual halt/crash recovery is not executed or qualified. Faults are thrown at the second native record, preserving the diagnostic exception and all previously measured native partial writes/cache/manifest/disk outcomes.

The throw probe now calls the physical reader at every native upsert checkpoint while armed, including the no-fault control. Each call must refuse active native upsert and preserve the entire direct native storage/cache/owner fingerprint. There are 12 checks per no-fault run and 5/6/7/8 at the selected WAL/data/index/integrity fault runs, totaling 228 across the final 30 JVMs. The first write's checkpoint calls exercise the known-endpoint state; later writes also exercise unresolved endpoints. No journal event is created by these fixture observations.

After any fault, physical observation must refuse failed native upsert with unchanged native fingerprint. All 12 shadow failure prefixes still contain only the original BASELINE/VIEW and no materialization TOUCH/CHANGE or success footer. The journal's existing update failure is checked before finish, and finish independently also refuses the Base through the new gate. The explicit uncached disk control still returns the same original native value at every fault phase. Another physical snapshot afterwards still refuses, and a new journal session observing that same Base also fails twice (observe/finish). At the integrity phase the native decode matches completed manifest bytes; it still cannot clear the failed write gate. These checks demonstrate failure retention within activation, not a qualified recovery procedure.

All three no-fault storage traces remain byte-identical and contribute nine views. The temporary fault datasets retain their held lease and are quarantined until JVM exit. Fixture-native controls occur only after the primary candidate/journal outcome. No retry or flush repairs a failed journal.

## Retained setup mistake and build evidence

An initial fault fixture attempt ran nine cases successfully and failed its tenth, the first WAL shadow case, in a new-session negative check. The generic positive observe helper called ResidentTValueRead.fingerprint before journal.observe; the new guard correctly refused that fingerprint, so the fixture did not reach its intended journal error capture. The guard implementation was not changed. The negative fixture now fingerprints native Base directly around journal.observe and checks the resulting recorded refusal.

All ten preliminary JVM records/logs/partial evidence, original fault runner source, and initial build hashes are archived under initial-fault-session-fixture-attempt. One raw failure prefix from the aborted run is retained as recorded. The final full 30-case fault matrix passes. The preceding 42 composite/scope/serialization runs were not repeated: their runner and all guard/reader/Base/recorder classes are byte-identical between builds; only the fault runner's new-session fixture body changed. Analysis verifies those compiled hashes explicitly. No negative boundary or oracle check was waived.

Only diagnostic Base/RecordedLinks/physical-reader overlays and fixture runner families are newly compiled. The parent Data decode overlay, factory update wrapper, dirty journal, and old native classes are hash-verified unchanged. Prior native classes remain on disk; the new Base deliberately supersedes its prior class at runtime. No tracked production source or flag default changed. Parent saved-link, serialization-failure, and storage-fault evidence manifests are verified unchanged.

## Limits and next boundary

This is a trusted, explicitly activated, single-thread diagnostic guard for Base.add/update. It is not a production optimization, performance measurement, durable transaction marker, or concurrency protocol. Caller quiescence is still required; Base cache invalidation remains outside its locker. General nested/worker-thread writes, arbitrary I/O failure, process crash/recovery, relocation, deletes, flush faults, and recovery/reset admission are unqualified. The two older rejected overlapping concurrent corpora remain retained and unqualified. Full inference-frontier completeness is not claimed.

The next analogous boundary is delete/flush: they can also change native index/data/integrity state or interrupt publication outside Base.add. Their source sequencing and fault points must be reviewed before extending this gate, then tested separately while preserving the successful publication controls. Simply treating a later decode or new journal session as recovery remains unsupported.

## Reproduction

From the worktree root:

1. `python docs/tvalue-write-guard/build.py`
2. `python docs/tvalue-write-guard/run.py`
3. `python docs/tvalue-write-guard/analyze.py`

Requires frozen prior classes under ../build, Java 17, ECJ 3.33.0 targeting Java 8, and native resources. Sources, generated-overlay build commands and hashes, 72 final JVM records/logs, 42 successful traces, 15 denied failure prefixes/errors, 12 same-failed-Base new-session errors, physical proofs, archived initial fixture attempt, independent replay summary, and SHA-256 manifest are committed under docs/tvalue-write-guard.
