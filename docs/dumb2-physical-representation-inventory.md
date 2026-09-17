# DUMB 2.0 — physical representation inventory

Status: M1.6 architecture inventory for `develop/3.8.0`.

This inventory classifies the existing KANGER/DUMB physical and runtime representation before DUMB 2.0 lifecycle work. It is not a compatibility promise: DUMB 2.0 may change the old file format. The stable `kanger-data-dumb` backend remains the executable reference implementation and is not the implementation target of this redesign.

## Boundary

DUMB 2.0 Context identity and durable revision are Context-wide properties. They are not schema identity, unit identity, factory identity, Mind identity, or SMART GenerationId.

The current runtime has two distinct transaction mechanisms which must remain conceptually separate:

1. semantic/runtime overlays and checkpoints in `Mind`, factories and `Escalera`;
2. physical crash-recovery and durable publication in DUMB `Base` / `RecoveryLog` / `IntegrityManifest`.

A semantic child commit is therefore not a durable Context revision. A DUMB 2.0 revision may advance only after a new Context state has crossed the storage-wide durability boundary.

## Classification

| Area | Current representation | Decision | DUMB 2.0 consequence |
| --- | --- | --- | --- |
| `Escalera` canonical chain | linked `IStep` root plus rebuildable ID/hash/predecessor indexes | ADAPT | Preserve canonical lookup and linked semantic order. Associate the visible root with a Context read snapshot/revision. Keep acceleration indexes derived and rebuildable; they never become semantic or persistence authority. |
| Factory hierarchy | schema-specific canonical registries using `Escalera`, borrowed `IBase`, and `User.nextId(schema)` | ADAPT | Preserve factory semantic/canonical logic. Replace the hard dependency on `User` as the source of schema attachment and persistent ID allocation with a Context-owned schema boundary. Do not duplicate factory semantics inside DUMB2. |
| RuleFactory transaction-local indexes | domain/term/candidate ID indexes, promotion state and checkpoint journals | KEEP | These are runtime/semantic overlay metadata, not durable Context metadata. Rebuild or checkpoint according to the existing factory protocol; do not persist them as Context identity. |
| Unit packing (`Step` / `Sapato`) | packed unit records containing Context-local IDs and linked-chain references | ADAPT | Reuse the local packed-unit model where useful. Local IDs are valid physical/operational identity inside one Context. Exact old byte compatibility is not required. No packed local ID may become cross-Context semantic identity. |
| `.store` data file | append/update physical record storage addressed by offsets | ADAPT | Keep the concept of an implementation-local record store. Physical offsets remain non-semantic. Format may change freely for DUMB2. |
| `.index` | `(baseCode, localId) -> physical offset` | ADAPT | Keep a local lookup index, but its schema routing key must come from persisted Context schema metadata, not process/open order. Derived index contents remain repairable/rebuildable. |
| schema `baseCode` assignment | `bases.size() + 1` during schema acquisition | REPLACE | Opening order must not define persistent schema identity. Persist a stable Context-local mapping between schema descriptor and physical schema code/address space. Reopen must recover it before factories attach. |
| `IBase` | one schema-local persistent address space with local ID allocation, chain endpoints and durability operations | KEEP | The contract is already correctly below Context semantics. ContextId and Revision do not belong on individual `IBase` instances. DUMB2 may provide a new implementation of the same conceptual boundary. |
| `IData` | storage-generation selector/provider used by `User` | ADAPT | Existing provider methods are useful but insufficient as a Context contract. Do not overload `IData` with Context semantic identity merely to transport metadata; DUMB2 should own a Context lifecycle boundary above schema bases. |
| storage selection in `DB` / `User` | mutable selected storage name; `User` manually acquires a hard-coded schema list | REPLACE | A DUMB2 Context open/create operation must atomically acquire one Context descriptor containing identity, revision and schema metadata. `User` should consume an opened Context rather than define its physical schema composition. |
| Context identity | none in old DUMB | REPLACE | Use DUMB2 `ContextIdStore`: one stable ContextId per physical semantic database generation. Move/rename/reindex preserve it; explicit semantic fork receives a new one. No silent legacy adoption. |
| durable revision | none in old DUMB | REPLACE | Use DUMB2 `RevisionStore`. Revision starts at zero and advances monotonically only when a new durable Context state is published. It is distinct from ContextId and SMART generation identity. |
| integrity metadata | `IntegrityManifest` checksums and sizes for `.store`/`.index` | ADAPT | Preserve the separation between integrity and identity. Integrity metadata must cover the DUMB2 physical state needed to prove one durable revision, without turning checksums into identity. |
| crash recovery | `RecoveryLog` prepares old/new physical boundaries around data/index/manifest publication | ADAPT | Preserve recovery-before-open and all-or-recover publication semantics. Extend the durable publication protocol so Context metadata/revision cannot acknowledge a state whose physical files are not durable. |
| semantic checkpoints | `Escalera.mark/commit/release`, factory journals, child `Mind` overlays | KEEP | Keep semantic rollback/publication semantics independent from physical WAL. A semantic checkpoint completion must not itself increment durable Context revision. |
| physical flush ordering | factory `update()` materializes roots, then `User.flush()` delegates storage durability | ADAPT | DUMB2 needs one explicit storage-wide commit boundary: materialize schema state, publish/force physical generation and integrity metadata, then publish the next revision atomically/recoverably. |
| reopen | `User.openClosedStorage()` selects storage, manually acquires every known schema, rebinds factories, then runs semantic hydration/qualification | REPLACE | Reopen belongs to Context lifecycle. First recover/validate physical generation; read ContextId/revision/schema metadata; acquire all schema bases; then publish a coherent immutable read snapshot to runtime and perform semantic hydration/qualification. Partial attachment must never become visible. |
| reindex | copies logical bases into a temporary physical generation, swaps files, reopens | ADAPT | Reindex is maintenance of the same semantic Context, so it preserves ContextId. It may create a new durable revision if the published physical Context state changes. Stable schema metadata must be copied/rebuilt explicitly, not inferred from acquisition order. |
| global selected DB name as Context identity | current process-level `storageName` / `User` attachment | REMOVE | Name/path is a locator only. Rename/move does not change Context identity. Runtime code must not use selected name as semantic identity. |
| old DUMB format compatibility | implicit reference implementation | REMOVE for DUMB2 | DUMB2 does not silently adopt or mutate a legacy DUMB generation. Legacy content requires an explicit future conversion/import path if one is ever defined. |

