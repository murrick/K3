# Diagnostic qualification against the SMART development baseline

Experiment parent: `a6d5599f7fafd996be45cfbb914d92100dd1f0c4`.
Exact development head: `5d5f6aff271f1abf371fbde55d637744c53f0bc3` — Merge stabilized 3.8.0 federation checkpoint.

The development branch advanced after the memory-context experiments. This docs-only stage rebuilds the unchanged diagnostic journal/readers and fixtures against that exact native source revision before continuing lifecycle qualification. It does not merge development into the experiment, modify engine code, or change defaults.

## Result

All 21 fresh JVMs pass: nine ancestry cases, nine late-parent-addition cases and three reporter/clear scope cases, with optimization flags ON/OFF/VERIFY. Both diagnostic journals produce identical traces. Ancestry and late-addition traces and expected missing-event error files are also byte-identical to the prior native baseline for every seed/selected variable and flag.

The rebuilt core retains the qualified memory semantics: existing descendants keep captured parent membership; new children of the current root include late additions; a new grandchild of an old child retains the old child's membership. Canonical payload term rewrites propagate only where that value is resident. Scoped clear removes all keys and a subsequent add creates the expected new key; missing notification is detected by a fresh complete oracle, with no repair.

## Evidence and reproduction

- Fresh isolated detached checkout of exact develop HEAD; recorded tree, compiler, Java version, all source hashes and 730 compiled class hashes. This count includes native classes and diagnostic/qualification helpers.
- 42 paired traces replayed: 3,642 authority views and 130,470 serialized entries.
- 63 expected missing-event cases detected by both journals at observation and finish.
- Native reference/order controls, observer fingerprint checks, three-context setter fanout and isolation, six-context late-addition membership, duplicate-add no-change, resets and deletion visibility retained from the unchanged fixtures.
- Three scope JVMs each run 1,028 route serialization cases, 1,028 reporter map cases and ten native authority states.
- The native TValue and TValueFactory sources are unchanged between the experiment base and develop. Mind/User and other core paths changed substantially; Escalera now consults live per-User optimization options instead of static/direct JVM option reads. This stage exercises the JVM-default fallback with fresh Users, not interactive live option switching.

`docs/tvalue-smart-base/` contains build/run/analyze scripts, explicit source manifest, full build/hash proof, recorded development changes, logs, raw traces, negative controls and summary. To reproduce, create a clean detached `../K3-smart-native` checkout at the recorded development head and supply `../tooling/ecj.jar` (hash recorded). From the experiment repository root run `python docs/tvalue-smart-base/build.py`, then `run.py`, then `analyze.py`. The build intentionally uses the legacy DUMB classes for the existing memory fixtures, while compiling the current native core, UDF, command, bootstrap and console sources. It uses no previously compiled native classes on its compilation/runtime classpath.

## Qualification boundary

This verifies the established memory diagnostic fixtures against the development revision that introduced the SMART/federation foundation. It does **not** qualify the SMART backend's Context storage, immutable revision publication, reopen, persistent ownership, federation inference, native journal hooks or rollback. Those require backend-specific fixtures and an observer capable of reading that physical representation; the existing reader deliberately rejects unsupported storage classes.

No complete inference corpus or performance comparison was run here, and no production integration is proposed. The accepted diagnostic implementation remains `StreamAuthorityJournal.java`. The next lifecycle experiment can now use this recorded native baseline for promotion/context completion, while SMART storage qualification remains a separate explicit task.
