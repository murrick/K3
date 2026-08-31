# KANGER qualification module

This Maven module owns executable regression and invariant qualification gates. It depends on the production modules it qualifies; production modules do not depend on it.

Java package names intentionally remain unchanged where a gate verifies package-private lifecycle or storage contracts. Command-owned shared test fixtures are consumed through the command module test artifact rather than borrowed source roots.
