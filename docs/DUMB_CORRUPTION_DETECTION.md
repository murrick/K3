# DUMB corruption detection — 3.4.4.1

## Scope

This artifact adds fail-fast corruption detection to the native DUMB backend. It does not claim operation-level crash atomicity, deterministic recovery, multiple-writer safety, OS-crash durability, or sudden-power-loss durability.

The acceptance target is narrow and measurable:

```text
Q3 GAP_SILENT_CORRUPTION = 0
Q3 GAP_HANG = 0
```

Q2 crash windows remain a separate 3.4.4.2 concern.

## Physical storage and logical bases

One DUMB storage prefix is a physical pair shared by several logical `Base` instances distinguished by a four-bit `baseCode`:

```text
<storage>.index
<storage>.store
```

3.4.4.1 adds one integrity file for that complete physical pair:

```text
<storage>.integrity
```

The historical index and store formats are unchanged. The manifest is global to the storage prefix and contains:

- magic and manifest format version;
- exact total protected-record count;
- for every live record, the composite key `(baseCode, id)`;
- packed record length and CRC32 of the complete serialized `Sapato`;
- CRC32 of the complete manifest body.

Entries are ordered first by `baseCode` and then by ID. Duplicate, negative, unordered, truncated, oversized, invalid-base, wrong-version and checksum-invalid manifests are rejected.

Each `Base` instance owns only its local subset in memory. During flush it reloads the latest global manifest under the storage-wide locker, replaces only entries for its own `baseCode`, preserves all other logical bases, and atomically publishes the merged file.

## Validation on reopen

When a manifest exists, opening each logical base verifies its protected subset:

1. global manifest structure and whole-file checksum;
2. every manifest `(baseCode, id)` exists in the matching index view;
3. every index offset resolves to a store record;
4. the store record ID equals the index ID;
5. serialized record length and CRC32 match the manifest;
6. every live index ID for that base code exists in the manifest;
7. local index and manifest cardinalities are identical.

Any mismatch raises an explicit `DatabaseErrorException` with a `DUMB storage corruption` diagnostic. A damaged state must not be exposed as missing data, a changed value, a shortened chain, or a normal empty result.

## Publication ordering

Normal persistence order is:

```text
index flush
store flush
reload latest global integrity manifest
replace current baseCode subset
integrity temporary file write
integrity file-descriptor sync
atomic integrity replacement when supported
```

The manifest is therefore published last. If the process stops before manifest publication, reopening compares the previous manifest with the changed index/store and rejects any divergent protected state.

This is detection, not recovery. A mismatch can prevent the database from opening until a recovery tool or a known-good copy is used.

## Storage lifecycle

The integrity file participates in the same physical lifecycle as index and store:

- `drop` removes `.index`, `.store`, and `.integrity`;
- `reindex` builds all three temporary files and installs all three together at the existing reindex boundary;
- each logical base may flush independently without erasing manifest entries owned by another base code.

## Legacy and first-subset bootstrap boundary

A legacy DUMB storage may not have an integrity manifest. For upgrade compatibility, the first opened logical base scans its index/store subset and creates the initial global manifest. Later logical base codes absent from that manifest are added by the same full local scan when first opened.

Bootstrap proves only that the scanned bytes were structurally readable and mutually consistent at that moment. It does **not** retroactively certify that no earlier undetected corruption had already changed a semantically valid value.

After a subset has been successfully published, subsequent tested corruption of that protected subset is covered by the 3.4.4.1 contract.

## Qualification runner

`KangerDumbQualificationRunner` copies all three persistent files and applies deterministic mutations to:

- index headers, records, padding and random positions;
- store headers, block metadata, payload and random positions;
- manifest header, composite entries, footer and random positions;
- truncation boundaries and structural zeroing.

The CI gate runs with:

```text
-Dkanger.dumb.qualification.requireNoSilentCorruption=true
```

The broader strict mode remains intentionally red until 3.4.4.2 closes interrupted-operation atomicity gaps.

## Performance implication

The local manifest checksum is updated only for records changed by add/update/delete. Reopen performs a complete validation scan of the live records for each opened logical base. This intentionally favors confidence over startup latency in 3.4.4.1. Later artifacts may add verified block summaries or generations, but may not weaken the fail-fast contract.
