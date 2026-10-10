# TValue diagnostic source set

## Current context-membership snapshot experiment

Each journal now retains a private ordered State array and replaces it only on context registration. Every traversal captures the array reference at entry, preserving reentrant registration and live retirement semantics. See [the context-snapshot report](../../docs/tvalue-journal-context-snapshots.md) for bounded qualification, callback allocation measurements and the extra registration cost. Prior experiment build scripts require their frozen source checkout.

## Current three-route allocation experiment

The metadata callbacks now sort and deduplicate three primitive route IDs without a temporary list/TreeSet. Both journals and full authority export gates remain active. See [the three-route report](../../docs/tvalue-journal-three-routes.md) for qualification, allocation measurements and reproduction using a frozen parent control checkout. Earlier build scripts below require their frozen source checkout; the current diagnostic sources intentionally differ in the two journal classes.

## Current fast disabled bridge experiment

The current bridge checks a volatile attachment before creating callbacks or entering dispatch. See [the fast disabled report](../../docs/tvalue-observer-fast-disabled.md). Reproduce with its `build.py`, `run.py`, `wiring.py`, `qualify.py`, `cost.py`, `cost_analyze.py` and `manifest.py` on pinned native `2422f7f6`. The previous bridge is preserved as a byte-identical control. Earlier scripts below require their frozen stage checkout.

## Selected native base after hygiene

The bounded observer qualification now targets native `2422f7f6d9e1af7608203b1566df64b5f29fe344`. See [the fresh-base report](../../docs/tvalue-hygiene-base.md) and its `build.py`, `run.py`, `wiring.py`, `cost_smoke.py` and `analyze.py`. Keep a clean `../K3-observer-native` checkout on that exact commit. At that frozen stage the diagnostic implementation was unchanged; all 34 requalification JVMs preserve the previous native controls and checked traces. Earlier build commands below preserve their original `5d5f6aff` binding. Old timing figures do not qualify the new base.


## Current observer/session experiment

The current implementation adds explicit observer attachment, exclusive thread-owned consumer sessions and mandatory `NO_INSTRUMENTATION` refusal. See [the observer/session report](../../docs/tvalue-observer-session.md). Runtime API sources are isolated in `runtime/`; they are compiled only into generated qualification runtimes. Production sources and defaults are unchanged.

Reproduce that frozen stage with `build_observer.py`, followed by `docs/tvalue-observer-session/run.py`, `wiring.py` and `analyze.py`, from the repository root. Use Java 17, ECJ 3.33 at `../tooling/ecj.jar`, the bundled jline jar, and a clean native checkout at `../K3-smart-native` pinned to `5d5f6aff271f1abf371fbde55d637744c53f0bc3`.

The implementation has nine diagnostic classes, two runtime API classes and six fixture/helper classes (the two new drivers are explicitly listed in `build_observer.py`). `open()` returns an `AutoCloseable` owner; `begin()`/`finish()` remain compatible. All sessions are bounded and single-threaded. Callback failure taints export and preserves native behavior. Abandonment drops journals and captured references. Both authority journals remain active.

## Observer cost experiment

[The cost report](../../docs/tvalue-observer-cost.md) measures clean, disabled and attached modes on the unchanged observer/session patch. Its additional `ObserverCostRunner` is compiled separately by `docs/tvalue-observer-cost/build.py`; run `run.py`, `analyze.py` and `manifest.py` in that directory from the repository root. The current observer build remains the prerequisite.

## Frozen source-relocation stage

The remainder describes the previous stage at commit `3a37a7b6509f7c3335b4b6d00b18f19ed28f9b1e`. Its build/hash assertions require that exact checkout; use the current commands above for the observer stage.


This source set isolates the qualified TValue diagnostic consumer and its bounded resident readers from experiment documents. It is deliberately outside `kanger-qualification/src`, the module's normal Maven test source directory. No reactor, Maven profile, production dependency or default is changed.

`src/` contains nine diagnostic classes. `test/` contains four executable fixture/helper classes. `sources.txt` and `test-sources.txt` are explicit source lists. Their Java contents are byte-identical to the recorded experiment sources; old documents remain frozen evidence, not the implementation location for this source set.

The classes remain in `org.kanger` to preserve the already-qualified package access and reflection contract. This packaging step does not redesign that API, make the adapter concurrent, or change callback failure handling. A later observer interface is required before general core integration.

## Build and run

From the repository root, with Java 17, ECJ 3.33 at `../tooling/ecj.jar`, jline at `lib/jline-3.13.0.jar`, and the exact native checkout at `../K3-smart-native`:

```bash
python kanger-qualification/diagnostics/build.py
python docs/tvalue-diagnostic-module/run.py
python docs/tvalue-diagnostic-module/analyze.py
```

The build pins native commit `5d5f6aff271f1abf371fbde55d637744c53f0bc3`. Its qualification bootstrap reads the frozen native source manifest and recorded experimental callback edits from `docs`. No Java source from `docs` is compiled. The module's Java compilation is also independently checked against a native-only classpath with no diagnostic classes. Fixtures compile separately against that native classpath and the module classes.

Outputs go to `../build`: clean and experimentally instrumented runtimes, native-only classes, independent module/test classes and a reproducible `tvalue-diagnostic-module.jar` containing diagnostic classes only. Source/class hashes, compiler commands and jar entry hashes are recorded in `docs/tvalue-diagnostic-module/build-validation.json`. Generated native wrappers remain experiment evidence, not changes to `kanger/src`.

## Use and limits

The checked consumer entry point is `QualifiedJournalConsumer.begin()` / `finish()`. Successful finish returns an immutable trace; typed refusals return no trace. Both underlying journals and the full authority comparison remain active. The callback facade records unsupported recycled identity and adapter capture errors without changing the native mutation's result.

**The jar alone does not connect callbacks to a normal core.** Use this API only with the explicitly instrumented qualification runtime and bounded, quiescent single-threaded fixtures. A vanilla runtime is not a qualified consumer connection. The matrix's clean runtime supplies independent native controls; its driver does not attempt diagnostic exports. Default-disabled observer wiring and a production integration contract are the next separate step.

SMART persistence, concurrent sessions, arbitrary extensions and complete inference-corpus equivalence are not qualified by this source relocation. Root clear ID reuse remains unrepaired. The defensive `JOURNAL_DISAGREEMENT` consumer branch remains unfaulted. This is diagnostic packaging, not a production speedup.

See [the qualification report](../../docs/tvalue-diagnostic-module.md) and [the integration proposal](../../docs/tvalue-integration-proposal.md).
