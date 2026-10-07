# Owner-preserving resident TValue observation

The diagnostic journal now reads native in-memory TValue state without rebinding owners, hydrating units or initializing native indexes. Fifty-six fresh matrix JVMs meet their stated expectations. Six owner/control JVMs each execute 27 assertions, and independent replay reconstructs all 9,093 full authority views across 18 traces.

**The qualified scope is serial, explicit sessions over native memory-only Step/TValue/Escalera/TValueFactory data with stable identity and no persistent connection.** Sapato nodes, unresolved units and custom step/value implementations reject qualification without invoking their callbacks. This is diagnostic evidence, not a production API or selective inference consumer.

Parent: `ca2d1b081d5e49cbe98bfe7096abe8eb97d1df22` on `experiment/3.8.0-tvalue-persistence-boundaries`. Production sources, qualification runners, workflows, optimization defaults and all previous evidence are unchanged.

## What changed

The preceding journal scanned through native iterator/lookup paths, which call `Step.getData(mind)` and assign the supplied Mind as the TValue owner. The preceding witness showed that a root observation could change `pack()`'s deletion flag from 1 to 0 while the recorded root projection stayed unchanged.

This stage replaces three diagnostic read paths: dirty bucket reads, full projection capture, and child value enumeration in the promotion hook. They use `ResidentTValueRead` instead of native iterator/lookup calls. The existing temporary native hooks and six setter wrappers remain byte-identical to the preceding stage; all behavioral changes are in diagnostic helpers.

| Operation | Before this stage | Resident reader |
|---|---|---|
| Read a linked unit | `getData(mind)` may rebind owner | Exact native `Step.getData()` returns attached data without a Mind argument |
| Read an ordered variable bucket | Native `forEach` initializes indexes and performs native lookups | Read copies of existing routing metadata and native lookup state |
| Uninitialized variable index | Native `ensureIndex` writes acceleration metadata | Simulate the native oldest-first reverse-chain order in temporary data |
| Valid cache fast index | Native lookup uses `memoryById` | Use the actual table, including missing entries; do not repair it from the chain |
| Invalid cache index | Native lookup rebuilds from linked state | Compute a temporary resident ID map without changing native fields |
| Persistent or custom node | Native methods can hydrate or invoke callbacks | Reject before calling data/next methods on the unsupported node |
| Child promotion observation | Iterate the child factory through native reads | Enumerate attached resident child values without owner reassignment |

Reflection reads native fields; it never assigns them. Exact native Step, TValue, Escalera and TValueFactory implementations bound the read contract. Storage connections and any Sapato node are rejected, including a Sapato that might already have attached data. No IBase method is called.

The reader preserves the distinction between linked-chain enumeration and cache lookup metadata. If native get would reuse a valid fast table, the observer uses that table rather than constructing a cleaner replacement. The lookup-alias negative control removes an entry from the native fast table while leaving the chain and variable routing intact. Both resident algorithms and actual native `forEach` omit the value; the journal's full oracle detects the untracked change and rejects qualification without restoring the entry.

## Independent checks

Incremental bucket reads recursively collect a variable's IDs from parent to child, deduplicate in native LinkedHashSet order, then apply the target context's native lookup semantics. The separate full algorithm flattens routing across all layers from base to leaf and reconstructs every visible variable bucket at once. It does not use dirty state or copy a full result into the shadow projection.

These algorithms share validated raw-state primitives, so their agreement alone is not an independent proof of those primitives. The new native fixture separately compares resident root and child projections with actual native iteration/`forEach`, after completing the purity checks. Existing frozen candidate, transaction and ordered-publication controls supply broader native comparisons. Nine preceding metadata/dirty/persistence logs and compressed traces remain byte-identical.

The owner fixture first establishes a root TValue that is deleted only in a child and whose owner reference points to that child. Root and child observation preserve that reference and keep the packed deletion flag at 1. Then an explicit native root enumeration control rebinds the owner to root and changes the packed flag to 0, confirming that the source behavior still exists and was not disabled by the experiment.

Before and after each tested observer boundary, the fixture compares a fingerprint of resident value IDs, owner references/IDs, resident term/TVariable references, factory lazy-index flag, local routing, action, roots, cache index flags, memory/persistent/predecessor metadata, mutation counter and checkpoint depths. This is a measured field fingerprint, not a claim to snapshot every field of the JVM heap. It checks both lazy and already initialized indexes, ancestor/child views, deferred checkpoint observation, native rollback and shared payload writes.

## Validation

