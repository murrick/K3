# Boundary-batched TValue journal prototype

The diagnostic observer reconstructs the native ordered canonical TValue projection at every recorded boundary. All 28 fresh JVMs pass, and replay of its baseline/reset/delta records agrees with all 31,562 complete authority snapshots. The 47 source-keyed candidate answers, full transaction states and work vectors, corpus semantics and nine complete SON snapshots retain their existing oracles.

Base: `experiment/3.8.0-linker-dependency-lifecycle` at `e636d1957a6841fab4f2c23f6bb399b8cfd1f848`. This branch adds only this report and standalone diagnostic sources/evidence under `docs/tvalue-journal/`. Production sources, qualification-module sources and CI workflows are unchanged. There is no journal consumer, execution frontier, default flag change or runtime speedup claim.

## Observer contract

`TValueStateJournal` has an explicitly opened ThreadLocal session. Each native Mind object receives a session-local context ordinal, its native ID, generation and invocation number. The ordinal distinguishes fresh native Users whose Mind IDs can repeat. A baseline or reset anchors a complete projection; a change records the previous and next ordered bucket for one variable. Each entry retains canonical TValue ID, Term ID and logical deletion status. Logically deleted identities remain in the enumeration according to the native factory contract.

The snapshot discovers variable IDs from native TValue metadata and uses unregistered variable shells with those IDs to call native `TValueFactory.forEach`. It does not hydrate canonical TVariables or change their ownership. It neither reads nor writes the current binding or transient function-result projection. All observations also record a complete `VIEW` for an independent replay check.

Observations occur after successful Mind construction, before linking, after complete descending and ascending rotators, before and after native Mind settlement, and at session end. Local TValue checkpoints and composite settlements defer observations while open. Reset advances the generation and establishes a new baseline. Retired children are retained for diagnostic bookkeeping but are not rescanned after their objects may have rebased to a parent.

This is a **boundary-batched net projection**, not a mutation-by-mutation causal log. Changes that appear and disappear between observations are invisible. An inner checkpoint commit followed by outer release yields no canonical addition delta. A surviving outer commit yields the actual ordered bucket delta. The observer does not assume that every kind of mutation is undone by a local checkpoint: the native state after settlement remains the authority.

## Temporary instrumentation and structural checks

`build.py` creates temporary copies of Mind, Linker and TValueFactory outside the repository source tree. Hooks run only inside explicit diagnostic sessions. Mind wrappers retain the original private commit/release bodies and observe their completed outcome; rejected user commits retain their live child until native release. Linker retains its full traversal and direction-specific donor views. TValueFactory hooks record completed native checkpoint/reset operations, without modifying add, lookup, current binding or pack algorithms.

Removing the exact hooks/wrappers and reversing the two private body renames recovers all three original source files byte-for-byte. The clean rebuild matches all 663 classes from the preceding diagnostic baseline. Instrumentation changes eight shared classes: Linker and its four anonymous classes, Mind and one anonymous class, and TValueFactory. These hashes and structural results are committed separately; no bytecode-equivalence claim is made for instrumented classes.

Diagnostic failures are collected and rejected at session finish, rather than used to steer inference at a hook. Snapshots can hydrate native TValue objects through the existing cache. The observer also retains strong references and full snapshots throughout a session. Therefore the qualification demonstrates the tested semantic equivalence, not arbitrary storage/extension transparency, retention neutrality or acceptable production overhead.

## Native settlement qualification

The dedicated native gate passes 62 assertions in ON, OFF and VERIFY modes. Its three complete traces are identical: 26 contexts, 98 authority observations, two deferred observations, 15 bucket changes and one reset per JVM.

| Native boundary | Qualified outcome |
| --- | --- |
| Nested inner commit, outer release | No false canonical delta; transient explicitly selected current remains the rolled-back value; native action behavior is preserved |
| Surviving nested commit | One complete ordered bucket delta with all surviving values |
| Duplicate add | No canonical delta |
| Logical deletion | Same canonical identity remains enumerated, with deletion status changed |
| Resurrection | Same canonical identity is restored in a distinct observed delta |
| Physical pack removal | Removal is separate from logical deletion; remaining native order is preserved |
| Native clear/reset | Empty canonical projection, generation reset anchor and native current clearing |
| Accepted child publication | Exactly one parent bucket delta |
| Native conflict rejection | No false parent publication; all reservations close |
| User rejection followed by release | Rejected commit retains the live child; release publishes no false parent delta |
| Injected native settlement exception | No false parent publication; exception/reservation behavior remains native |

