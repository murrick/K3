# TValue persistence boundaries and owner-sensitive observation

This stage adds diagnostic hooks for native `TValue.apply`, `applyMap` and `setId`. Forty-six fresh matrix JVMs meet their stated expectations, including six expected identity-mutation rejections. Independent replay reconstructs every one of 7,561 native authority views in 19 traces. The qualified projection remains ordered TValueID/TermID/context-deletion bits with stable unit and variable IDs.

**Observation transparency for owner-sensitive operations is unqualified.** The native owner witness demonstrates that a journal scan rebinds a TValue from child to parent and changes the deletion flag emitted by `pack()` from 1 to 0, even though the parent's recorded canonical projection is unchanged. Native `forEach` alone produces the same effect. No ownership compensation or production fix is introduced.

Production sources, qualification runners, workflows, defaults and prior evidence remain unchanged. Parent: `b2c13ae8a4cb99f177a200a22b6a23748491bf0a` on `experiment/3.8.0-tvalue-metadata-observation`.

## Owner witness: a measured boundary

The fixture creates a root TValue inherited by a child. Only the child marks it deleted. The TValue's owner reference is set to the child; the stored owner ID remains the root's ID. The fixture reads `pack()` directly before and after each measured operation, without an intervening factory lookup, and checks both deletion maps, all identity/payload IDs and owner reference directly.

| Measured operation | Owner reference afterward | `pack()` deletion flag | Root canonical tuple and deletion bit |
|---|---|---|---|
| No read | Child | 1 → 1 | Unchanged; root deletion remains false |
| Root `TValueFactory.forEach` | Root | 1 → 0 | Unchanged; root deletion remains false |
| Root journal observation | Root | 1 → 0 | Unchanged; no root bucket delta emitted |

Each case runs in clean and shadow builds, with all eight experiment flags ON and OFF: 12 JVMs. Complete native stdout matches between builds for the same measured operation. The journal-read case also runs the observer explicitly in the clean control, so the control separates the native read effect from setter instrumentation. Comparing its result with the no-read control establishes the transparency failure; matching the same-read controls does not waive it.

The source path explains the result. `Step.getData(Mind)` calls `IUnit.setMind(mind)` on resident units. `Escalera` lookups and iterators call this method; the journal's full capture and ordered bucket reads use those native paths. `TValue.pack()` evaluates `isDeleted` with the object's stored Mind reference. Rebinding that reference can therefore affect serialization without changing the context-specific deletion bit that the journal records.

This narrows earlier diagnostic claims. Exact replay of the three-field projection and native fixture equality do not prove preservation of TValue owner references or every owner-sensitive operation. The previous report's statement that object owners are not rewritten is superseded for TValue: no explicit compensating owner write was added, but the invoked native read path itself writes the owner. The prior evidence is retained unchanged. This witness does not establish a cause for the earlier concurrent corpus differences.

## Persistence bodies and partial writes

The temporary wrappers save original unit, variable and term IDs, execute the original body and notify the observer in `finally`. Original return values and exceptions remain native. Reversal of all wrappers and prior hooks recovers Mind, Linker, TValueFactory and TValue source byte-for-byte.

| Operation on an observed object | Native consequence | Observer qualification |
|---|---|---|
| `applyMap`, same unit/variable IDs, new TermID | Clears resident references after all fields parse; hydration remains lazy | Surviving TermID change recorded without term hydration |
| Binary `apply`, same unit/variable IDs, new TermID | Keeps existing resident references; `getValue` can return the old non-null term while the TermID differs | ID projection change recorded; stale resident reference remains native |
| Either restore with deletion=true | Sets deletion in the supplied owner context | Context deletion change recorded |
| Either restore with deletion=false after deletion=true | Does not clear the existing deletion mark | No false resurrection delta |
| Map write inside a TValue checkpoint, followed by release | Existing object's payload mutation survives addition rollback | Surviving net change recorded after deferred boundary |
| Truncated binary packet after TermID is read | Throws `OutOfBufferException`; TermID changes, variable ID and resident term survive | Partial native TermID write recorded |
| Map with valid new TermID but invalid variable ID | Throws `NumberFormatException` before references clear; partial TermID write survives | Partial native TermID write recorded |
| `setId(sameID)` | Executes native assignment | No net projection change |
| `setId(otherID)`, map ID change, or truncated binary packet after ID write | Cache key remains old while object ID changes; new ID lookup misses | Expected identity rejection after native execution; no repair |
| Same-ID unregistered persistence probe | Mutates only that separate object | No invalidation of the registered canonical object |
| `setMindId(otherID)` | Changes owner ID | Outside recorded projection; no bucket delta |

