# Native persistent TValue lookup boundary (3.8.0 diagnostic)

Parent: `7bb814c706e790c825fb0a69b1e01084c27a8e1e`.

The read-only diagnostic now reproduces connected root and child TValue
enumeration for a fully resident, current native DUMB Base under externally
quiescent storage and payload state. It preserves owner pointers, native lazy
indices and storage/cache fingerprints. Six new storage JVMs and all 56 prior
memory JVMs meet their stated expectations. However, the existing dirty journal
misses setter routing when native rematerialization replaces a TValue object.
The full oracle rejects that case in three shadow JVMs. General persistent
dirty-journal coverage remains **unqualified**.

## Native lookup differs from the physical chain

| Native path | Diagnostic reconstruction |
| --- | --- |
| Valid Escalera `memoryById` | Use the actual table; never repair it from the chain |
| Invalid Escalera index | Simulate native traversal into temporary memory/ID maps |
| Persistent membership | Resolve against the actual fully resident current Base cache |
| Root factory `connection` fallback | Include resident storage records even when persistent membership is absent |
| Child factory without `connection` | Missing persistent membership remains a miss |
| Lazy variable index | Reverse the native newest-first chain into temporary oldest-first routing |
| Initialized variable index | Copy actual local routes, including their native ordering |
| Layered variable enumeration | Parent IDs first, then child IDs, deduplicated in historical order |

The fixture deliberately removes one persistent membership after native index
initialization. Root native `forEach` still finds the TValue through connection;
child native `forEach` does not. Both results match the diagnostic, and the
diagnostic preserves the incomplete table. This prevents a plausible but wrong
chain-based lookup repair.

`WalkIterator` has another effect: when its root is Sapato, its constructor
replaces root data with the current storage lookup result. The diagnostic
checks native Sapato anchors against current cached data identity, physical
next reference and hash, and refuses a stale anchor. It never performs that
replacement. An explicit native iterator control can refresh the native anchor;
the diagnostic then reads it without further changes.

## Admission and purity scope

The new stage supplies a replacement **diagnostic helper class** on a separate
classpath. No native source, native hook, existing journal implementation or
production distribution is changed. The old helper and all historical evidence
remain committed unchanged. Only `ResidentTValueRead.class` differs among
preceding diagnostic classes; its Layer class is byte-identical. All 696 classes
in each preceding clean/shadow directory are SHA-256 verified before overlay
compilation; preceding physical-reader helpers are also verified.

Storage admission requires exact native Mind/User/DB/Base/Data/Sapato/TValue
classes, a matching cache Mind/schema binding, and agreement between the actual
User storage registry and DB base registry. A root connection, when present,
must be that same active Base object. The preceding physical snapshot validator
requires open native Data, resolved endpoints, a complete already resident
chain and exact integrity ID coverage. No storage lookup, callback, endpoint
resolution, hydration, index initialization or owner compensation occurs inside
the new reader. Unknown Mind entry points fail before an overridden
`getTValues` can run; three entry controls record zero callbacks.

Caller-enforced exclusion of storage operations and payload mutation is still
required. Base cache invalidation occurs outside its storage locker; the helper
locks do not establish a concurrent snapshot. Reflection is tied to this source
layout and normal native containers. No arbitrary extension/container or
concurrent worker coverage is claimed.

The fixture combines the previous factory/owner/resident-reference fingerprint
with the real Base/Data fingerprint. These cover lazy/routing/cache metadata,
roots, checkpoints, owner/term/variable reference identities, Base/Data counters
and both LRU orders. Identity hashes are compared only within one JVM, not used
as a complete heap proof. The full oracle and incremental bucket algorithms
share validated raw-state primitives; separate actual native iterator/forEach
controls bound common-mode risk rather than establishing a formal proof.

## Rematerialization exposes an identity-registration gap

The final fixture uses native `User.setProperty("cache.data.size","0")` before
DB initialization. Base caching remains enabled and holds the full resident
phase. This matters because Base and Data cache different things: with the
default Data offset cache, clearing only Base can return the same decoded
Sapato/TValue identity. With Data caching disabled, a later Base miss decodes a
new object through the real native storage path.

