# Saved-link provenance for diagnostic resident endpoint inference

Parent: `5bd5d7416d79d87a3a7203f3b18f9f4e0b78672e`.

## Result

The accepted composite Mind commit now completes its final quiescent root update under the dirty journal without native endpoint repair, when the diagnostic recorder was explicitly activated before the relevant native disk decodes. Native `rootId/topId` remain null at the post-release qualification check. The previous journal and factory hooks are unchanged.

30 fresh JVMs (24 composite settlement cases and six scope fixtures; clean/shadow × flags ON/OFF/VERIFY) pass. Independent replay validates 36 successful traces and 165 authority views. All 33 previously successful settlement/fresh/rollback-finalization traces remain byte-identical. The three newly successful accepted-finalization traces each contain five views, one materialization TOUCH, no CHANGE, and one held-child retirement. No full-oracle result is used to repair or admit the incremental projection.

| Boundary | Evidence / outcome |
| --- | --- |
| Accepted child with held sibling | Parent gets exactly its original native addition |
| Rejected or exceptional child | No false parent delta; native outcome preserved |
| User rejection | Rejected child remains live until native release |
| Final accepted root update | Journal succeeds; native endpoints remain unresolved |
| Valid recorded resident chain | Read-only local endpoint inference succeeds |
| Unsaved complete resident reordering | Refused: resident link differs from saved witness |
| Native rewrite with controlled old-cache reinsertion | Refused: stale manifest-entry witness |
| Missing resident node | Refused; no cache warming or disk read |
| Recorder inactive or activated after decode | Refused; no retroactive witness creation |
| Closed Base | Refused |

## Why the previous candidate was insufficient

The parent stage demonstrated a complete, acyclic, exact-native resident chain 7 → 40 → 91 whose stored chain was 91 → 7 → 40. Manifest keys, lengths, and CRCs stayed unchanged under public unsaved `Sapato.setNext()` calls. Integrity entries do not expose separate saved links. That topology-only negative evidence and its strict original reader are retained unchanged.

This stage records links from a different source: the existing native `Data.readOne()` disk decode. A temporary diagnostic Data overlay adds exactly one call after successful `step.apply(packet)` and `step.setSize(...)`, while the original `buffer` is still available. Removing that one line recovers the native source byte-for-byte. The overlay is compiled separately; no tracked production source is edited.

`RecordedLinks.decoded(this, step, buffer)` ignores inactive recording and unsupported classes. For exact Data/Base/Sapato/TValue with matching step/unit IDs and the actual active Base.data identity, it checks the read bytes' length and CRC against the current native manifest entry. It then stores a witness keyed by exact Sapato object identity: Base identity, ID, next ID, hash, unit identity, and the current manifest entry object identity. Hook exceptions are captured as diagnostic errors rather than thrown into the native read. An unresolved-endpoint reader refuses recorded errors. This is trusted instrumentation provenance, not a security boundary against code deliberately calling or altering the diagnostic recorder.

Explicit `RecordedLinks.enable()` creates a caller-thread-local recorder before fixture native decodes; `disable()` clears it in finally. It is not implicitly enabled by journal begin, and it does not record arbitrary caller `Sapato.apply`, pack, getters, resident setters, or cache hits. A previously decoded node has no witness if recording was inactive. Retained witnesses are bounded by this explicit activation lifetime, not by a production cache policy.

## Reader extension

The new diagnostic `ResidentPersistentRead` preserves the old known-endpoint path. For nonempty bases with both native endpoint fields null, it copies the actual resident cache by iteration, then requires a witness for every manifest ID. Witness validation requires unchanged Base/ID/manifest-entry identity, unchanged node hash and unit identity, and resident `next` equal to the recorded saved `next`. It derives one local root/tail from those verified links, rejects dangling links or ambiguous endpoints, and uses the existing exact-class, ID, full-traversal, cycle, and complete-universe checks. A partially resolved endpoint pair is refused.

