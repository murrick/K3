# Compact current stamp experiment

Base `develop/3.8.0 @ 1c943d02d39bd33ebd91fabb7c4190112ab49459`, live checked 2026-10-04. `kanger.experiment.compactCurrentStamp` is default OFF. All eight integrated optimizations retain their defaults.

For an exact ArgumentsList, equalsStamp gathers a fresh ITerm array instead of a temporary ArrayList and its growth backing storage. Full getTVariables enumeration, both deletion checks, all variable isEmpty/getValue reads and their order precede target-list size and term comparisons. It retains term object references (no ID/value memoization) and the existing ParametersIncompleteException catch. Subclasses use the original virtual getStamp path. Public getStamp remains an owned mutable ArrayList. No state is reused across comparisons, no persistence or knowledge representation changes.

Focused qualification compares an independently copied old equalsStamp body with fresh identical fixtures, complete ordered callback traces, result and original exception identity. Nineteen scenarios cover empty/single/multiple/mismatched lists, value reads before early mismatch, incomplete variables, empty/null terms, custom lists and stamp/enumeration overrides, later callbacks mutating earlier terms and argument structure, target callbacks mutating later comparisons, deleted variables and null targets. Additional built-in binding/deletion and public snapshot ownership checks. Marker: COMPACT_CURRENT_STAMP_OK scenarios=19 checks=47.

Local extended qualification, CI Java 8/21/26 × eight prior OFF/ON × experiment OFF/ON and clean/OFF/ON/ON/OFF/clean timing are pending. No merge or default enable is requested.
