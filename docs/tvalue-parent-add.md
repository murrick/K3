# Parent additions after child construction

Parent: `a35f8caabc556a110e5255ae2922428e2eab0968` (authority-contexts qualification).

This docs-only experiment qualifies the unchanged diagnostic stream journal against native memory contexts when values are added to four existing variables in a parent after children have been constructed. It changes no engine implementation, defaults, or production hooks.

## Result

Existing children retain their captured parent cache chain: new parent values are absent from their native `get(id)` and `forEach` results. A fresh child of the current root sees those additions. A fresh grandchild of an old child inherits the old child's captured membership, including its local values, rather than the latest root membership. This is snapshot membership, not a deep copy: rewriting the term of an already captured canonical value remains visible in every context containing it.

Two waves of four parent additions, two fresh root children and one fresh grandchild exercise six contexts. Conservative notifications to old descendants do not manufacture membership or produce false changes. Rewriting a first-wave value changes only the root and its two fresh children; rewriting an original inherited value changes all six contexts. Duplicate parent addition returns the same canonical reference and produces no semantic change.

## Evidence

- Nine fresh JVMs: three selected variables × flags on/off/verify; all exit successfully.
- Eighteen old/stream traces replayed independently: 882 full views, 33,192 serialized value entries. Paired traces and flag variants are byte-identical for each selected variable.
- Each trace has six baselines, 49 observations, eight added entries and nine term changes; no resets, removals, reordering, deletion transitions, deferred or retired contexts.
- 1,836 native context-variable controls across positive and negative fixtures check exact canonical references/order, direct ID membership and reader agreement. Native enumeration runs outside observer checks; full authority/projection reads are fingerprinted for purity.
- 27 expected missing-event controls: unnotified addition fails only in root; unnotified late-value rewrite fails in root and fresh child; unnotified original-value rewrite fails in all three original contexts. Both journals report the same failures at observation and session finish. The oracle detects gaps and never repairs the projection.
- Native runtime, accepted diagnostic classes and inherited fixture class/source hashes are checked before compilation and after execution.

`ParentAddRunner.java`, `build.py`, `run.py`, `analyze.py`, build validation, raw compressed traces/logs, error controls and `summary.json` are retained in `tvalue-parent-add/`. Reproduce from repository root after rebuilding the recorded native, authority-stream and authority-contexts dependencies: run the build, run and analyze scripts in that order.

## Limits and next work

This fixture covers memory-only additions to existing variables and explicit post-mutation notification bridges. It does not qualify new variable creation, persistent storage, promotion, clear, native transaction hooks or rollback. No performance claim follows from these correctness runs. The accepted diagnostic baseline remains `StreamAuthorityJournal.java`; complete-corpus semantic equality and end-to-end inference speedup remain unqualified.

The next dynamic case is clear/promotion and context completion, followed by real hook/error-boundary qualification and the complete inference corpus before considering production changes.
