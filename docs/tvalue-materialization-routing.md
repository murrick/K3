# Post-native TValue materialization routing (3.8.0 diagnostic)

Parent: `50550406ae1761322d06dcb995e2bd4c00fdb78a`.

A temporary post-`Base.get` signal closes the preceding rematerialization
routing gap for **already known canonical TValue IDs**, the same variable ID,
current native DUMB storage and valid native lookup metadata. It registers the
replacement object separately in each affected context and dirties its known
bucket before the caller can mutate the returned unit. It retires displaced
instances only from that context's canonical observer registration. A detached
old instance can still exist elsewhere; this is not a heap-wide alias claim.

The final 71-JVM matrix meets all expectations. Without the new Base hook, the
original three negative controls still reject the missed setter; their output
and errors are byte-identical to the preceding stage. With the hook, the three
shadow controls accept the replacement setter in both parent and child, and
ignore later writes to the detached old instance in the canonical projection.

## Signal and registration conditions

The production tree is unchanged. A separate classpath compiles a temporary
Base wrapper that calls the complete original `get` body, then calls the
diagnostic signal, then returns the original IStep. Exceptions from the original
body bypass the signal. On a cache miss, original ID validation and cache
insertion finish before the signal; on a hit, it receives the original cached
result. Original Base source is recovered byte-for-byte by removing the wrapper
and restoring the method signature. Temporary instrumentation can add stack
frames; no byte-identical instrumented exception-stack claim is made.

All preceding journal methods are also recovered byte-for-byte: this stage adds
one registration method and imports. The existing six TValue setter wrappers,
Mind/Linker/factory hooks and read-only persistent helper are reused unchanged.
All 696 classes in each preceding clean/shadow directory remain hash-identical;
the new overlay contains the temporary Base family, diagnostic journal family
and new qualification runners. This is not a production build change.

The signal only examines active, initialized, non-reset journal contexts that
already know the ID. It checks exact native Base/Sapato/TValue classes, step/unit
ID and Base binding, active User/DB storage identity, returned-node identity in
the actual Base cache, and native Escalera lookup metadata. It reads the Base
cache by iteration, never access-ordered `get`, and performs no storage read,
hydration, owner write, index initialization, canonical lookup or snapshot.

| Condition | Registration decision |
| --- | --- |
| Unit already registered in this context | No new binding or dirty selection |
| ID unknown, context reset/uninitialized/retired, unrelated storage | No new registration |
| Actual memory lookup masks the stored unit | Keep that context's current binding |
| Native persistent membership selects the stored unit | Eligible for replacement binding |
| Root connection fallback selects the stored unit | Eligible for replacement binding |
| Child lacks persistent membership and connection fallback | No binding for that context |
| Returned unit is not resident or lookup index is invalid | Record qualification failure after native return |
| Materialized variable differs from the known variable | Record unsupported identity transition after native return |

For an eligible replacement, the signal removes old observed instances with the
same operational ID in that context, registers the returned object at the
already known route and emits `TOUCH reason=materialize`. It never discovers new
IDs or variables, updates native routing, copies an oracle result, or changes a
shadow view directly. The dirty reader and full oracle run at later boundaries.
Conservative bucket selection also covers a replacement's different payload
even when no setter event described that rematerialized payload.

## Actual native controls

The positive fixture reuses the preceding real DUMB setup: native opening of an
empty DB, native variable/value creation, TValue factory update and Base flush.
Base caching is enabled; Data caching is explicitly disabled using native
`cache.data.size=0`, so clearing Base causes real re-decoding. An independent
native sibling reservation prevents intermediate releases from triggering root
pack; it is released after the diagnostic sessions close. Synthetic bindings
can be removed by final native pack. This is not a complete semantic database
publication/reopen qualification.

The stable initial phase still checks root fallback, child no-fallback,
oldest-first layered ordering, contextual deletion, owner and lazy-index
preservation, local rollback and registered shared writes. Its three successful
shadow traces are byte-identical to the preceding stage.