## Required DUMB 2.0 metadata boundary

The next lifecycle slice should introduce one Context-owned durable descriptor/boundary that can recover, validate and publish at least:

- stable `ContextId`;
- current durable revision;
- stable Context-local schema mapping/descriptor set;
- physical-format version needed to reject incompatible generations deterministically.

`ContextIdStore` and `RevisionStore` remain small codecs. The lifecycle owner coordinates them; they should not absorb schema storage, recovery, factory wiring, or semantic transaction logic.

## Durable publication invariant

For a successful transition from revision `R` to `R+1`:

1. semantic/runtime state is settled at the root operation boundary;
2. changed schema roots/records are materialized into the physical generation;
3. data/index and integrity/recovery metadata reach the implementation durability boundary;
4. the Context publication metadata is made coherent with that durable state;
5. revision `R+1` becomes visible;
6. only then may a subsequent operation acquire the new snapshot.

A failure before step 5 must reopen as `R` or recover deterministically to the fully published `R+1`; it must never expose revision `R+1` pointing at an older or partial physical state.

## Read snapshot invariant

An operation observes a pair conceptually equivalent to:

`(ContextId, Revision, RootSet)`

for its lifetime. Later commits may advance mutable HEAD, but they do not mutate the operation's already acquired read snapshot. `ContextId + Revision` is therefore a suitable part of cache invalidation/fingerprint state, while local IDs and physical offsets are not.

## Explicit non-goals for M1.6

This inventory does not introduce SMART generations, publication/HEAD semantics, multi-Context scheduling, server routing, source priority, cross-Context global IDs, or a new semantic index. Those remain later milestones.
