# TValue: explicit observer connection and session ownership

This experiment continues `3a37a7b6509f7c3335b4b6d00b18f19ed28f9b1e` on the exact qualified native base `5d5f6aff271f1abf371fbde55d637744c53f0bc3`. Production sources, defaults, reactor and normal qualification source roots are unchanged. Native wrappers are generated evidence only.

## Connection contract

The generated runtime depends on `TValueObserver` and `TValueObservation`, not on diagnostic journals or `ConsumerHooks`. No observer is attached by default. `QualifiedJournalConsumer.open()` checks the protocol marker on all three instrumented classes, attaches its observer exclusively, starts both journals, and returns a thread-owned `AutoCloseable` session. The existing `begin()`/`finish()` facade remains compatible with the fixture.

```java
try (QualifiedJournalConsumer.Session session = QualifiedJournalConsumer.open()) {
    // Bounded, single-threaded resident native operations and observations.
    List<String> immutableTrace = session.finish();
}
```

Only successful finish exports a trace, after both full authority checks and journal comparison. Opening on vanilla or partially instrumented classes refuses with `NO_INSTRUMENTATION` before starting journals. A second attachment is rejected without disturbing the owner. Finishing/closing another thread's session is rejected. A foreign-thread callback taints the active attachment and produces `ADAPTER_FAILURE` on the owner's finish. This is a defensive boundary, not qualification of concurrent native operations.

The bridge catches callback failures and records attachment failure, preserving the native operation's result or exception. Closing without finish abandons the diagnostics, releases observer ownership, removes both thread-local journals and clears captured adapter context references. Closing an already closed session on its owner thread is harmless. After abandonment or refusal a fresh session can start.

Protocol markers identify this frozen generated patch; they are not a security mechanism or a proof of arbitrary instrumentation coverage. The recorded source and class hashes qualify the actual wiring. No production integration or cost claim is made.

## Reproduction and evidence

With the prerequisites described in the diagnostic module README:

```bash
python kanger-qualification/diagnostics/build_observer.py
python docs/tvalue-observer-session/run.py
python docs/tvalue-observer-session/wiring.py
python docs/tvalue-observer-session/analyze.py
```

The build compiles clean and hooked native runtimes without the diagnostic implementation, compiles the module against the clean runtime, then builds fixture classes separately. All runtime runs use the standalone diagnostic jar without fallback diagnostic implementation classes. Clean native classes match the previous native-only manifest. Removing recorded insertions recovers the native source byte for byte; the changed native classes remain Mind, Mind$1, TValueFactory and TValue.

The final matrix contains 28 JVMs: 18 prior consumer/parity runs across three repetitions and on/off/verify flags, six owner/connection runs, and four missing-instrumentation runs. The latter cover the real vanilla classpath without bridge API and each missing marker individually. The prior 63 successful exports, native control rows, diagnostics and console output are compared byte for byte with the previous module evidence. Root-clear recycled-ID exports remain refused. Observer faults exercise successful native operations and a null setter's original NullPointerException with callbacks disabled and throwing.

[summary.json](tvalue-observer-session/summary.json), [build-validation.json](tvalue-observer-session/build-validation.json), [structural-check.json](tvalue-observer-session/structural-check.json) and [manifest.json](tvalue-observer-session/manifest.json) record the results. Both full journals remain enabled. Defensive `JOURNAL_DISAGREEMENT` is still not fault-injected.

## Remaining scope

SMART persistence, the complete inference corpus, arbitrary extensions and concurrent sessions remain unqualified. Native recycled IDs after root clear remain unrepaired. No performance measurement is included. The next bounded step is measuring this exact bridge with no attachment and with diagnostics attached, retaining native parity and the full oracle, before any production integration decision.