The replacement phase keeps parent and child alive and initializes their native
lookup indices before turnover. Baselines register the old units. Native cache
clear and native gets return new decoded units; the signal emits six touches
for three IDs across two contexts. The unchanged resident helper still refuses
the stale Sapato anchor without repairing it. An explicit native iterator
refreshes its own anchor. The unflushed old payload B reloads as stored payload A in both contexts,
without any replacement setter. A setter on the newly decoded TValue then changes the
TermID in both parent and child projections. A subsequent setter on the old
displaced instance produces no canonical change or setter touch in either
context. All native mutations remain intact until explicit fixture restoration.

Six additional shadow JVMs test two closed boundaries under ON/OFF/VERIFY:

* A direct native storage update replaces a known record with another variable
  ID. `Base.get` still returns the native unit. The diagnostic session rejects
  the variable transition, and a subsequent disk re-decode confirms the native
  payload was not restored or compensated.
* A real local addition followed by native checkpoint release invalidates the
  lookup index. A direct native update/get returns a replacement, but the signal
  rejects registration under invalid metadata. Return value and disk payload
  survive, and the session closes.

Both failures are post-native qualification failures, not exceptions introduced
into the native storage API. The full snapshot algorithms remain checking-only
and never repair the shadow or register an unknown object.

## Evidence and remaining scope

The final matrix comprises six positive clean/shadow storage JVMs, all 56 prior
memory JVMs, three original negative JVMs without the new hook, and six new
closed-scope JVMs. All final exit codes are zero and stderr empty. Clean storage
controls pass 47 assertions; shadow storage controls pass 50. All six native
behavior rows agree. Independent committed replay validates 33 successful
traces and **9,216 authority views**. Failing qualification sessions are retained
as errors and are not counted as successful traces.

All 56 preceding memory native logs and 18 traces are byte-identical, including
owner/lazy-index controls, 19/83/22 metadata/dirty/persistence assertions, 27
required negative boundaries, four 47-source exact candidate oracles, full
20-operation transaction state/work/stdout comparisons, six-order publication
controls with 90 assertions each and native commit-exception gates. The two old
overlapping-preparation concurrent corpus failures remain unqualified.

Twelve preliminary positive JVMs are retained. The first variable-scope JVM passed;
the first index-scope fixture failed before materialization because a no-op
checkpoint correctly preserves the index when preservation is enabled. Both
are retained. The corrected fixture performs a real local addition before
rollback; only the six corrected scope JVMs enter the final matrix. The main
62-case matrix and original three negative cases were not rerun after this
scope-only fixture correction. Only the six positive JVMs were rerun for the
additional payload-reload witness; compiled hooks and scope runners stayed
hash-identical. One added clean fixture attempt failed at an expected-payload
assertion because its shadow-only restoration did not run. Its evidence is
retained; the corrected clean control explicitly restores its detached payload.

Caller-enforced quiescence remains mandatory. The ThreadLocal session does not
cover worker-thread loads. Unknown IDs, reset/generation transitions, invalid
indices, direct Data decoding without a subsequent qualifying Base lookup,
publication that changes lookup visibility, and later aliases outside the
canonical registry require separate contracts. In particular, registration is
not promised for a stored unit currently masked by memory that later becomes
visible without another qualifying lookup. The full reader still requires
complete residency and a current Sapato anchor.

This does not cover current/FValue/owner event channels or establish a complete
inference frontier. There is no consumer, performance claim, production default
change or develop merge. Next: publication and generation transitions that
change which already loaded object becomes canonical.

## Reproduction

Prepare the preceding parent/helper class directories with their committed
builders in isolated worktrees sharing `../build` and `../tooling/ecj.jar`:

```
python docs/tvalue-materialization-routing/build.py
python docs/tvalue-materialization-routing/run.py
python docs/tvalue-materialization-routing/analyze.py
```

ECJ 3.33.0 targets Java 8; runtime Java 17.0.20. The evidence manifest covers the
report, source, scripts, final outputs and retained preliminary attempts.
