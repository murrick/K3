# Rejected hypothesis: reusable pair output from argument projection

The diagnostic now compares each completed operation with the first output
recorded under the existing observed input key. The output descriptor contains
result, local commit/release, success, and both ordered substitution arrays as
TValue IDs with explicit null positions. This is a proposed replay descriptor,
not a serialization of all semantic effects. No replay is executed.

`kanger.experiment.verifyPairOutputs=true` enables tracing and throws an
AssertionError on a mismatch. Diagnostic exceptions are intentionally not
swallowed by Linker's catch(Exception). Corpus test wrappers may catch them
and continue to subsequent tests, so the entire corpus can report several
rejected cases rather than abort at the first case.

## Narrow sample versus full corpus

The existing six-fixture / 16-operation sample matches on all 4193 repeated
inputs: 2233 same-pass and 1960 later-pass repeats. Off and factory-verify output
matches byte for byte; stripping the new pair-output lines reproduces the
previous pair-boundary profile.

The full 123-case corpus with the replay assertion enabled **fails**: 32 mismatch
assertions are observed (31 completed-corpus cases and one promotion case).
These are rejections of the proposed output invariant, not evidence of a new
baseline inference defect. The assertion interrupts those test executions, so
their final answers must not be compared as though a replay had run.
The observation-only run, with factory candidate verification still enabled,
passes all 123 cases and finishes with LATENT_CORPUS_PASS.

## Reproducible counterexample

```text
!num(0); !@x num(x) && x < 10 -> num(++x);
?$x num(x);
```

Observed key:

```text
false|1:TVARIABLE=empty@none|0:TERM=1
```

| Output field | First occurrence | Later occurrence |
|---|---|---|
| result | true | false |
| local checkpoint | commit | release |
| success | true | true |
| master substitution IDs | [0] | [0] |
| slave substitution IDs | [null] | [null] |

Term ID 1 is an internal identity, not the numeric literal in the source.
The variable's current selection remains empty, but its set of known bindings
has changed. Reference code searches TValueFactory.find(variable, term): an
initial insertion sets result, whereas an already existing binding does not.
Current argument/current-TValue state therefore omits information needed to
predict result and checkpoint behavior.

LatentPairOutputCounterexampleRunner asserts that the diagnostic rejects this
case, then runs the underlying fixture without the rejected invariant and
requires successful inference with values. The established full corpus remains
the stronger logical oracle.

## Decision

Do not cache/replay this descriptor under the argument-only observation key.
The prior zero-mismatch evidence applies only to the smaller sample and is
superseded by this counterexample for any general claim.

Prefer the original architectural boundary: derive compatible Domain pairs and
structural argument-position plans at build/publication time; retain runtime
binding-existence checks, CVariable handling, checkpoint decisions and effects.
Memoizing a complete mutable inference result would require additional
invalidation dependencies and has not earned that complexity.

Next bounded investigation: whether a structural pair plan can avoid repeated
argument classification/search while invoking the existing runtime checks in
their original order. It must not mechanically freeze blockLeft/blockRight or
assume that unchanged current values imply unchanged binding availability.

## Evidence and reproduction

`latent-substitution-evidence/pair-output/` contains the two narrow profiles,
the deliberately failing invariant qualification, and the observation-only
corpus run. The failure log is retained as negative evidence.

Run `org.kanger.LatentPairOutputCounterexampleRunner` for the focused regression.
Run LatentSubstitutionCorpusRunner with `tracePairInputs=true` for observation;
add `verifyPairOutputs=true` only to reproduce the rejected hypothesis.
Diagnostic qualification failure in that latter mode is expected.

Local environment remains ECJ targeting Java 8 on OpenJDK 17. Full Maven and
canonical Java 8/21 qualification remain outstanding.
