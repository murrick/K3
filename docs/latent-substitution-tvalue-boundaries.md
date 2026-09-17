# TValue index preservation: mutation boundaries

2026-09-17. Production implementation unchanged from `122188b`.
OpenJDK 17 / ECJ Java 8 source target; no Maven or canonical Java 8/21 gate.
All latent and other optimization flags omitted. Three fresh JVMs: OFF,
preserveTValueIndex=true, and preservation plus verifyTValueIndex=true.

`TValueIndexBoundaryRunner` passes in all three modes. The ten ordered
ID projections and completion marker are byte-identical; raw evidence is in
`latent-substitution-evidence/tvalue-boundaries/{off,on,verify}.log`.
Assertions additionally check value equivalence, hash membership, cache size,
root identity, guard outcome, and materialization metadata.

Covered boundaries:

- Three canonical TValue objects over a compiled variable owner; Step-to-Sapato
  materialization drains memory entries and records three persistent IDs.
- Materialization consumes open checkpoints; release afterwards still throws.
- No-change release on materialized and independently loaded persistent chains.
- Interior persistent deletion leaves the same root object but changes links.
  Release invalidates in all modes. Deleted ID/hash are absent and a fresh
  Escalera observes the changed persistent chain.
- Batch deletion with duplicate and missing IDs also forces invalidation.
- Child overlay keeps the parent isolated; actual TValueFactory.commit transfers
  the child's chain. A raw parent-cache checkpoint restores its old root and
  invalidates. This is a cache protocol probe, not a complete Mind transaction.

The same-root deletion case confirms why the mutation counter is required in
addition to root equality. Checkpoints preserve roots, not copies of mutable
graphs: release does not undo physical deletion, in OFF or ON.

Raw deletion intentionally bypasses factory auxiliary indexes. No factory lookup
or logical inference is performed afterwards in that fixture. Higher-level
transaction semantics remain covered by the previous standalone qualification.

A trial extension expected manually injected TValue entries to survive full
close/open. It failed the value assertion already in OFF, so it was removed
from this raw-cache runner rather than treated as an optimization regression or
a passing reopen test. The lifecycle reason was not established here. A focused
reopen scenario must first establish a valid baseline using normal inference;
fresh Escalera loading within an open generation is not equivalent to reopening.
Existing broad lifecycle qualification remains separate evidence.

No performance measurements, production edits, default enablement, or merge.
