# DUMB Reliability Qualification

Status: **active qualification; not yet crash-atomic**

Tracking issue: #23

## Purpose

DUMB is the native/reference KANGER storage engine. This qualification establishes evidence-backed guarantees and exposes unsupported failure modes. It is not a certification exercise and must not turn a successful happy-path test into an unsupported ACID claim.

The qualification distinguishes:

1. logical correctness after clean lifecycle operations;
2. durability after a completed flush;
3. atomicity of an interrupted add/update/delete;
4. detection of corrupted index/store content;
5. deterministic recovery;
6. OS-crash and power-loss durability.

## Current physical model

Each DUMB logical database is represented by two independently updated files:

```text
<name>.index
<name>.store
```

The store contains serialized `Sapato` blocks. The index maps logical IDs to physical store offsets.

Current write ordering is not an atomic transaction protocol:

- `Data.set()` writes through immediately;
- a new or relocated store offset is then placed into the in-memory `Index`;
- `Index.set()` and `Index.remove()` may remain dirty until `Index.flush()`;
- `Base.flush()` invokes `index.flush()` and then `data.flush()`;
- current files have format version checks but no record/block checksum;
- there is no WAL, commit generation, double-written manifest, or durable sync barrier across the pair.

Consequently, process termination after a completed flush is a different guarantee from process termination inside a mutation, and both are different from sudden power loss.

## Qualification levels

### Q0 — format and invariant validation

Required checks:

- exact file header and supported version;
- legal index block size and complete record boundaries;
- unique IDs and valid base codes;
- every live index offset is within the store file;
- store block size/data size are non-negative and within file bounds;
- serialized payload is complete;
- index-to-store ID consistency;
- valid `next` references;
- complete acyclic root-to-tail chain;
- explicit diagnostics for orphan store blocks and dangling index entries.

Acceptance criterion: malformed state is rejected explicitly or repaired deterministically. It must never be accepted silently as a valid database.

### Q1 — clean lifecycle durability

Scenarios:

- explicit `Base.flush()`;
- explicit `Base.close()` / `closeStorage()`;
- normal JVM shutdown hook;
- repeated reopen cycles;
- clear and recreate;
- reindex;
- clean backup/copy/restore;
- seeded random add/update/delete operations compared with an independent oracle.

### Q2 — process-crash consistency

Fault points must cover both sides of every physical transition:

- before/after store overwrite;
- before/after store append;
- before/after relocation invalidates the old block;
- before/after index set/remove;
- before/after index block split;
- before/after linked-chain boundary update;
- before/after index flush;
- before/after store flush;
- before/after close.

Acceptance criterion: reopen returns either the complete pre-operation state or the complete post-operation state. An impossible hybrid, dangling reference, silent loss, or partially visible record is a failure.

### Q3 — corruption detection and recovery

Scenarios:

- truncation at every header/record/block boundary;
- truncation inside payloads;
- deterministic random bit flips;
- structural field zeroing;
- invalid offsets, sizes, IDs, types and links;
- orphan store records and dangling index records;
- duplicate IDs and chain cycles;
- index/store generation mismatch once generations exist.

Acceptance criterion: corruption is detected before semantic data is returned. Recovery, when available, must be deterministic and produce a protocol.

### Q4 — resources, concurrency and soak

Scenarios:

- injected short write and failed write;
- disk full;
- failed flush/sync;
- readonly and permission failures;
- second writer and file-lock behavior;
- supported concurrent-reader model;
- seeded multi-million-operation soak;
- repeated halt/reopen during soak;
- bounded caches and long-term file growth/reuse;
- backup while active only if an explicit snapshot contract exists.

### Q5 — OS crash and power-loss durability

No Q5 claim is permitted until DUMB has both:

- an atomic recovery protocol spanning index and store;
- explicit durable ordering barriers (`FileDescriptor.sync()` or an equivalent platform mechanism).

The final Q5 matrix should include more than `Runtime.halt()`: controlled VM reset, filesystem/device fault injection, or another environment that can invalidate unsynced page-cache assumptions.

## Reproducible runner

`org.kanger.KangerDumbQualificationRunner` creates a direct 100-record DUMB fixture and produces:

```text
target/dumb-qualification/dumb-qualification.csv
target/dumb-qualification/dumb-qualification.md
```

Default mode is observational:

```bash
java \
  -Dkanger.dumb.qualification.output=target/dumb-qualification \
  -cp "target/classes:lib/*:kanger-server/lib/*" \
  org.kanger.KangerDumbQualificationRunner
```

Strict acceptance mode:

```bash
java \
  -Dkanger.dumb.qualification.strict=true \
  -Dkanger.dumb.qualification.output=target/dumb-qualification-strict \
  -cp "target/classes:lib/*:kanger-server/lib/*" \
  org.kanger.KangerDumbQualificationRunner
```

Strict mode is expected to fail on the initial 3.4.4 baseline. It becomes a release gate only after recovery and validation changes remove the recorded gaps.

## Initial canonical protocol

Environment:

```text
Java:         21.0.10
OS:           Linux 6.12.13 amd64
seed:         19640207
fixture:      100 linked LONG records
```

Canonical runner result:

```text
PASS:                   2
GAP_SILENT_HYBRID:      3
DETECTED_EXCEPTION:    25
GAP_SILENT_CORRUPTION: 49
PASS_OR_IRRELEVANT:    77
```

The 151 corruption mutations consist of deterministic truncations, structural zeroing, fixed-position flips and seeded random flips across both files.

### Confirmed current guarantees

- explicit clean close and reopen preserve all fixture records and the complete chain;
- completed `Base.flush()` followed by `Runtime.halt(0)` preserves all fixture records and the complete chain in the tested environment.

### Confirmed current gaps

#### Append window

A new store block is written before the new ID-to-offset entry is persisted. Halting before index flush leaves an orphan store block and the new logical record is absent after reopen.

#### Relocation window

When an updated record no longer fits its old block, the old block is invalidated and a new block is appended before the index offset is persisted. Halting in this window leaves the old index pointing to an invalid block and the existing logical record disappears.

#### Delete window

The store block is invalidated while the corresponding index removal can still be dirty in memory. Halting in this window leaves a live index entry resolving to no record.

#### Silent corruption

The format version protects only the file header. Record and block payload corruption can currently produce missing IDs, altered values, invalid `next` IDs or incomplete chains without a storage-level corruption exception. The qualification oracle catches these states, but normal DUMB open/read does not always do so.

## Current defensible statement

> In the tested environment, a completed DUMB flush/close survives ordinary JVM process termination and reopens with the verified logical state.

The following claims are **not yet justified**:

- operation-level crash atomicity;
- automatic detection of all corrupted records and links;
- deterministic recovery from index/store divergence;
- multiple-writer safety;
- arbitrary OS-crash durability;
- sudden power-loss durability.

## Expected engineering sequence

1. Add strict structural validation and checksums so silent corruption becomes explicit detection.
2. Introduce a minimal atomic commit/recovery protocol across index and store.
3. Add durable ordering barriers and document their cost/semantics.
4. Re-run the same runner in strict mode on Java 8, Java 21 and macOS.
5. Extend with write-failure, concurrency and long-running soak matrices.
6. Promote only guarantees demonstrated by the resulting protocol.

The recovery mechanism should be chosen from measured failure windows. A small generation/commit manifest or write-ahead journal is preferable to relying on incidental write order or file close behavior.