| Gate | Matrix JVMs | Result |
|---|---:|---|
| New owner/read controls, clean/shadow × ON/OFF/VERIFY | 6 | 27 assertions each; complete native stdout equal; owner and packed deletion preserved; actual native root/child order matches |
| Previous metadata, dirty lifecycle and persistence gates, shadow ON/OFF/VERIFY | 9 | 19, 83 and 22 native assertions per respective fixture; complete logs and traces byte-identical to preceding stage |
| Unsupported/resident-gap controls, shadow ON/OFF/VERIFY | 27 | Nine cases in each mode; expected rejection, zero trap/storage callbacks and closed session; no native state repair |
| Exact candidate replay, clean/shadow × ON/OFF | 4 | Four frozen complete 47-source oracles and exact small/external rows; full native logs match preceding controls |
| Transaction state/work, clean/shadow × ON/OFF | 4 | 20 operations each; complete stdout/state/work match preceding clean evidence |
| Ordered native publication, clean/shadow × ON/OFF | 4 | Six fixed orders per JVM, 90 assertions each; both six- and seven-Rule native outcomes retained exactly |
| Native commit exception atomicity, clean/shadow ON | 2 | Original direct runner completion markers |
| **Total** | **56** | All matrix processes exit zero with empty stderr; negative cases assert the required rejection |

The nine negative modes are native Sapato, callback-trap Sapato, callback-trap Step, callback-trap TValue, unresolved attached data, a linked cycle, an IBase connection proxy, a raw untracked TermID write and a raw fast-lookup alias mutation. Unsupported classes reject before callback invocation. The two untracked-write cases are rejected by the full projection oracle; it never repairs them. Failed sessions relinquish ThreadLocal ownership and permit a fresh session.

All 18 compressed traces independently replay every one of 9,093 authority views and verify dirty selections, counts, retirement and generation boundaries. The three new owner traces are byte-identical across flags: each has 14 views, nine changed buckets and one deferred observation. A root owner-preservation read emits no false canonical bucket change.

The clean rebuild retains 682 preceding class files byte-identically, excluding the changed diagnostic journal helper family. The shadow rebuild also retains those same 682 preceding shadow class files, including all temporary native hook classes. The nine native instrumented class files remain the same as the preceding stage. Reversing wrappers and hooks still recovers all four original native source files byte-for-byte. ECJ 3.33.0 targets Java 8; execution uses Java 17.0.20. This docs-only branch does not claim a new Java 8/21 CI run.

An initial clean fixture incorrectly opened a dirty-journal session across native mutations despite the clean build having no mutation/checkpoint hooks. The full oracle correctly rejected its stale shadow. That single setup failure, original runner source and logs are archived in `rejected-attempts` and excluded from the 56-case matrix. The corrected clean control invokes resident full-read APIs; the shadow control exercises the actual hooked journal. Each qualified matrix case then ran once.

## Remaining scope

The owner side effect is removed from the tested resident observer paths. Native `getData(mind)` behavior is unchanged, and the journal does not undo owner changes made by legitimate engine operations. Owner references/IDs and resident-reference-only changes remain outside the event schema, which records ordered TValueID/TermID/context-deletion bits.

Persistent storage, Sapato data, unresolved hydration, custom Mind ancestry and arbitrary extensions are unqualified. The reader depends on this exact native field layout. Serial session controls do not qualify concurrent worker observation or inference, and fixed serial publication orders do not generalize the earlier prepared-operand capture/replay to concurrent preparation.

There is no native index repair, ownership compensation, production consumer, optimization default change, performance claim or develop merge. Full scans and strong session references remain diagnostic costs. The two preceding rejected overlapping-preparation corpus attempts remain archived and unqualified; this stage does not rerun or relabel them. The previous owner-mutating reader's negative evidence is retained.

Next work can address persistent boundaries without introducing hydration or storage callbacks into observation, while retaining explicit rejection where a read-only projection cannot be established.

Reproduce from the worktree with the existing compiler and repository dependencies:

```sh
python docs/tvalue-owner-pure-observation/build.py
python docs/tvalue-owner-pure-observation/run.py
python docs/tvalue-owner-pure-observation/analyze.py
```

The [evidence directory](tvalue-owner-pure-observation/) contains scripts/helpers, native stdout, expected-rejection records, stderr, execution metadata, full transaction state/work, compressed traces, source/class hashes, structural checks and the archived invalid control. [summary.json](tvalue-owner-pure-observation/summary.json) scopes the resident owner result and retains the old corpus failure status. [manifest.sha256.json](tvalue-owner-pure-observation/manifest.sha256.json) covers every new report/evidence file except itself.
