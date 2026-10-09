# KANGER qualification module

This Maven module owns executable regression and invariant qualification gates. It depends on the production modules it qualifies; production modules do not depend on it.

Java package names intentionally remain unchanged where a gate verifies package-private lifecycle or storage contracts. Command-owned shared test fixtures are consumed through the command module test artifact rather than borrowed source roots.

## Live session options parity

`org.kanger.KangerOptionsRunner` runs the historical/completed logical corpus and
structured-set regressions twice in the same JVM, first with all eight session
optimizations disabled and then enabled. It also checks individual overrides,
child-transaction inheritance, another User's isolation, and time-zone validation.
Run without arguments for offline execution, or with `smart` for a disposable
SMART storage. `KangerCanonicalConsoleRunner` covers the operator command surface.
