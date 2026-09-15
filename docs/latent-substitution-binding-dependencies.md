# Binding dependency observation

Enable `-Dkanger.experiment.traceBindings=true` to observe variable/value-ID sets
before and after each completed Linker pass. The scan uses the same argument
variable enumeration as rotator and the same TValueFactory.forEach lookup as
rotateVariables. It does not set current bindings or skip work. It is expensive
diagnostic instrumentation, disabled by default; do not benchmark with it on.

Each changed variable records its owner Rule, added/removed enumerated TValue
IDs, actual visible Rule consumers, and additional consumers connected by an
existing TSolve containing that variable. Consumer IDs come from Rule argument
occurrences, not an assumption that ownership alone is sufficient.

## Results

The six fixtures / 16 operations have 44 changed variable rows. All direct
consumers in these observations equal the variable's owner Rule; no additional
cross-rule tuple consumers were observed in this sample.

For each native male query, pass 3 exposes 17 added TValue IDs across 15 variables
and nine existing Rules (IDs 1, 5, 6, 7, 8, 9, 10, 11, 13), compared with 34
operational new-TValue events. The boundary observation distinguishes values
still enumerated after the pass from intermediate attempts.

Off and factory-verify output, including binding traces, matched byte for byte.
Removing binding-trace lines reproduces the previous pass-trace checkpoint
exactly. Raw logs are in `latent-substitution-evidence/bindings/`.

LatentBindingObservationRunner separately checks inner commit followed by outer
rollback (no observed additions), committed additions (visible), and a manually
constructed cross-rule TSolve (additional consumer in each direction). That
constructed tuple tests observation capability; it does not show that the
regression corpus naturally creates such a tuple.

## Interpretation and remaining gaps

These are differences in values exposed to rotation at completed-pass
boundaries, not durable database commits. A newly visible variable can expose
already existing values; removal of its last Rule consumer can remove an entire
row without deleting the TValue objects. Within-pass additions removed before
the boundary do not appear. Early exits have no completed-pass observation.

The scan intentionally follows forEach semantics, including its current
visibility behavior, rather than inventing a different deleted-value filter.
It must not be treated as an authoritative transaction mutation journal.

Tuple dependencies here are one-hop observations on changed bindings. A new
TSolve can alter isValidFor even when all participating TValue IDs already
existed. Such tuple-only changes are **not** detected by these binding deltas.
Nor do they cover Cause/used/excluded/calculated state or function dependencies.

Next step: observe changes to the TSolve relation independently from TValue
changes, then compare the resulting proposed activation set with actual work.
Do not use owner-only activation or remove either traversal on this evidence.

## Reproduce

With the qualification classpath configured as in the experiment report:

```sh
java -cp "$LATENT_CLASSPATH" org.kanger.LatentBindingObservationRunner
java -Dkanger.experiment.latent=off -Dkanger.experiment.tracePasses=true \
  -Dkanger.experiment.traceBindings=true -cp "$LATENT_CLASSPATH" \
  org.kanger.LatentSubstitutionStateRunner > binding-off.log
java -Dkanger.experiment.latent=factory-verify -Dkanger.experiment.tracePasses=true \
  -Dkanger.experiment.traceBindings=true -cp "$LATENT_CLASSPATH" \
  org.kanger.LatentSubstitutionStateRunner > binding-factory.log
cmp binding-off.log binding-factory.log
```

Locally compiled with ECJ targeting Java 8 and executed on OpenJDK 17; canonical
Java 8/21 Maven qualification remains outstanding.
