# DUMB corruption detection — 3.4.4.1

## Scope

This artifact adds fail-fast detection of accidental persistent-state corruption to the native DUMB backend. It does not claim operation-level crash atomicity, deterministic recovery, multiple-writer safety, OS-crash durability, sudden-power-loss durability, or authenticated protection against a malicious writer capable of modifying all storage files coherently.

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
- raw stored payload length and CRC32 of the complete serialized `Sapato` bytes;
- CRC32 of the complete manifest body.

Entries are ordered first by `baseCode` and then by ID. Duplicate, negative, unordered, truncated, oversized, invalid-base, wrong-version and checksum-invalid manifests are rejected.

Each `Base` instance owns only its local subset in memory. During flush it reloads the latest global manifest under the storage-wide locker, replaces only entries for its own `baseCode`, preserves all other logical bases, and atomically publishes the merged file.

## Validation on reopen

When a manifest exists, opening each logical base verifies its protected subset:

1. global manifest structure and whole-file checksum;
2. every manifest `(baseCode, id)` exists in the matching index view;
3. every index offset names a structurally bounded live store block;
4. raw stored payload length and CRC32 match the manifest before semantic hydration;
5. the hydrated store record ID equals the index ID;
6. every live index ID for that base code exists in the manifest;
7. local index and manifest cardinalities are identical.

The checksum is calculated from the physical payload bytes, not by reserializing a hydrated semantic object. This avoids dependence on a live `Mind` and makes the check a property of persisted representation.

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

## Explicit legacy migration

A non-empty storage without `.integrity`, or a non-empty logical base whose `(baseCode, id)` subset is absent from an existing manifest, is rejected by default. Automatic fallback would make deletion of the manifest indistinguishable from a legitimate legacy database and would defeat the corruption guarantee.

One-time migration must therefore be explicit:

```text
-Dkanger.dumb.integrity.bootstrap=true
```

With that property enabled, each opened legacy logical base performs a complete local index/store scan and publishes its protected subset. The property must then be removed; subsequent absence of the manifest or a protected subset is an error.

Bootstrap proves only that the scanned bytes were structurally readable and mutually consistent at that moment. It does **not** retroactively certify that no earlier undetected corruption had already changed a semantically valid value.

## Qualification and lifecycle regressions

`KangerDumbQualificationRunner` copies all three persistent files and applies deterministic mutations to:

- index headers, records, padding and random positions;
- store headers, block metadata, payload and random positions;
- manifest header, composite entries, footer and random positions;
- truncation boundaries and structural zeroing.

`KangerDumbIntegrityTestRunner` separately verifies:

- two logical base codes with overlapping IDs sharing one physical manifest;
- opposite flush/close order without loss of another base subset;
- fail-fast rejection of a missing manifest on non-empty storage;
- successful explicit one-time bootstrap and protected reopen after the property is removed.

The CI corruption gate runs with:

```text
-Dkanger.dumb.qualification.requireNoSilentCorruption=true
```

The broader strict mode remains intentionally red until 3.4.4.2 closes interrupted-operation atomicity gaps.

## Security boundary of CRC32

CRC32 is used here as an efficient accidental-corruption detector, not as a cryptographic authenticity mechanism. A malicious actor able to rewrite index, store, and manifest and recompute checksums is outside the 3.4.4.1 guarantee. Authenticated integrity, signatures, encryption at rest, access control, and key management require a separate security design.

## Performance implication

The local manifest checksum is updated only for records changed by add/update/delete. Reopen performs a complete validation scan of the live records for each opened logical base. This intentionally favors confidence over startup latency in 3.4.4.1. Later artifacts may add verified block summaries or generations, but may not weaken the fail-fast contract.