The reader never changes Base endpoints, native lookup metadata, ownership, indices, or caches. It performs no disk read, serialization, hydration, or extension callback. Fingerprints demonstrate unchanged native storage counters, cache order/identities, and unit owners across positive and refused reader calls. Recorder state is diagnostic bookkeeping outside that native fingerprint.

TValue payload mutations are not rejected merely because their serialized payload differs from the recorded bytes: the witness proves the saved physical links and node identity, while the existing journal tracks current resident canonical TValue tuples and scalar setters. It is not a proof that every current resident payload equals disk bytes. CRC retains the native integrity mechanism's limitations; no cryptographic or collision-free equivalence claim is made.

A native successful rewrite replaces its manifest entry identity, invalidates its cache record, and makes an old witness ineligible. A new native decode is required to establish a new witness. The stale-entry fixture deliberately reinserts the old node into the cache after a real native rewrite to exercise that refusal; this controlled reinsertion is not claimed to occur in ordinary native publication.

## Composite qualification

The native fixture is copied from the parent settlement stage, with explicit recorder activation and an assertion that accepted final release has not repaired native endpoint fields. Its previous expected accepted-finalization refusal is replaced by required successful journal finish; any finish error fails the fixture. The same real held sibling, stored rule generations, non-sequenced rejection, live user rejection, and original analyzer exception are used. Child setup and native commit/release behavior are unchanged. Native outcome rows match the preceding stage in all 24 cases. Existing settlement, rollback, and fresh-session traces remain byte-identical across the stage and flags.

Data instrumentation, the extended physical reader, recorder, and fixture runners are the only new class overlays. All prior 696 native classes per clean/shadow mode and the old update-boundary journal/factory classes are hash-verified unchanged on disk. The new overlay Data supersedes its prior class at runtime; the new reader supersedes the old physical helper. The older native Base materialization wrapper and post-successful factory update wrapper are reused unchanged.

## Scope, retained failure, and next boundary

One preliminary scope JVM failed during fixture setup: calling native Base.update on an unhydrated decoded TValue reached the original pack-time NullPointerException because its Mind was null. Its stdout/stderr, invocation, and original build hashes are archived under `initial-scope-fixture-attempt`. The fixture now explicitly calls native `getData(owner)` before that test rewrite. This native preparation is outside reader observation. No journal error was waived or native exception changed. The final six scope runs all pass 21 assertions. A local Python scope-dispatch NameError before any JVM launched was corrected without changing test expectations.

This qualification requires trusted diagnostic instrumentation, explicit activation before decode, caller-enforced quiescence, and absence of unhandled partial native storage failures. Native Base cache invalidation still occurs outside its locker: the locks do not prove concurrency safety. The recorder is thread-local; worker-thread decodes, reentrant callbacks, general reopen admission, arbitrary I/O/crash/corruption recovery, and full inference-frontier completeness are not qualified. Existing post-failed factory-update journal rejection is not removed or suppressed. Its old evidence is preserved; that matrix was not rerun under the new Data/reader overlays and no new failure equivalence is claimed.

The previous 83-JVM regression evidence, strict topology-only negative stage, and two rejected overlapping concurrent corpora are retained, not rerun or relabeled. Production, defaults, and develop are unchanged. This is a scoped diagnostic publication result rather than a production optimization or measured performance gain.

The next useful boundary is to qualify the new recorder/reader under incomplete native factory updates, ensuring that saved-link witnesses cannot turn a failed update into a successful journal session. That negative matrix must be run with the new overlays rather than inferred from the older failure evidence.

## Reproduction

From the worktree root:

1. `python docs/tvalue-saved-links/build.py`
2. `python docs/tvalue-saved-links/run.py`
3. `python docs/tvalue-saved-links/analyze.py`

Requires the frozen prior class directories under `../build`, Java 17, and ECJ 3.33.0 at `../tooling/ecj.jar` targeting Java 8. Sources and scripts, class hashes, 30 full result records, stdout/stderr, 36 complete replayed traces, accepted-prefix diagnostics, archived preliminary failure, summary, and SHA-256 evidence manifest are committed under `docs/tvalue-saved-links/`.
