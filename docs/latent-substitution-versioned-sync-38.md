# Version-guarded solve synchronization: first 3.8.0 prototype

2026-09-17, based on `b60c0fc` (develop base `9479cd4`). Default OFF:
`-Dkanger.experiment.versionedSolveSync=true`. TValue preservation is ON
throughout this qualification and benchmark. No latent topology changes.

Mind increments its solve-map version on successful fresh tuple publication and
its own clear. Duplicate addTSolve keeps identity and version. Internal accesses
use a package-private accessor; public getRuleSolves permanently records exposure
of the mutable map, forcing the reference scan thereafter. Mind subclasses also
fall back and preserve virtual getter behavior. Exposure is never reset by clear.
Linker's private index clear resets the last-synchronized-version sentinel. Its
invocation-level map clear immediately precedes that private-index clear.

When version is unchanged, the optional `verifySolveSync=true` executes the old
synchronizer and asserts unchanged indexed group counts and indexed-solve count.
This checks the reference append path's effects; it is not an independent rebuild
of every nested index map. The verifier may run reference writes before rejecting
a mismatch, and is only a diagnostic, not a recovery mechanism.

## Local qualification

Java 17, ECJ Java 8 source target, 512 MiB. OFF, ON and verified modes each pass
the five existing core corpora (123 cases). Twenty nested transaction operations
produce byte-identical state files across all modes: result, values, solutions,
hypotheses, active rules, pass count and semantic-effect counters. The archived
transaction runner was adapted to baseline statistics; experimental pass-action
masks are not available on this new base and are not claimed as compared.

Focused ON/verified checks cover duplicate publication, unchanged skip, new tuple
visibility, private-index clear at the same map version, external list append,
aliases retained across map/index clear, and overridden subclass getters.
All pass. Three new runners live in qualification, outside production artifacts.

## First paired measurement

100-edge / ten-predicate symmetry/shared-path fixture; fresh JVM order ON then
OFF, one warmup and three samples per JVM. Verification, sampling and scan
profiling OFF during timing. TValue preservation held ON in both modes.

| Solve-sync mode | Median query seconds |
| --- | ---: |
| OFF | 3.645 |
| ON | 2.031 |

About 44.3% less elapsed time (1.79x) in this one pair. All six measured fingerprints,
201 result rows, 354,220 domain pairs and 318,210 unifications match the previous
baseline. Reverse-order replication and small-workload timings remain pending;
this is not a general-speedup or default-enablement claim.

A separate ON diagnostic sample performs 204 reference scans, visiting 2,264
groups and processing 4,200 new list slots. The preceding reference measurement
performed 1,119,114 scans and visited 23,127,334 groups for the same 4,200 slots.
The remaining 204 scans all process new slots. No performance conclusion is
derived solely from these operation counts.

## Remaining gates

Canonical Java 8/21 CI for versionedSolveSync itself, reversed timing order,
native/facts comparisons, and further mutation-boundary review are pending.
Existing CI's TValue matrix does not automatically enable this separate flag.
No merge, default promotion, or removal of the reference synchronizer.
Next is qualification/replication of this minimal prototype, not redesign of the
bidirectional substitution traversal.

Evidence: `latent-substitution-evidence/versioned-sync-38/`. Use the post-TValue
benchmark's large-fixture options and toggle versionedSolveSync only. Corpus:
LatentSubstitutionCorpusRunner. Transactions: LatentSolveSyncTransactionRunner
with state-output and work-output paths. Safety: LatentSolveSyncSafetyRunner
(forces versioned mode and scan counters); optionally add verifySolveSync=true.
