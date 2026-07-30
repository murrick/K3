# DUMB Q2 process-crash recovery

## Artifact

KANGER III `3.4.4.2` adds operation-level process-crash recovery on top of the `3.4.4.1` corruption-detection checkpoint.

The commit boundary is a successfully completed `Base.flush()`.

- Changes made after the last completed flush are provisional.
- A process/JVM crash before the next completed flush restores the previous committed state.
- A completed flush preserves the new state across an ordinary process termination.

This stage does not yet claim durability across OS crash or sudden power loss. Physical ordering and sync barriers belong to `3.4.4.3`.

## Files

For each physical DUMB storage pair:

```text
<storage>.index
<storage>.store
<storage>.integrity
<storage>.integrity.delta
<storage>.wal.<baseCode>
```

Several logical bases share the first four files. The undo journal is per logical `baseCode`, because each base has an independent flush boundary.

## Undo protocol

Before `add`, `update`, or `delete` changes the main files, DUMB appends a checksummed before-image to the corresponding undo journal.

A successful flush performs:

1. publish index changes;
2. publish store changes;
3. append the integrity delta frame;
4. remove the undo journal.

Journal removal is the process-level commit point.

If a journal is present on reopen, DUMB:

1. replays before-images in reverse order;
2. restores the index/store logical pre-state;
3. rebuilds that base's integrity subset while retaining other bases;
4. removes the journal;
5. validates the recovered state normally.

Recovery is idempotent. A second crash during recovery leaves the journal in place until the rollback and integrity reconstruction are complete.

## Incremental integrity publication

The compact `KDI2` integrity snapshot remains the baseline. Repeated flushes append small checksummed `KDD1` delta frames instead of rewriting the complete manifest.

Normal close compacts all committed delta frames into one atomic snapshot and removes the delta file. Recovery also compacts after replacing the recovered base subset.

This avoids the quadratic I/O pattern observed when the complete integrity snapshot was rewritten after every flush.

## Qualification

`KangerDumbQ2RecoveryRunner` checks:

- operation-level unflushed append, relocation update, delete, mixed operations, and repeated changes of one ID;
- a positive committed-flush control;
- physical failpoints after WAL, data, index, and integrity mutation phases;
- failpoints after each flush phase;
- repeated process termination during each recovery phase.

Every uncommitted case must reopen as the exact last completed flush. The committed control must reopen as the complete post-state. No recovery journal may remain after a successful reopen.

`KangerDumbQualificationRunner` remains the strict combined Q1-Q3 gate. `KangerDumbIntegrityTestRunner` separately verifies shared-base integrity, delta reopen/compaction, delta corruption rejection, and explicit legacy bootstrap.

## Remaining boundaries

Not established by `3.4.4.2`:

- write-order durability under OS crash or power loss;
- directory-entry durability for journal deletion and atomic replacement;
- disk-full and short-write recovery;
- concurrent writer processes;
- cryptographic authenticity against intentional coordinated file replacement.