The negative sequence is:

1. Begin a shadow session and register the existing resident objects through
   its normal baseline capture.
2. Clear Base cache using the native method and warm the same persistent IDs.
   The diagnostic refuses the stale native anchor without repairing it.
3. Refresh the anchor using an explicit native factory iterator. The reader
   matches the current native projection and remains pure.
4. Call native `setPersistentReferences` on a rematerialized TValue, keeping its
   variable ID and changing only its TermID. The existing hook runs, but that
   new object identity has not been registered by a dirty read or baseline.
5. Observe and finish. The independent full projection sees the change and
   rejects the stale incremental projection. The native write remains intact.
   A subsequent fresh session closes successfully after fixture restoration.

The three archived errors are identical, with the surviving transition for
TValue 0 from TermID 2 to 4. Neither the full oracle nor a snapshot fallback
registers or repairs the unknown object. A new materialization/lifecycle
contract is needed before dirty consumers can rely on persistent coverage.
Supported stable-resident reads do not establish such a contract.

## Qualification

The final matrix has 62 fresh JVMs, all exit zero with empty stderr and their
expected markers. The six storage cases use clean/shadow builds and eight flags
ON/OFF/VERIFY: clean cases pass 44 assertions; shadow cases pass 45, including
the required full-oracle rejection. Their native behavior rows are identical.
The positive phase exercises lazy and initialized roots, a persistent parent
with local child additions, contextual deletion, owner preservation, local
checkpoint rollback and registered shared-payload writes.

The fixture opens a real empty DB through native `Mind.useStorage`, creates
native resident Rules/variables/terms/TValues, and publishes TValues through
native factory `update`, Base flush and actual schema storage. It holds an
independent native sibling reservation during intermediate child settlement,
so root pack/update occurs only on the final release after all diagnostic
sessions close. The final reservation count is zero. These are synthetic
bindings without active term-bearing rules; final native pack may remove them.
This is a factory lookup/materialization qualification, **not** a full semantic
database close/reopen, persistent publication or complete corpus qualification.
The preceding physical-reader stage independently covers real file reopening.

All preceding 56 memory native logs and 18 traces are byte-identical. These
include the 27 owner assertions, prior metadata/dirty/persistence gates with
19/83/22 assertions, 27 required negative boundaries, four 47-source candidate
oracles, full 20-operation transaction state/work/stdout comparisons, four
six-order publication controls with 90 assertions each, and two native commit
exception gates. Independent committed trace replay verifies 24 complete
successful traces and 9,138 authority views; failing sessions are represented
by their retained error messages rather than counted as successful traces.

Three preliminary fixture failures are retained under `setup-attempts`: native
storage opening initializes the variable index; last-reservation settlement
packs unsupported synthetic bindings; and a retained Data cache preserves
object identity after Base cache clear. The final fixture uses native generation
reset, a native sibling reservation and explicit Data cache configuration to
establish the intended test boundaries. Six successful storage JVMs before the
public Mind entry guard was added are retained separately. After that final
guard and its callback controls, the complete 62-case matrix ran once. No frozen
corpus failure was waived, replaced or retried into acceptance.

## Reproduction and next boundary

Prepare preceding owner-pure clean/shadow directories and the physical-reader
helper directory from their committed builders in isolated worktrees sharing
`../build` and `../tooling/ecj.jar`, then run:

```
python docs/tvalue-persistent-lookup/build.py
python docs/tvalue-persistent-lookup/run.py
python docs/tvalue-persistent-lookup/analyze.py
```

Java 8 target via ECJ 3.33.0; runtime Java 17.0.20. The stage manifest covers the
report, source, scripts, all final evidence and retained preliminary attempts.
No production consumer, default change, performance claim or develop merge.
The two previously rejected overlapping-preparation corpus attempts remain
unqualified. The next boundary is registration/invalidation when materialization
replaces canonical object identities, including native cache and factory
generation transitions.
