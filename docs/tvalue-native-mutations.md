# Automatic native TValue mutation notifications

This isolated documentation experiment follows `8753b61d8429218d700ca32052336697d68e5a01` on exact SMART-era develop source `5d5f6aff271f1abf371fbde55d637744c53f0bc3`. It qualifies diagnostic callbacks on ordinary in-memory mutations, using the unchanged before/stream journals and full authority comparison at every observation. It does not change production code or defaults.

## Native boundaries

The positive driver calls native operations directly. It does not notify mutation, settlement, reset or retirement events manually. Both clean and instrumented runtimes are rebuilt from source; all 730 previously qualified clean class hashes must match. Only Mind, Mind$1, TValueFactory and TValue native class bytes may differ in the instrumented runtime. Reversing the recorded wrappers recovers every original source byte.

The fixture checks exact native enumeration order and object identity, direct canonical lookup, both pure authorities and fingerprint preservation across four variables and three generations. It covers term rewrite and duplicate setter calls; the native partial `setValue(null)` failure with its callback in `finally`; child deletion, duplicate deletion and canonical resurrection; root deletion and `pack()`; child/root `clear()`; fresh contexts after clear; new root values after descendants retire; provisional checkpoint rollback; and final reservation settlement.

`pack()` removes live parent enumeration routes even when a descendant's captured ID cache still returns the old canonical object. Clearing a parent likewise removes inherited enumeration routes from existing descendants. Updating only the cleared owner's journal misses these descendant changes. The added observer captures old resident values with a pure read before clear and dirties their variable buckets throughout active descendants after the unchanged native body, including failure exits. The full oracle verifies the result; it never repairs the projection.

## Evidence

The final matrix contains three selected variables × three optimization flag settings × two builds: 18 fresh JVMs. The analyzer independently replays all 18 old/stream traces and requires byte equality between journals and between flag settings for each selection. Clean and instrumented native observation rows must also match exactly. Each instrumented JVM deliberately suppresses one metadata event, one pack removal event and one clear descendant fanout: all 27 missing-event cases must be rejected by both journals with identical errors. The owning factory reset remains enabled in the clear negative control.

The independent replay checked 1,260 authority views and 10,116 serialized value entries; native controls checked 3,708 variable/context views including negative fixtures. Each positive journal trace contains 10 contexts, 70 views, 2 resets, 9 retired contexts, 10 deferred observations, 24 additions, 29 removals, 5 deletion transitions, 2 resurrections and 3 term rewrites. Reset baselines are recorded separately from patch transitions.

Exact counts and replay statistics are in [summary.json](tvalue-native-mutations/summary.json). Source/class hashes and source-only compiler commands are in [build-validation.json](tvalue-native-mutations/build-validation.json); [analyze.py](tvalue-native-mutations/analyze.py), [run.py](tvalue-native-mutations/run.py) and [build.py](tvalue-native-mutations/build.py) reproduce the checks.

The owner-only reset preliminary run is archived separately: native controls passed, but both journals detected descendant mismatches at child/root clear. These failed results are excluded from the final matrix. A separate archived fixture expectation failed when it assumed late additions after root clear would be invisible to old descendants. Native in-memory clear resets the ID counter, allowing reused IDs to resolve to previously captured objects in old descendants. This boundary is recorded, not qualified here: the final fixture retires old descendants before adding replacement values.

## Scope

Qualification applies to the legacy DUMB in-memory fixture on the exact new core. SMART backend persistence, arbitrary metadata identity changes, reused IDs with live descendants, concurrent scheduling, the complete inference corpus and an end-to-end speedup remain unqualified. The observer capture and fanout have diagnostic cost; this stage makes no performance claim. Earlier full-corpus limitations remain in force. No develop merge is performed.

The next scoped investigation should isolate ID reuse after clear with live descendants and establish native enumeration/cache behavior before proposing a journal contract for that boundary.
