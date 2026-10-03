# Adjacent deletion checks: semantic counterexamples

Live base develop/3.8.0: 1c943d02d39bd33ebd91fabb7c4190112ab49459.
Branch experiment/3.8.0-duplicate-deletion-forensics. Production is unchanged.
This investigates the repeated `!a.isDeleted(mind)` in ArgumentsList.getTVariables,
identified in the post-snapshot profile. The old implementation remains the oracle.

## Reproduction

Diagnostic DuplicateDeletionForensics.java copies the existing enumeration body
with exactly one adjacent check removed. Each scenario creates separate identical
fixtures for the production and one-check implementations. It compares variable
identity/counts, callback counts and exact thrown exception identity. The diagnostic
is outside the production/qualification reactor and does not introduce a runtime
flag or optimization. Local compilation uses ECJ Java8 target compatibility and
execution uses Java 17.0.20, 512 MB. All eight production flags use their ON defaults.
Run `bash docs/duplicate-deletion-forensics/build-local.sh` from repository root.

Local result: DUPLICATE_DELETION_FORENSICS_OK scenarios=6 divergent=5 checks=40.
Stderr is empty. The stable built-in control is equal; five stateful cases diverge.
These are constructed semantic witnesses, not estimates of their corpus frequency.

| Scenario | Reference | One check |
| --- | --- | --- |
| Stable built-ins | Includes variable | Includes variable |
| Custom argument: second deletion read true | Excludes variable, 2 callbacks | Includes variable, 1 callback |
| Custom argument: second deletion read throws | Original exception, 2 callbacks | Includes variable, 1 callback |
| Exact current classes, parent marks child | Excludes variable | Includes variable |
| Exact classes, mutable overlay Set callback | Excludes variable, 2 callbacks | Includes variable, 1 callback |
| Exact classes, overlay Set second call throws | Original exception, 2 callbacks | Includes variable, 1 callback |

In the parent witness, current Argument, TVariable and Mind have exactly their
built-in classes. A custom parent.getNext callback marks the already-visited child
as deleted at the end of the first traversal. Both implementations call the parent
once; only the reference revisits the child and sees the deletion on its second
check. Thus preserving callback count alone is insufficient.

The overlay witnesses use an exact root Mind (no parent), exact Argument and exact
TVariable. A custom HashSet installed through public getDeleted().put returns the
first pre-mutation membership result and inserts the requested ID; its second read
sees the mark or throws the fixture's original exception. Exact outer class guards
do not guarantee callback-free collection contents.

## Boundary and decision

Unconditional removal is incorrect. Guards checking only the three outer classes
are also insufficient. A viable stronger guard would need callback-free resident
argument/variable reads, a qualified parent chain and overlay collections, and an
established stable-observation/ownership boundary covering the two reads. Root-only
and exact-standard-set guards may exclude the constructed callback witnesses, but
that alone does not prove equivalence under supported concurrent mutation.

Mind.next is mutable; getNext may be overridden. The public deleted/restored getters
expose live maps and mutable Set values. PortableMindLayer modifies them directly.
Each reference deletion traversal acquires/releases each Mind's locker separately;
query is not one enclosing immutable snapshot (see Mind concurrency documentation).
Preflighting custom parent traversal would itself add callbacks and change order.
Maintaining a cached empty/epoch state would require accounting for public writes.
These are reasons to require further proof, not proof that every stronger guard
is impossible. No concurrent-race result is claimed by this diagnostic.

The simple coalescing candidate is deferred. There is no valid fast-path proposal
ready for timing, so no performance benchmark or general regression matrix is run.
The stable fixture alone cannot justify changing the production method. The next
bounded optimization lead is requalifying the existing active-Mind reference reuse
prototype on the eight-default base; it avoids replacing the same WeakReference
without deleting owner checks or custom callbacks.

No production change, merge, default enable, release, tag or deploy is performed.
