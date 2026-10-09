# Recycled TValue IDs with live descendants

This documentation experiment follows `6f360e1d83cc7f5d1949e65a8d2a1c5addf2853c` on exact native develop source `5d5f6aff271f1abf371fbde55d637744c53f0bc3`. It characterizes a boundary deliberately excluded from the preceding ordinary-mutation qualification. Production source and defaults remain unchanged.

## Observed native behavior

In-memory root `TValueFactory.clear()` calls `Escalera.clear()`, which clears the User counter for this schema. The next root value reuses ID 0. Existing child and grandchild caches still resolve that ID to the original object. Their enumeration uses live parent variable routes, so a new root route can enumerate the old object under the replacement variable. A fresh descendant sees the replacement object.

The native controls distinguish pointer identity, object variable, value ID and term ID. Every single-variable pure bucket must match native enumeration in exact pointer order; full authority is compared separately so an incomplete oracle cannot silently establish equivalence.

| Operation and replacement variable | Existing child/grandchild | Both full authorities | Unchanged journals |
| --- | --- | --- | --- |
| Root clear; same variable | Old object through the reused route | Match native enumeration | Accept |
| Root clear; other existing variable | Old object enumerated under the other variable | Match native enumeration | Accept |
| Root clear; previously empty variable | Old object enumerated under the new variable | Omit that variable: it is absent from the target resident chain's variable keys | Reject projection/full-authority disagreement |
| Child clear; any of the three variables | Replacement gets a new ID; old grandchild has no captured replacement | Match native enumeration | Accept |
| Root pack; any of the three variables | Replacement gets a new ID; old descendants have no captured replacement | Match native enumeration | Accept |

The third row is an oracle scope limitation, not an optimization gain or a qualified native identity contract. It confirms why matching the two full authorities alone is insufficient: they share a qualified raw reader and both select variable keys from the target chain. The independent native enumeration check exposes the omitted key. The incremental journal's dirty new-variable bucket includes the old object, and its full comparison refuses the mismatch. No reader, journal or native cache is repaired in this experiment.

## Diagnostic refusal policy

`RecycledIdHooks` extends the preceding observer adapter with a pure alias check at the native post-add callback. For each active descendant of the adding owner, it checks whether the resident lookup for the new value ID points to a distinct object. It reports the owner/descendant levels, reused ID, old variable and new variable. Fingerprints must remain unchanged. Retired contexts leave the registry; fresh descendants sharing the replacement object are not classified as aliases.

The guarded driver assigns `refused-recycled-identity` to any such case, even where both journals accept their views. This is a diagnostic refusal-policy prototype: it records the unsupported boundary without throwing from or undoing the native mutation. A future authoritative consumer must enforce this verdict before using these views. The underlying journals are still run independently for characterization, and their accept/reject results are retained. The guard does not claim to repair semantics or make reuse safe.

Turning the guard off retains all native mutation callbacks and both full checks. It demonstrates that existing-variable reuse can be accepted by the unchanged journals, while the unseen-variable case is independently rejected. Guarded child-clear and pack controls must have no aliases.

## Matrix and evidence

The final matrix is three case-order permutations × three flag settings × clean, unguarded instrumented and guarded instrumented execution: 27 fresh JVMs, nine boundary cases each. All 243 native cases must pass their explicit controls. Native rows are byte-identical across the three executions and flag settings for each permutation. All 288 successful old/stream traces are independently replayed, covering 9,792 authority views and 20,700 serialized value entries; corresponding case traces are identical across flags, guard settings and permutations. Failed unseen-variable journal verdicts are stored in diagnostics rather than counted as successful traces.

The matrix checks 7,290 native variable/context buckets and 2,430 native views. Both pure full authorities are checked for each view: 4,860 comparisons. Exactly 54 native/full-view disagreements occur, all at the two old descendants after the unseen-variable replacement. Both journals reject all 18 instrumented occurrences of that case. The guarded matrix reports two aliases in each of 27 root-clear cases and zero aliases in all 54 child-clear/pack controls.

All 730 prior clean native/diagnostic class hashes must match. Only Mind, Mind$1, TValueFactory and TValue native class bytes differ in the instrumented runtime; reversing recorded callbacks reconstructs every original native source byte. Both journals and both readers remain unchanged.

See [summary.json](tvalue-recycled-id/summary.json), [build-validation.json](tvalue-recycled-id/build-validation.json), [analyze.py](tvalue-recycled-id/analyze.py), [build.py](tvalue-recycled-id/build.py) and [run.py](tvalue-recycled-id/run.py) for counts, exact source/class hashes and reproduction. Every result includes its JVM command and isolated home. Diagnostics retain journal errors and policy verdicts; native rows retain canonical pointer labels and enumeration/full-view comparisons.

## Scope and next step

This is a characterization and diagnostic exclusion of recycled identity in legacy DUMB memory on the exact SMART-era core. It does not qualify SMART persistence, concurrent execution, arbitrary metadata identity changes, the complete inference corpus or a speedup. Production, develop and defaults are unchanged; previous complete-corpus limitations remain in force.

The next useful step is to consolidate the diagnostic qualification boundary: explicit consumer refusal for unsupported identities and incomplete authority coverage, with supported mutation/settlement cases retained as controls. Any proposal to change native root-clear counter policy or descendant snapshot semantics needs a separately scoped contract and native regression qualification.
