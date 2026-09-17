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

## Reverse-order replication and small workloads

On the same local Java 17 runtime, a second fresh-JVM pair in OFF-then-ON
order gives 3.836100779 s OFF and 1.923961648 s ON (49.8% less time).
One warmup and three measured queries per mode; all six fingerprints and work
counters match the first pair. Thus both orders support a large-fixture gain,
without establishing a general workload-wide speedup.

Small workloads use five warmups and twenty samples per fixture per JVM,
with TValue preservation ON and diagnostic flags OFF:

| Fixture | JVM order | OFF median ms | ON median ms |
| --- | --- | ---: | ---: |
| natives-values | OFF then ON | 74.322 | 56.553 |
| natives-values | ON then OFF | 73.147 | 61.549 |
| singleton-values | OFF then ON | 17.498 | 18.316 |
| singleton-values | ON then OFF | 18.112 | 18.082 |

Native queries improve by 23.9% and 15.9%. Singleton facts show no stable gain:
ON is 4.7% slower in the first pair and essentially unchanged in the reverse
pair. All 160 measured fingerprints, row counts and substitution-work counters
match within their fixture. Raw query/compile measurements are in reverse-*.csv,
small-{off,on}.csv and small-reverse-{off,on}.csv beside the original evidence.

Live develop/3.8.0 was rechecked at f70042ee5cc03a5205a5a3069ee779246da6c5ce.
Its two commits since 9479cd4 touch only DUMB2 ContextId/RevisionStore and tests;
the measured core remains the 9479cd4-based experimental branch. No develop
changes were merged into this experiment.

## Remaining gates

Canonical Java 8/21 CI for versionedSolveSync itself is now defined separately
in solve-sync-experiment.yml: Java 8/21 times TValue OFF/ON, each running the
corpus and transactions in solve-sync OFF/ON/verify, comparing transaction-state
files, and running the focused guard checks in ON/verify. All four jobs passed
on e005fbb427712c712a11cd5c3c3c1b97d2804d6a:
https://github.com/murrick/K3/actions/runs/35262301809 .
Existing CI's TValue matrix alone does not qualify this separate flag.

The general CI run https://github.com/murrick/K3/actions/runs/35262301763
passed its regression-build step but failed all six cells at DUMB2 compilation:
ContextIdStore.read does not declare StorageLifecycleException in the inherited
9479cd4 base (lines 77, 92, 95, 102). Live develop already fixes exactly this in
e414b452fea89287f6c40edc1a017353ad5156b5. This experiment neither touches that
file nor incorporates that later fix. General CI is therefore NOT green, and its
later invariant steps did not execute. Reconcile with current develop and rerun
the full gates before integration; the focused four-job success is not a waiver.
Further mutation-boundary review remains before any integration proposal.
No merge, default promotion, or removal of the reference synchronizer.
Next is qualification/replication of this minimal prototype, not redesign of the
bidirectional substitution traversal.

Evidence: `latent-substitution-evidence/versioned-sync-38/`. Use the post-TValue
benchmark's large-fixture options and toggle versionedSolveSync only. Corpus:
LatentSubstitutionCorpusRunner. Transactions: LatentSolveSyncTransactionRunner
with state-output and work-output paths. Safety: LatentSolveSyncSafetyRunner
(forces versioned mode and scan counters); optionally add verifySolveSync=true.

## Qualification on current develop base

The experiment delta through 8eb6b6f is reapplied without conflicts on live
develop/3.8.0 f70042ee5cc03a5205a5a3069ee779246da6c5ce in the new branch
experiment/3.8.0-solve-sync-qualified. The old branch and its measurements remain
intact. DUMB2 is taken unchanged from that current base, including the checked
exception correction. Production delta is still confined to Mind and Linker;
no default flag, persistence contract or substitution traversal is changed.
Full and focused CI results on this new base are pending.
