# Direct Domain references: context-rebinding counterexample

2026-09-16. Qualification-only change. No production optimization or context semantics altered.

## Existing behavior

Step.getData(mind) and Sapato.getData(mind) invoke IUnit.setMind even for an existing canonical object. Domain.setMind assigns its Mind, obtains its currently visible TVariables through ArgumentsList.getTVariables(mind), and calls setMind on each. Variable discovery respects deletion state and recursively traverses nested Function arguments; it is not a static direct-variable mask. Argument.getObject may load a missing object but, once cached, returns it without automatically rebinding it.

TVariable.setMind sets a thread-local weak runtime context and conditionally updates the owner/default Mind reference. Stable ownership ID remains separate. getCurrent/getValue and other no-Mind operations use activeMind(), so changing runtime context changes where they resolve TValue state. Merely preserving the canonical TVariable pointer does not preserve that context.

Consequently, a retained Domain can have the intended Mind while a referenced variable has a different active Mind. A Domain-only context guard is insufficient. Replacing the full variable traversal with a static TVariable array also requires proving visibility and nested-function behavior, not only matching argument positions.

## Executable witness

LatentDomainContextRunner compiles a variable rule, creates a child Mind and uses existing factory APIs on the same thread:

1. Root and child Domain lookup return the same canonical Domain object. Child lookup leaves Domain and TVariable bound to child.
2. Root TVariable lookup returns the same variable object but switches its active Mind to root. Domain still points to child.
3. Repeating the child Domain lookup keeps the Domain identity unchanged and restores the variable's active Mind to child.
4. After another root variable lookup, explicitly calling Domain.setMind(child) also restores the variable context in this fixture; its stable owner ID does not change.
5. After child rollback, root Domain lookup preserves identity and restores both root contexts.

The runner passes in `latent=off` and `latent=factory-verify`, with byte-identical direct evidence files. This is a controlled API counterexample to treating a saved pointer, or a matching Domain Mind, as sufficient. **It does not claim that this interleaving was observed inside an actual rotator** or that explicit setMind alone replaces all possible factory behavior.

Evidence: [domain context](latent-substitution-evidence/domain-context/). Reproduce with compiled qualification classes:

```sh
java -Xmx512m -cp "$CP" org.kanger.LatentDomainContextRunner /absolute/path/off.txt
java -Xmx512m -cp "$CP" -Dkanger.experiment.latent=factory-verify \
  org.kanger.LatentDomainContextRunner /absolute/path/factory.txt
```

## Consequence for the index experiment

The earlier 78.7% repeated Domain materialization count remains valid, but is not a count of proven removable context operations. Reject both unqualified pointer reuse and the shortcut `if (domain.getMind() == mind) skip rebinding`.

The narrower candidate is reuse of the ordered occurrence list while preserving eager Domain.setMind on every occurrence, including duplicates, before semantic pair checkpoints. Remaining proof obligations are canonical identity/lifecycle stability within a rotator, context-sensitive variable discovery, hydration and custom factory behavior. Verification must distinguish pre-lookup state from the state repaired by reference lookup; checking only after the oracle runs can conceal the very effect being removed.

No full corpus rerun was needed for this qualification-only addition; prior production code and its corpus checkpoint are unchanged. Canonical Maven/Java 8/21 gates remain outstanding. Next implementation should preserve rebinding and validate actual skipping separately rather than infer safety from this witness.
