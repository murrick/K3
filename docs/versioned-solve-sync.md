# Guarded solve-index synchronization

Repeated Linker validity checks rescan Mind's tuple groups even when no tuple
has been published since the preceding scan. The optional version guard skips
that unchanged synchronization work; it does not change substitution discovery,
candidate acceptance, persistence, or the bidirectional traversal.

Default OFF. Enable with `-Dkanger.experiment.versionedSolveSync=true`.
TValue preservation is a separate flag and is not enabled implicitly.
`-Dkanger.experiment.verifySolveSync=true` additionally executes the reference
synchronizer on proposed skips and rejects changed group/identity counts. This
diagnostic is not an independent deep rebuild or a recovery mechanism.

## Guard boundaries

- Fresh tuple publication and Mind.clearMind advance the version; duplicates do
  not. Clearing Linker's private index resets its synchronization baseline.
- Public getRuleSolves exposure permanently forces reference scanning, including
  aliases retained across clear. All Mind subclasses also use reference scans;
  their overridden getters retain their existing behavior.
- The old synchronizer remains available and is the default path. Existing
  sequential lifecycle requirements and limitations for in-place tuple mutation
  are unchanged.
- Optional `kanger.experiment.profileSolveScans` counters support guard tests and
  diagnostics. They remain disabled by default and do not drive inference.

## Evidence and qualification

The full experiment and raw measurements remain at
[experiment checkpoint 5cc35e0](https://github.com/murrick/K3/blob/5cc35e02b07ddfe54c3b09049e87b32c87ede64e/docs/latent-substitution-versioned-sync-38.md).
With TValue preservation held ON, the large fixture improved from 3.645 to
2.031 seconds; reversed JVM order gave 3.836 to 1.924 seconds. Native queries
improved 16–24%; simple facts showed no stable gain. These are fixture-specific
Java 17 measurements, not a general speedup guarantee or a latent-topology gain.

The transferred Mind/Linker code and three runners are identical to the qualified
experiment on develop base f70042e. Its Java 8/21 general matrix passed 10 jobs,
including solve-sync ON/verify. A separate four-job matrix covers Java 8/21 and
TValue OFF/ON, each exercising solve-sync OFF/ON/verify on 123 corpus cases,
comparing 20-operation transaction snapshots and checking mutable-map guards.
This PR retains both CI matrices; its own run results are recorded in the PR.

No default activation or removal of the reference synchronizer is proposed.
