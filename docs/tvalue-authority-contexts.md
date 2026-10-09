# Dense authority across native memory contexts

The accepted streaming authority and journal pass a three-level native memory ancestry qualification. Parent changes propagate to descendants; child and grandchild local changes leave their ancestors' views unchanged. Context visibility, explicit diagnostic reset and missing setter notifications remain consistent with the pre-streaming journal. Keep the accepted `docs/tvalue-authority-stream/StreamAuthorityJournal.java` and `StreamAuthorityRead` unchanged.

This stage adds a fixture and evidence only. There is no new implementation edit or additional speedup claim.

## Fixture and protocol

Each fixture creates a root, child and grandchild using actual native Mind constructors. Four variables have eight root values each; the child and grandchild each add one local value per variable before observation begins. Their complete views contain respectively 32, 36 and 40 values, with bucket widths 8, 9 and 10. Native `forEach` enumeration is checked against the qualified reader for count, order and exact object reference identity. Reader controls and both observers must preserve native fingerprints. All contexts are memory-only.

Independent pre-streaming and streaming journal sessions observe the same native state. Forty-eight seeded steps cycle through root term rewrite, child visibility toggle, grandchild visibility toggle, child local rewrite, grandchild local rewrite and root visibility toggle. Real setters return before explicit diagnostic metadata bridges. Visibility changes are detected by the journals' existing mark processing. Child and grandchild diagnostic resets occur at steps 24 and 36. Both sessions finish before native contexts are released.

The independent replay checks exact setter notification destinations: root to all three contexts, child local to child and grandchild, grandchild local to grandchild only. It verifies parent view invariance for descendant-only operations and actual view changes for the applicable term/visibility operations. Full before/after buckets replay to every fresh authority view, including the two explicit resets. The oracle remains enabled, freshly reads every factory and never repairs the projection.

## Results

Nine fresh JVMs pass: three seeds (24301–24303) under on/off/verify flags. There are no preliminary fixture failures. The 432 positive mutation steps produce 18 byte-identical paired successful traces, with identical traces across flags for each seed. Each trace has three contexts, 150 authority views, two resets and five seeds. Independently replayed totals are 2,700 authority views and 97,200 serialized value entries. No values are inserted, removed or reordered during the observed sessions; both deletion and restoration and term changes are exercised.

Each JVM also creates three fresh three-level fixtures and deliberately omits a setter notification on a root, child-local or grandchild-local value. All 27 negative cases produce identical refusals in both journals. The root mutation fails in three contexts, child-local in two, grandchild-local in one. Each affected context refuses both at observation and again at finish; ancestors outside the mutation's scope do not falsely refuse. Failed sessions are closed by finish before the next fixture starts.

Across positive and negative fixture construction, 432 per-variable native enumeration controls verify 3,888 canonical references. All 662 inherited runtime class hashes and 13 accepted diagnostic class hashes, accepted source hashes and new fixture class/source hashes are checked. Accepted implementation and production sources/defaults remain unchanged; no develop merge.

## Scope limits

All inherited values and local additions are materialized and natively enumerated before a journal session begins. This does not qualify adding a new parent value after child setup, later parent/child clear, promotion, retirement or persistent storage. Explicit journal reset is a diagnostic scheduling operation, not a qualification of native transaction/rollback boundaries. Native engine hook integration was not rerun; metadata bridges remain explicit. No end-to-end inference performance is claimed. The earlier frozen complete corpus remains unqualified.

## Reproduce

From repository root, prepare the clean runtime described in `docs/tvalue-layer-cost` and the accepted `docs/tvalue-authority-stream` overlay. With Java 17 and ECJ 3.33 targeting Java 8 at `../tooling/ecj.jar`, run this stage's `build.py`, `run.py` and `analyze.py` sequentially. Only `AuthorityContextRunner` is compiled here. Raw logs, result commands, traces and negative error messages are retained. `summary.json` records replay and scope results; `evidence-sha256.json` covers the stage and this report, excluding itself.
