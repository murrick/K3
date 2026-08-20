# WHAT'S NEW — KANGER III 3.7.0

KANGER III 3.7.0 is a stabilization and architectural-convergence release. Its main result is not a new language surface, but a substantially stricter runtime model: transaction state, storage state, command handling, Browser/Console presentation and diagnostic boundaries are now aligned around explicit ownership and qualification rules.

## Transaction and storage model

- The active workspace is represented by an explicit U-stack (`U0...Un`) rather than implicit file/storage switching.
- Storage lifecycle operations have explicit admission rules and preserve transaction invariants.
- Offline-to-storage workspace insertion is qualified and collision-aware.
- Commit, rollback, squash, checkpoint, close, use, drop and reindex behavior are exposed through the canonical command layer.
- DUMB storage reliability qualification remains part of the release gate.

## Canonical command surface

- Console and network clients converge on the shared command parser/formatter and canonical command processor.
- Rule, base, function, solution, transaction, source and storage command families have explicit intent rather than first-character command ambiguity.
- Console-local conveniences remain presentation adapters rather than semantic authorities.

## Browser and Server stabilization

- KANGER product identity is `3.7.0`; public API identity remains `1`; deployable Server identity remains `server-0.18`.
- Browser session/bootstrap, containment, operation/snapshot protocol and error presentation were hardened.
- Browser gateway and Console presentation were converged without moving semantic authority into JavaScript.
- Editor state and local-file behavior were separated from authoritative KANGER context state.
- The qualified Browser artifact contains 23 explicitly inventoried files.

## Hypothesis semantics

- Historical hypothesis generation is preserved, while completed optimization now keeps candidates that make the original query determinate either TRUE or FALSE.
- TRUE-only admission is explicitly rejected as incorrect semantics.
- Hypotheses exposed to clients are rendered as assertion-ready KANGER source.
- Examples:

```text
?$y son(John,y);  ->  !@y ~son(John,y);
?female(Tom);     ->  !~female(Tom);
```

- WHEN display and WHEN ACCEPT share the same materialization boundary.
- Corrected regression cardinalities include `set_06_07: 14` and `set_06_0A: 8`.

## Test isolation

Interactive visual tests no longer borrow the live Console user, database or transaction stack.

```text
options test [prefix]
```

runs under a disposable offline User/Mind, while:

```text
options test db [prefix]
```

runs under a disposable User/Mind with its own private DUMB database. Qualification proves that a live working storage and an open U1 remain unchanged across both modes.

## Compatibility identities

```text
KANGER product/Core release: 3.7.0
Core binary/serialization compatibility: 3.3
Public API: 1
KANGER Server: server-0.18
```

The compatibility value `3.3` is deliberately not used as the public release label.

## Known non-blocking debt

- Completed hypothesis optimization for the largest visible historical set remains comparatively expensive (approximately 10–12 seconds in the accepted characterization). Correctness takes precedence over optimization.
- Typed canonical diagnostics, friendlier storage-rebase error presentation, read-only runtime/system `status`, and expanded `.k` transport/import-export lifecycle are post-3.7.0 work.
- Resource quota/governor design is deferred research and is not part of the 3.7.0 architecture contract.

## Release boundary

`release/3.7.0` is an immutable repository snapshot created only after final closure qualification and explicit acceptance. Tagging, GitHub Release publication and production cutover remain separate explicit decisions.
