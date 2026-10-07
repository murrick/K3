# Post-successful native TValue root-update boundary (3.8.0 diagnostic)

Parent: `c7319da9c67a0e74169e754e80d00a3e915b6028`.

A temporary bracket around root TValueFactory.update defers its Base.get events
until the complete native method succeeds. The unchanged post-materialization
registration checks then run against settled lookup metadata. For already known
IDs and unchanged variable routes this qualifies the preceding root-update
transition, without copying an oracle, performing storage reads or repairing
native indices. It also preserves a descendant's different canonical object
when that descendant still selects its old memory entry.

**83 fresh JVMs meet all expectations**: 12 new controls and the full preceding
71-case matrix rerun. All 71 regression logs and 33 successful traces are
byte-identical. Independent replay validates 36 successful traces and
**9,267 authority views**. Native production source and optimization defaults
are unchanged; all instrumentation is compiled into separate temporary overlays.

## Event bracket, not implicit publication

The native update body runs unchanged. Its temporary wrapper calls beginUpdate,
executes the entire original body, sets success only after normal return, and
calls endUpdate in finally. A successful no-op native update is still a normal
success. Only an exact native root Mind, its currently assigned native factory
and current native DUMB TValue Base are eligible. Memory-only factory updates
are not bracketed. Root updates inside nested/reentrant operations are not
qualified by this stage.

While this bracket is active, exact Base/Sapato events for that same Base are
retained in a LinkedHashMap keyed by operational ID. The latest returned step
for an ID replaces any earlier step. This records actual get results only:
there is no traversal to discover objects, generation seed, full snapshot,
factory lookup, native owner write, index initialization or LRU access.

After successful return the frame is removed and every retained event is passed
to the preceding materialized method. Its eligibility rules remain intact:
active initialized/non-reset contexts, already known IDs, same variable route,
current storage identity, actual resident returned-node identity, valid native
lookup metadata and actual context lookup visibility. Unknown IDs and reset
contexts are not registered through this signal. The full reader/oracle remains
checking-only at later observation boundaries. Added begin/end methods, frame
state and deferral line can be removed to recover the previous journal
byte-for-byte. Removing the factory wrapper recovers the previous clean/shadow
factory sources byte-for-byte, including the full original native update body.

If native update throws, the frame is discarded without registration and the
journal records `unsupported incomplete native factory update`. The wrapper
preserves the original native exception and partial writes. No compensation,
retry, successful oracle seed or pending-event admission follows the failure.
This diagnostic state is unsuitable for further incremental use; closing it
retains its qualification error. It is not a transactional storage repair.

## Parent and child are separate lookup authorities

The real DUMB fixture creates three native values, disables Data caching and
warms the native parent/child lookup indices. Both contexts initially register
the same memory objects. Root update redecodes them as new TValue objects.
The parent now selects stored replacements, but the pre-existing child still
selects its old memory objects. A raw resident-cache iteration obtains the
middle witness; an explicit native getRoot resolves endpoints using the newest
ID. No follow-up Base.get of the middle ID is needed for registration.

The successful trace has exactly three `TOUCH reason=materialize` records, all
in the parent. It demonstrates:

* A setter on the replacement changes the parent projection only.
* A setter on the old object changes the child projection only. That object is
  retired from the parent's observer registry, not from the child's registry.
* Native child transaction(parentFactory) plus explicit native iteration changes
  its lookup generation. The existing RESET seed registers the replacement.
* A subsequent replacement setter changes both projections. A detached old
  setter changes neither and emits no additional canonical setter touch.

Diagnostic observations preserve owner/index fingerprints and Base read
counters. Native generation iteration can hydrate/rebind owners; it remains
explicit fixture work rather than a diagnostic read. An independent sibling
reservation prevents premature native pack. All reservations end at zero.
Final native cleanup can remove these synthetic bindings, so this does not
establish complete semantic database publication/reopen behavior.

## Partial native failure control

A second fixture replaces the middle Step's data with a test TValue subclass
whose pack() throws a fixed IllegalStateException. The full diagnostic baseline
was captured before this fixture change. The oldest ordinary record is appended
and decoded successfully before native serialization attempts the middle record.
The custom callback runs exactly once, from native serialization. Diagnostic
code never invokes it. The original exception class/message, first written
record, second write attempt and partially advanced native root are verified
before any cleanup. Clean/shadow native behavior rows agree under ON/OFF/VERIFY.

The shadow prefix contains only its original BASELINE/VIEW, with zero pending
materialization TOUCH records. Closing it rejects the incomplete update; a
separate empty session then closes normally. Explicit fixture cleanup happens
only after these checks. This is a controlled serialization failure, not proof
of arbitrary I/O failure recovery or process-crash safety.

## Evidence and limits

New successful controls run clean/shadow under ON/OFF/VERIFY (22/25 assertions).
New partial-failure controls also run clean/shadow under all three modes
(8/9 assertions). All 12 exit codes are zero and stderr empty. Three successful
new traces have 17 authority views each and are identical across flag modes.
The three rejected prefixes are retained separately and are not successful
traces. No new fixture needed a failed preliminary attempt.

The full 71 regression JVMs include six preceding rematerialization positives,
56 memory controls, three frozen original negatives using their original
classpath and six unchanged variable/index rejection controls. Their committed
strict analyzers, 47-source candidate oracles, transaction state/work checks,
ordered publication gates and independent trace replay all pass. Unsupported
materialization outside an eligible successful update still rejects. Previous
native/helper class directories remain hash-identical; the new overlay changes
only the TValueFactory family, journal family and new runner family. Factory
wrappers may add stack frames or shift temporary line numbers; byte-identical
instrumented exception-stack output is not claimed.

Both prior evidence manifests are verified unchanged. The earlier root-update
rejections are preserved as historical baseline evidence, and the two older
concurrent corpus failures remain unqualified. There is no scheduler-neutrality,
complete inference frontier, consumer integration or speedup claim.

Caller-enforced quiescence remains mandatory. Worker threads, nested updates,
callback reentrancy, mixed storage generations, unknown/changed IDs, nonresident
returned steps, invalid final lookup metadata and visibility changes without a
qualifying get/reset/publication signal remain outside this qualification.
Typed/composite persistent publication and partial-update recovery require
separate contracts. In particular, the event queue is bounded by actual gets in
the native method; it does not promise to find objects made visible solely by
some unrelated native operation.

Next: composite persistent publication, especially settlement and rollback paths
that alter lookup visibility or object registration after preparation.

## Reproduction

Prepare the preceding committed class directories in the shared build tree, then:

```
python docs/tvalue-update-boundary/build.py
python docs/tvalue-update-boundary/run.py
python docs/tvalue-update-boundary/regression/run.py
python docs/tvalue-update-boundary/analyze.py
```

ECJ 3.33.0 targets Java 8; runtime Java 17.0.20. The stage manifest covers this
report, source/scripts, both overlay class manifests and every final result.
