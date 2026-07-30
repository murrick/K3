# DUMB corruption detection — 3.4.4.1

## Scope

This artifact adds fail-fast corruption detection to the native DUMB backend. It does not claim operation-level crash atomicity, deterministic recovery, multiple-writer safety, OS-crash durability, or sudden-power-loss durability.

The acceptance target is narrow and measurable:

```text
Q3 GAP_SILENT_CORRUPTION = 0
Q3 GAP_HANG = 0
```

Q2 crash windows remain a separate 3.4.4.2 concern.

## Integrity manifest

Each DUMB base now has three persistent files:

```text
<base>.index
<base>.store
<base>.integrity
```

The historical index and store formats are unchanged. The integrity manifest contains:

- magic and manifest format version;
- storage base code;
- exact live-record count;
- for every live ID, the packed record length and CRC32 of the complete serialized `Sapato`;
- CRC32 of the complete manifest body.

Entries are written in ascending ID order. Duplicate, negative, unordered, truncated, oversized, wrong-base, wrong-version and checksum-invalid manifests are rejected.

## Validation on reopen

When a manifest exists, opening the base verifies:

1. manifest structure and whole-file checksum;
2. every manifest ID exists in the index;
3. every index offset resolves to a store record;
4. the store record ID equals the index ID;
5. serialized record length and CRC32 match the manifest;
6. every live index ID exists in the manifest;
7. index and manifest cardinalities are identical.

Any mismatch raises an explicit `DatabaseErrorException` with a `DUMB storage corruption` diagnostic. A damaged state must not be exposed as missing data, a changed value, a shortened chain, or a normal empty result.

## Publication ordering

Normal persistence order is:

```text
index flush
store flush
integrity temporary file write
integrity file-descriptor sync
atomic integrity replacement when supported
```

The manifest is therefore published last. If the process stops before manifest publication, reopening compares the previous manifest with the changed index/store and rejects any divergent live state.

This is detection, not recovery. A mismatch can prevent the database from opening until a recovery tool or a known-good copy is used.

## Legacy bootstrap boundary

A non-empty legacy DUMB database may not have an integrity manifest. For upgrade compatibility, the first open performs a complete index/store scan and creates the initial manifest.

That bootstrap proves only that the scanned files were structurally readable and mutually consistent at that moment. It does **not** retroactively certify that no earlier undetected corruption had already changed a semantically valid value.

After the first successful manifest publication, subsequent tested corruption is covered by the 3.4.4.1 contract.

## Qualification runner

`KangerDumbQualificationRunner` copies all three persistent files and applies deterministic mutations to:

- index headers, records, padding and random positions;
- store headers, block metadata, payload and random positions;
- manifest header, entries, footer and random positions;
- truncation boundaries and structural zeroing.

The CI gate runs with:

```text
-Dkanger.dumb.qualification.requireNoSilentCorruption=true
```

The broader strict mode remains intentionally red until 3.4.4.2 closes interrupted-operation atomicity gaps.

## Performance implication

The manifest checksum is updated only for records changed by add/update/delete. Reopen performs a complete validation scan of live persistent records. This intentionally favors confidence over startup latency in 3.4.4.1. Later artifacts may add verified block summaries or generations, but may not weaken the fail-fast contract.
