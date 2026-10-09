# Isolated TValue diagnostic source set

This stage follows `86ba48b40e8bfffd3ae33949210c649be883f7cb` and implements the first packaging step in the integration proposal. Nine diagnostic Java sources and four fixture/helper sources now live under `kanger-qualification/diagnostics`, outside normal Maven source directories. The reactor, normal qualification sources, production source and defaults are unchanged.

## Packaging proof

The Java files are exact copies of the qualified implementations, preserving package access and reflection behavior. Frozen experiment documents are retained. `source-origins.json` maps each of the 13 new source files to its prior evidence file, and the build checks byte equality. Every clean and instrumented class hash matches the preceding consumer-gate build, not merely the 730-class native baseline.

The explicit module source list includes both journals, full/raw readers, observation frames, recorded-link validation, native callback facade and checked consumer. The separate test list contains the three existing fixture/helper runners and ConsumerRunner. No Java source under `docs` is compiled in the new source-set build. Recorded native manifests and callback edits remain part of the qualification bootstrap and evidence verification.

An independent native-only compilation excludes every diagnostic class. The nine-source module then compiles against that classpath, and the four-source test set compiles against native-only plus module classes. All independently compiled class hashes must match the combined clean build. A reproducible jar contains module classes only, with fixed timestamps and exact entry hashes; native classes and fixtures are excluded.

The jar by itself does not connect callbacks to a vanilla core. This stage deliberately preserves the preceding consumer implementation: general observer availability, lifecycle ownership and a default-disabled native observer interface still need a separate patch. Normal core execution is not claimed as a qualified diagnostic connection.

## Runtime verification

The complete prior consumer matrix is rerun from module sources: three permutations × three flags × clean/instrumented runtime, 18 fresh JVMs. It retains 63 accepted exports, 27 recycled-ID refusals and 27 missing-event/adapter/no-session refusals. Native rows are byte-identical to the preceding characterization; all ordinary accepted exports match the prior journal traces. Independent replay validates accepted traces and immutable consumer results.

Three additional fresh JVMs run the independently compiled test classes with the standalone jar and a native-only instrumented runtime, under on/off/verify flags. That classpath contains no fallback diagnostic classes: the module must be loaded from the jar. Native rows, refusal diagnostics and all accepted traces must match the corresponding combined-build run exactly. These are packaging smoke runs, reported separately from the 18-JVM regression matrix. Across both sets, independent replay validates 84 accepted exports, 2,736 authority views and 6,336 serialized value entries. The standalone jar contains 24 diagnostic class entries.

The build still pins exact native commit `5d5f6aff271f1abf371fbde55d637744c53f0bc3`, preserves the original native code bodies byte for byte and verifies the unchanged clean baseline. Generated native callback sources remain under evidence; no core source is edited.

See [module README](../kanger-qualification/diagnostics/README.md), [build script](../kanger-qualification/diagnostics/build.py), [source origins](tvalue-diagnostic-module/source-origins.json), [build validation](tvalue-diagnostic-module/build-validation.json), [run.py](tvalue-diagnostic-module/run.py), [jar_smoke.py](tvalue-diagnostic-module/jar_smoke.py), [analyze.py](tvalue-diagnostic-module/analyze.py) and [summary.json](tvalue-diagnostic-module/summary.json).

## Scope

This is a source-set extraction and executable packaging qualification. It does not redesign the API, fix root clear ID reuse, integrate a production observer, qualify SMART persistence or concurrent sessions, rerun the full inference corpus, or establish a speedup. The adapter is still single-threaded diagnostic machinery, and the full oracle remains enabled. Earlier complete-corpus limitations remain in force.

The next step is the observer connection contract: an explicit disabled connection for normal native execution, session ownership and fail-closed detection of unavailable instrumentation. That should be reviewed and tested as a distinct behavioral patch rather than folded into a byte-identical relocation.
