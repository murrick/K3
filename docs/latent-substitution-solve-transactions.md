# Nested transaction qualification for versioned solve synchronization

Parent `83ea64b`. New qualification runner only; inference implementation and
default-off experiment flags remain unchanged.

`LatentSolveSyncTransactionRunner state-file work-file` runs twenty operations
on a root Mind and explicit child/grandchild Minds. Initial knowledge is one
edge plus symmetry and edge-to-path rules, with two-variable path queries.
The user home is isolated in a temporary directory. No storage is used in this
runner; storage reopen/replacement coverage remains in the earlier lifecycle test.

## Sequence

1. Query root, publish an edge in a child, query the child, check root isolation.
2. Publish another edge in a grandchild, query it, roll it back, query the child
   for the absent edge and repeat its full path query.
3. Publish a different edge in a fresh grandchild, query and commit to the
   child; verify the edge and query the child's full paths.
4. Roll back the outer child; verify both child and committed-grandchild edges
   are absent from the root, then repeat the root path query.
5. Publish/query a fresh child and commit to root; verify the edge and paths.
6. Attempt a contradictory insertion, verify rejection, repeat the path query.
7. Delete the committed edge, query paths, repeat the query.

Positive checks must return true. Isolation/rollback/collision checks must not
return true; they do not conflate false with undecidable null. Deletion's result
and derived-state behavior are recorded from the oracle rather than assuming
automatic retraction semantics.

Four within-run assertions compare values, solutions, hypotheses and visible
rules exactly: root before/after outer rollback, child before/after nested
rollback, root before/after rejected collision, and repeated post-deletion query.
These assertions pass in every tested mode.

## Independent-mode comparison

Three independent JVM modes:

| Mode | Latent | Factory groups / indexed intersection | Versioned sync |
|---|---|---|---|
| Oracle | off | off | off |
| Active | factory | on | on, real skips |
| Verify | factory-verify | on | on, each proposed skip checked by old scan |

All twenty operations finish in each mode. Complete direct-file state output
is byte-for-byte identical across modes. It includes query/result, sorted
values, solutions, hypotheses, visible generated/primary rules, pass-action
histograms, started passes, unification and TValue counters, and telemetry for
Cause/TSolve/generated-rule/solve-candidate events.

TSolve event counts sum to 188 in each mode across the twenty operations. These
are publication events inside queries, not 188 distinct persistent tuples.
Work snapshots separately sum to:

| Mode | Sync requests | Ordinary scans | Proposed skips | Alias/subclass fallbacks |
|---|---:|---:|---:|---:|
| Oracle | 2766 | 2766 | 0 | 0 |
| Active | 2766 | 93 | 2673 | 0 |
| Verify | 2766 | 93 | 2673 | 0 |

These are sums of exported last-Linker snapshots, not complete work across all
CHECKFALSE/CHECKTRUE invocations. Verification-only scans are excluded from the
ordinary scan count. The test makes no timing claim.

## Scope of the result

This adds evidence that new relations remain visible after publication, that
discarded nested knowledge does not leak, and that repeated queries after
rollback/commit/rejection/deletion retain the reference logical projection.
It does not prove every transaction topology, asynchronous mutation, or public
map alias case; alias/subclass fallback has its separate focused safety test.
It introduces no new journal, rollback policy, persistence format or manager.

No default enablement follows from this result. Before integration, the
canonical toolchain/full qualification and a consolidated review of the
experiment's scope and evidence remain outstanding. The original latent-domain
topology work and this dynamic solve-index optimization remain distinct.

## Evidence and reproduction

`latent-substitution-evidence/solve-transactions/` contains final direct-file
state and work outputs for all modes plus process logs. State files have twenty
complete seven-line operation records; work files have twenty records. Direct
files are authoritative: stdout completion messages are not required as evidence
because this environment has previously dropped final stdout capture lines.

Compile/run with the same ECJ 3.33 Java 8 target and OpenJDK 17.0.20 toolchain.
Factory modes use factoryCandidates, indexedIntersection and versionedSolveSync
true; oracle leaves all three false. No phase timers are required. Production
code did not change, so the existing 123-case corpus was not rerun here; this
checkpoint executes the new transaction qualification in all three modes.
Full Maven and canonical Java 8/21 qualification remain outstanding.

Live develop remains `3ad50f1e5253304f6b530de11c078f332ea4db89`.
No merge, release, tag or deploy.