Restoring a TermID through these methods does not reindex the factory's semantic hash table. Direct identity changes do not repair cache keys or variable routing. Native deletion=false is not an instruction to clear the deletion map. Binary `apply` is not a resident-reference refresh. These are measured native consequences, not changes made by the experiment.

Identity-changing writes remain rejected even if the full projection can be enumerated afterward. This rejection is an observer qualification result and does not undo the native write. Failed sessions remove their ThreadLocal state and allow a fresh session. The preceding object-identity registration, descendant routing, dirty selection and independent full oracle are unchanged.

## Validation and evidence

| Gate | Matrix JVMs | Result |
|---|---:|---|
| New persistence fixture, clean/shadow × ON/OFF/VERIFY | 6 | 22 native assertions per fixture; complete native stdout equal; shadow traces identical across flags |
| Previous metadata fixture, shadow ON/OFF/VERIFY | 3 | Existing 19 native assertions plus diagnostic assertions; complete stdout and traces identical to preceding stage |
| Previous dirty journal safety, shadow ON/OFF/VERIFY | 3 | 83 assertions per JVM; complete stdout and traces identical to preceding stage |
| Exact candidate replay, clean/shadow × ON/OFF | 4 | Four complete frozen 47-source oracles, plus exact small/external rows; logs match preceding clean evidence |
| Transaction state/work, clean/shadow × ON/OFF | 4 | 20 operations each; complete native stdout, state and work files match preceding clean evidence |
| Identity-changing writes, clean/shadow × ON/OFF | 12 | Six clean controls, six expected rejections; native object/cache outcomes equal |
| Owner read witness, clean/shadow × ON/OFF × three operations | 12 | Confirms native read and journal read change owner-sensitive serialization; transparency remains unqualified |
| Native commit exception atomicity, clean/shadow ON | 2 | Original direct runner completion markers; no reflective wrapper around `System.exit` |
| **Total** | **46** | All matrix processes exit zero with empty stderr; negative cases explicitly assert the expected failures |

The 19 compressed positive traces independently replay all 7,561 authority views. Each new persistence trace contains 30 views, 13 changed buckets, eight surviving TermID transitions, two logical deletions, two restorations and one deferred observation. Unchanged deletion=false, resident-reference restoration, owner-ID-only mutation and unregistered probe boundaries emit no false projection change. All dirty selection and counter checks remain independent of full-snapshot repair.

All 686 preceding clean class files rebuild byte-identically. The shadow build changes the same nine native class files as the preceding payload stage: eight original dirty-hook class files plus TValue. The diagnostic journal helper source is byte-identical to the preceding stage. Compilation uses ECJ 3.33.0 targeting Java 8; matrix execution uses Java 17.0.20. This docs-only publication does not claim a new Java 8/21 CI run.

The first build failed because the new diagnostic gap runner called a boolean variable as a method. A premature two-case run completed one clean fixture and failed shadow startup with `ClassNotFoundException` because the build was incomplete. These two executions and the build failure are archived in `rejected-attempts` and excluded from the 46-case qualification. The corrected full matrix ran once. A parser expectation miscounting the new trace's TermID transitions is also retained there; the independent replay had already counted eight transitions, and the assertion was corrected to match the explicit fixture sequence without changing native data or rerunning it.

## Remaining work

The observer still uses native read paths that rebind TValue owners. Copying owner references back afterward would be another native mutation and is not a transparency proof. A future observer needs a bounded read path that preserves owner-sensitive behavior, with explicit handling or rejection of unresolved storage units.

The recorded projection does not contain owner references/IDs or resident term/TVariable references. Arbitrary worker threads, custom Mind/TValue/factory implementations and storage hydration are unqualified. No DUMB adapter or production API is changed, and no DUMB integration validation is claimed by the direct `apply`/`applyMap` fixtures.

There is no production consumer, inference shortcut, performance claim, automatic index repair, ownership compensation or merge into develop. The two preceding rejected overlapping-preparation corpus attempts remain archived and unqualified; this stage neither reruns nor relabels them.

Reproduce from this worktree with the existing compiler and repository dependencies:

```sh
python docs/tvalue-persistence-boundaries/build.py
python docs/tvalue-persistence-boundaries/run.py
python docs/tvalue-persistence-boundaries/analyze.py
```

All scripts, native stdout, expected-rejection diagnostics, stderr, execution metadata, full transaction state/work, compressed traces, original/temporary source hashes, class hashes, structural checks and archived failed setup attempts are in [tvalue-persistence-boundaries](tvalue-persistence-boundaries/). [summary.json](tvalue-persistence-boundaries/summary.json) explicitly records `owner_observation_transparency_qualified=false` and the retained corpus failure status. [manifest.sha256.json](tvalue-persistence-boundaries/manifest.sha256.json) covers all new report/evidence files except itself.
