# Guarded TValue index preservation

This change is extracted from the latent substitution experiment, independently
of all Linker, RuleFactory, topology-index and profiling changes. Base:
`develop/3.7.0` at `74654935b78465ade2043e40b6ff147b43ad8147`.

`Escalera.release()` normally invalidates derived lookup maps even when it
restores the identical root without intervening mutations. Subsequent TValue
lookup then rebuilds the whole chain. Opt in with
`-Dkanger.experiment.preserveTValueIndex=true`; default behavior remains OFF.

Preservation requires the tvalues schema, a valid index tied to the current root,
the identical checkpoint root, and an unchanged mutation counter. All cache
mutators advance that counter, including conservative no-op calls. Fallback
release also advances it, so outer checkpoints cannot hide an intervening
invalidation. Interior deletion is covered even when root identity is unchanged.

`-Dkanger.experiment.verifyTValueIndex=true` additionally reconstructs all four
maps from the chain and compares IDs, hashes, predecessor links and memory-step
identities. It detects mismatches without repairing the live maps. This flag
only has an effect when preservation is actually selected.

Checkpoint stack consumption, physical materialization, canonical TValue
resurrection and factory transaction handling remain unchanged. Mutation through
escaped raw chain nodes is outside the existing ICache protocol; this counter is
not concurrency control or a copy-on-write graph. No storage format changes.

Three runners cover guard/fallback and deliberately corrupted-map detection,
persistent materialization/interior deletion and child-chain transfer, and normal
inference across full close/open with commit, rollback and collision rejection.
CI runs Maven regression and existing invariant gates on Java 8/21 in OFF, ON,
and verified modes. JAVA_TOOL_OPTIONS propagates flags to subprocess JVMs.
The old reindex fault injector gets SecurityManager opt-in only in its Java 21
test process. Qualification isolation checks the names of all 50 original
runners instead of rejecting every additional runner.

Prior experiment evidence (not a benchmark of this extracted branch):
standalone latent-OFF 100-edge workload median 13.900 -> 3.632 seconds in one
paired run, with unchanged domain visits/unifications. Native workload improved
across three factory-mode JVM pairs; singleton facts were noisy/mixed. No general
speedup guarantee or default enablement is claimed. Reports and raw measurements
remain on `experiment/latent-substitution-index`.

The complete experiment passed six Java 8/21 CI modes at `73821d0`:
https://github.com/murrick/K3/actions/runs/35211842464
The extracted branch requires its own CI result; the source change drops all
experimental timing/counter instrumentation and retains the preservation guard.
