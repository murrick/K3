# TValue payload setter observation

This diagnostic stage extends the previous dirty-bucket observer to writes on already observed native TValue objects. Thirty-one fresh Java 17 JVMs pass their stated gates. A separate Python replay reconstructs all 7,401 native authority views across ten traces. Production sources, qualification runners, workflows, optimization defaults and previous evidence remain unchanged.

The observer now records surviving TermID changes from `setValue(Term)` and `setPersistentReferences(long,long)` when the variable ID stays unchanged. It accepts `setTVar(TVariable)` with the same variable ID. Changing the registered variable ID rejects observer qualification after the native write has completed. An uninstrumented `applyMap` TermID write remains a detected gap.

**This covers the ordered canonical ID/TermID/deletion projection. It does not establish that direct metadata mutation preserves factory canonicalization invariants.** Native hash and variable indexes are not repaired. Earlier concurrent corpus failures remain open.

Parent: `95841a8f856f5cbe1c09e6f5cd71c26172d061e6` on `experiment/3.8.0-tvalue-publication-replay`.

## Changes and native consequences

| Write on an observed TValue | Journal qualification | Native consequence checked in clean and shadow builds |
|---|---|---|
| `setValue(newTerm)` | TermID change recorded | ID lookup and variable enumeration keep the original object; semantic hash lookup can miss both old and new pairs |
| `setPersistentReferences(newTermID, sameVariableID)` | TermID change recorded | Resident references clear; lazy term hydration still works; observer scans leave the term unhydrated |
| `setTVar(sameVariableID)` | No net projection change | Original native assignment runs |
| `setTVar(otherVariable)` | Expected rejection after native write | Existing object stays in the old variable bucket; new variable bucket stays empty; semantic hash lookup misses both pairs |
| `setPersistentReferences(sameTermID, otherVariableID)` | Expected rejection after native write | Same stale variable/hash routing as above |
| Unhooked `applyMap` TermID change | Independent full oracle detects mismatch | Native payload changes and indexes remain unrepaired |
| `setValue(null)` | ID projection remains unchanged | Native `NullPointerException` occurs after clearing the resident term reference; old TermID survives |

Restoring the original term restores the original semantic hash lookup in the native fixture. A TValue checkpoint release does **not** restore an in-place payload write: it removes provisional additions, while the changed existing object survives. Nested checkpoint commit likewise retains the payload. Transient changes restored before observation emit no net delta.

Changing a variable after indexing creates a disagreement between current metadata and existing variable-bucket routing. The previous full projection discovers variable IDs from native factory iteration and then reads each ordered native bucket. It cannot serve as a complete projection contract for arbitrary inconsistent identity/index mutations. Such variable-changing setter writes are explicitly rejected rather than qualified from an incomplete view.

## Diagnostic mechanism

`build.py` generates temporary Mind, Linker, TValueFactory and TValue sources outside the repository. Existing construction/checkpoint/settlement/add/remove/promotion/visibility hooks are reused exactly. Three TValue setter wrappers save the original IDs, call the unchanged native body, and notify the observer in `finally`, including the native partial-failure path.

The session adds an `IdentityHashMap<TValue,Long>` to each context. Only successful factory hooks and bucket reads used to build the shadow register objects. The separate full oracle never registers an object or fills a missing dirty key. A same-ID unregistered probe cannot dirty the registered canonical object's buckets. Constructor setters run before registration and are ignored.

After a registered payload setter, the observer selects the previously observed route and the old/new variable IDs. It propagates by object identity to all active contexts that have observed that shared object. The native parent/child fixture verifies that changing the ancestor value through a child's inherited reference updates both views. Neither object owners nor query/current projections are rewritten.

Variable-changing writes record an unsupported-identity error after native execution. All errors accumulate until `finish`, which releases the ThreadLocal session before rejecting qualification. Expected-negative runners verify that rejection leaves the native object and native indexes intact and permits a fresh session.

