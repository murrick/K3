# Whole-operation resident admission guard for native clear and close

Diagnostic branch only. Parent: `f6ac32d2e86129d39b646e09a39d8a19889f7d4f` (delete/flush guard). Production Java, defaults, and `develop` are unchanged.

The preceding guard covers the flush body, but native `Base.clear` and `Base.close` continue mutating after flush returns. This experiment wraps each whole operation. An active or failed operation cannot be admitted by the resident physical reader, including an empty manifest or checkpointed WAL. Failure remains sticky within the explicit caller-thread recorder activation.

## Exact native nesting

Native nonempty clear calls flush twice; close calls it once. Only those three native call sites are rewritten to private helpers which execute the existing, unchanged flush body under `RecordedLinks.beginNativeFlush(base, parent)`. The special entry requires an exact same-Base gate at depth one, with active operation `clear` or `close`. It retains the outer operation label during the child frame and attributes any child failure to that outer operation. Other nested `beginMutation` entries keep the preceding conservative failure behavior. This is not general reentrant mutation qualification.

Wrappers preserve native body statements, phase ordering, counters, cache invalidation, checkpoint calls, and exception behavior. The build removes the wrappers, call-site replacements, helpers, and diagnostic phase probes and recovers the preceding Base source byte for byte. Removing the three fixture hook lines recovers the native halt injector. The production halt path is never executed. Earlier upsert/get/delete/flush wrappers, Data provenance, reader, journal, factory, and native class families are unchanged.

## Controlled boundary matrix

Each cell runs clean/shadow with ON/OFF/VERIFY, in a fresh JVM, with actual native Base, manifest, index, WAL, cache, and decoded TValues. The fixture prepares three records in a known-endpoint resident chain and a pending WAL. It neither retries nor reopens faulted datasets. Data cache is zero; resident Base cache is enabled.

| Operation | Scenarios | JVMs |
|---|---|---:|
| clear | success; empty success; four flush checkpoints in each of the two flushes; after data/index/manifest clear, cache clear, last-ID reset, endpoint invalidation | 96 |
| close | success; four flush checkpoints; after compaction, cache clear, index close, data close, endpoint invalidation | 60 |
| preceding delete/flush matrix | same 60 native boundary scenarios | 60 |
| preceding settlement, serialization failure, upsert fault matrices | same native outcomes and physical/journal evidence | 72 |
| **Final total** | | **288** |

All final assertions pass. At every reached operation checkpoint, a before/after native fingerprint verifies read-only refusal: **1,044 new checks**, **1,440 including regression**. Native manifest/cache/handle state, flush count, and pending-WAL state are asserted independently. Successful clear returns an open, empty Base and two native flush attempts; empty clear has zero flush attempts. Successful close has one native flush, cleared cache, and closed Data. Injected close faults inside its original try block remain `IOException` with the native string conversion; later injected faults retain their original runtime exception class and message.

The unchanged reader checks closed Data before checking the diagnostic gate. At close checkpoints after Data closes, the fixture therefore checks the existing closed-data refusal and separately checks the still-active or failed gate. This distinction preserves the reader's established priority; it does not claim that the public physical reader reports a guard-specific error for an already closed file.

All **42 successful journal traces / 225 authority views** remain byte-identical and pass independent replay. All 15 denied prefixes, native outcome rows, physical proofs, and failure texts match the preceding fixtures. These successful traces belong to regression; the new 156 physical-only fixtures do not establish full journal settlement after clear or close. Starting/finishing an empty journal session cannot erase a recorded operation failure.

## Recorded preliminary fixture error

The first attempt ran 97 JVMs: 96 clear cases passed, then the first successful close case failed because the fixture incorrectly expected an active-operation message after Data had already closed. The reader correctly refused with its earlier closed-data check; the fixture converted that assertion into an unexpected native outcome. The full attempt, original fixture, build hashes, and failure are retained in `initial-closed-check-fixture-attempt/`.

Only that fixture check was corrected. Base, recorder, and injector class hashes are identical across attempts. The complete final matrix and regression were then rerun: **385 executed JVMs**, including the 97 archived preliminary runs. No failure is counted as a successful qualification.

## Limits and next scope

Qualification is explicit-activation, quiescent, single-thread diagnostic observation of these concrete controlled phase boundaries. Injected exceptions occur after native phases; this is not a claim about real I/O failures, process crash, durable recovery, concurrency, bulk mutations, relocation, or arbitrary reentrancy. Disabling/re-enabling the recorder is not qualified recovery. Native source recovery demonstrates body preservation, not general proof over all storage failures.

Earlier two rejected corpus attempts and all earlier negative evidence remain frozen; full corpus semantic equality is still unqualified. Next scope: reindex and its cross-Base writes, including what the source Base and destination Base can admit during an interrupted copy.

Evidence: `tvalue-clear-close-guard/summary.json`, `build-validation.json`, SHA-256 manifest, reversible build script, fixtures, per-JVM commands/logs, and independent analyzer.
