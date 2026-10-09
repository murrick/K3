# TValue diagnostic source set

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