The conflict fixture deliberately makes the child's Rule cache non-sequenced with its parent and supplies native stored operands, so the real Analyzer revalidates the collision. Two earlier failed diagnostic fixture attempts are preserved under `failed-attempts`: the first used unstored operands, and the second still followed the sequenced path. Corrections changed the harness only. The native Rule top anchor is not necessarily the newest sibling addition, and compiled clauses are not automatically stored records.

## Equivalence matrix

| Fresh JVM group | Count | Oracle |
| --- | ---: | --- |
| Dedicated journal gate: ON/OFF/VERIFY | 3 | 62 assertions per mode, identical complete traces |
| Clean/journal × ON/OFF × five existing native runners | 20 | Exact candidate replay, 20-operation transactions, 49 lifecycle assertions, 31 dependency assertions, 88 donor assertions |
| Clean/journal SON and five-set corpus: ON | 4 | Six complete SON snapshots and full corpus completion/semantics |
| Journal SON repeat: ON | 1 | Three further complete snapshots; identical full journal trace |
| Total | 28 | Exit zero, explicit completion markers, empty stderr |

Four clean/journal candidate runs each match the frozen source-keyed map for all 47 sources and retain the small/external answer vectors and reservation checks. Corresponding clean/journal native runner logs match exactly after stripping the diagnostic completion row. Full transaction state and work files match within each flag mode; ON/OFF work vectors are not cross-mode equality oracles.

The corpus comparison removes diagnostic timings and uses the existing normalization only for the three native concurrent payload sets (`set_08_01`, `set_08_02`, `set_08_03`); all other semantic rows remain exact. Both runs require the full corpus completion marker.

Sixteen journal traces independently replay successfully. The replay checks each delta's previous bucket, stable Term IDs for retained TValue identities, full ordered snapshots, canonical identity uniqueness, logical deletion bits, generation anchors and retirement. This validates recorded projection consistency; it is not an independent proof that every possible mutation/read channel has an event. The native gate also checks expected deltas against specific settlement outcomes.

Java 17.0.20 with ECJ 3.33.0 targeting Java 8 was used locally. ON/OFF explicitly sets all eight integrated optimization flags; VERIFY additionally enables TValue-index and solve-sync verification. No new Java 8/21 CI run is claimed for this docs-only branch. The preceding production donor-scope baseline's complete successful CI is recorded in the inherited dependency report.

## Next boundary

A finer incremental write observer can now be checked in shadow against these complete boundary snapshots before any scheduler consumes it. It must distinguish checkpoint survival, actual child settlement, deletion, resurrection, removal and reset without equating add attempts with canonical changes. Current bindings, domain stamps, FValues, callbacks, raw aliases, hypotheses and the remaining dependency channels still require separate observation or conservative full traversal. A TValue-only journal remains insufficient for an execution frontier.

Sessions are thread-local: worker-thread effects are not causally attributed by this prototype. Later recorded full views may reveal their surviving projection, but no concurrency coverage or arbitrary custom Mind/factory/operation guarantee follows. Same-state round trips between observations are also outside the recorded contract.

## Reproduction

From the checkout root, with ECJ at `../tooling/ecj.jar`:

```sh
python docs/tvalue-journal/build.py
python docs/tvalue-journal/verify_structure.py
python docs/tvalue-journal/run.py
python docs/tvalue-journal/analyze.py
```

The build writes only temporary instrumented sources/classes to `../build/tvalue-journal-*`. Complete compressed logs/traces, transaction state/work, class hashes, instrumentation hashes, the independent analyzer and `summary.json` are under `docs/tvalue-journal/`. `manifest-sha256.json` covers this report and the evidence, excluding the manifest itself. Failed probes and the preliminary successful manual gate are archived separately and are excluded from the 28-JVM matrix.