Registration is diagnostic bookkeeping, not a claim of current factory membership. Strong references to formerly observed objects may remain until session end; mutating such an object can over-select a bucket, whose native contents still decide the net result. Retired context states are ignored. No new cross-thread registry is introduced.

## Validation

| Gate | Fresh JVMs | Evidence |
|---|---:|---|
| Metadata native and shadow fixtures, ON/OFF/VERIFY | 6 | 19 native assertions per fixture, identical complete native stdout; ancestor propagation, no probe invalidation, persistent payload recording and no hydration also asserted by shadow runner |
| Previous dirty journal safety, ON/OFF/VERIFY | 3 | 83 assertions per JVM; stdout and compressed traces byte-identical to preceding stage |
| Exact candidate replay, clean/shadow × ON/OFF | 4 | All 47 frozen source rows in each of four oracles, plus exact small/external rows; full native logs match prior clean logs |
| Transaction state/work, clean/shadow × ON/OFF | 4 | 20 operations per JVM; complete stdout, state and work files match each other and preceding clean evidence |
| Unsafe variable writes and unhooked `applyMap`, clean/shadow × ON/OFF | 12 | Six clean controls and six expected observer rejections; complete native stdout matches after removing rejection diagnostics |
| Native commit exception atomicity, clean/shadow ON | 2 | Original direct runner completion marker; neither runner is wrapped around its `System.exit` path |
| **Total** | **31** | All processes exit zero; all stderr files are empty; six negative cases catch and assert the required observer rejection |

All ten positive journal traces independently replay against every full native authority view. Replay extends the prior parser only to permit and count surviving TermID changes; it never repairs reconstructed state from a full snapshot. Each metadata trace has 27 views, nine changed buckets, eight surviving TermID transitions and one deferred observation. The three metadata traces are identical across flags.

Temporary hooks reverse to all four original source files byte-for-byte. The clean rebuild retains 676 prior class files byte-identically; only the diagnostic TValueDirtyJournal helper family changes. The shadow build changes the preceding eight native instrumented class files plus `TValue.class`. New helper classes are diagnostic evidence only. All Java compilation targets Java 8 using ECJ 3.33.0; execution uses Java 17.0.20. No new Java 8/21 CI run is claimed for this docs-only branch.

## Limits and next work

Public `setId`, `setMind`, `setMindId`, `apply` and `applyMap` are outside setter-hook coverage. The `applyMap` negative case establishes detection for the tested TermID change, not coverage of every persistence/owner mutation. Resident reference changes with unchanged IDs are also outside the recorded projection. Arbitrary custom TValue/Mind/factory implementations and worker-thread mutation are unqualified.

The diagnostic still scans full native state as an oracle and retains object references. There is no production consumer, inference shortcut, performance claim, scheduler guarantee, rollback or automatic canonical index repair.

The earlier two rejected overlapping-preparation corpus attempts remain archived and unqualified. This stage does not rerun or relabel them, and the earlier prepared-operand publication replay is not generalized to concurrent preparation.

Next work can inspect persistence and owner/identity mutations against the same independent authority, before deciding which additional channels a selective inference consumer would require.

Reproduce from this worktree with the existing `../tooling/ecj.jar` and repository dependencies:

```sh
python docs/tvalue-metadata-observation/build.py
python docs/tvalue-metadata-observation/run.py
python docs/tvalue-metadata-observation/analyze.py
```

Scripts, native stdout, explicit expected-rejection diagnostics, stderr, result metadata, full transaction state/work, compressed traces, build/source hashes, structural checks and machine-readable conclusions are in [tvalue-metadata-observation](tvalue-metadata-observation/). [summary.json](tvalue-metadata-observation/summary.json) retains the old corpus failure status. [manifest.sha256.json](tvalue-metadata-observation/manifest.sha256.json) covers every new evidence/report file except itself.
