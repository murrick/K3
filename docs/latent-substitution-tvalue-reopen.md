# TValue preservation through normal storage reopen

2026-09-17, experiment branch after `35e3481`. Production code unchanged.
OpenJDK 17, ECJ Java 8 source target, three fresh JVMs, 512 MiB heap.
All other experimental optimizations omitted. Modes: OFF; preservation;
preservation plus independent index verification.

`TValueIndexReopenRunner` passes in each mode. Complete stdout projections
are byte-identical in `latent-substitution-evidence/tvalue-reopen/`.
The fixture uses ordinary compile/query and full Mind/User storage lifecycle;
it does not inject TValue objects or manually materialize them.

The database contains edge(A,B), edge symmetry, and edge-to-path projection.
Eight inference-created TValue entries survive two full close/open cycles with
the same ID, variable ID, term ID and deletion state. Active rule text and
query rows, including duplicate rows, agree before and after reopening.

A child adding edge(B,C) is rolled back; the original query rows return.
A second child adding edge(B,D) is committed. Twelve TValue entries and the
expanded active rules survive another close/open with identical projections.
A contradictory edge insertion is rejected and the query rows remain unchanged.
Expected query content is the OFF oracle's behavior, not an independent proof
of the inference engine's logical completeness or correctness.

Each TValue snapshot additionally checks variable/term hydration and hash
membership. It opens and releases a no-change raw cache checkpoint and checks
that OFF invalidates while ON preserves; the verified run independently
reconstructs the index. The runner does not assert cross-generation Java object
identity: persistent ID relationships are the comparison across reopen.

## Earlier synthetic fixture

Source inspection explains why expecting injected values 101–103 to survive
close/open was invalid. `User.closeQuiescentStorage` calls `checkpoint`, which
commits an empty child. Root settlement runs `Mind.pack()` and `update()`.
`TValueFactory.pack()` removes values whose donor term has no active rule
reference (as well as logically deleted values). The synthetic fixture only
compiled p(x) -> q(x); its numeric terms were not referenced by active rules.
That is a lifecycle/fixture distinction, not evidence against preservation.
This explanation is source-based; the earlier failed assertion was observed
in OFF and the new normal-inference fixture supplies the passing reopen evidence.

No timings or production changes. Default remains off. Full Maven and canonical
Java 8/21 qualification remain outstanding before considering promotion.
