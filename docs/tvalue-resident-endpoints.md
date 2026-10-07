# Resident endpoint inference: topology alone is insufficient

Parent: `c4c42b94626549bfcdce7254e77df9a7863b244d`.

## Result

Do not promote the proposed resident topology inference into `ResidentPersistentRead` or the dirty journal. Six fresh Java 17 JVMs targeting Java 8 (clean/shadow × flags ON/OFF/VERIFY) reproduce a valid, complete resident chain whose inferred endpoints disagree with native disk-based endpoint resolution. Each run passes 51 assertions; stdout is identical and stderr empty. This is an intentionally demonstrated inadequacy of the candidate, not a failed run or an oracle waiver.

| Observation | Pristine resident chain | After unsaved native link setters | Explicit disk control |
| --- | --- | --- | --- |
| Ordered IDs | 91, 7, 40 | 7, 40, 91 | 91, 7, 40 |
| Root / tail | 91 / 40 | 7 / 91 | 91 / 40 |
| Manifest ID universe | 40, 7, 91 | 40, 7, 91 | 40, 7, 91 |
| Manifest lengths and CRCs | Initial entries | Unchanged | Unchanged |

## Source finding and fixture

`kanger-data-dumb/src/org/kanger/storage/IntegrityManifest.java`, private `Entry`, contains only `length` and `crc32`. It carries no separately inspectable link identity. `Base.resolveEndpoints()` enumerates the actual index and decodes stored records with `Data.getUncached()`, then derives endpoints from those stored links. `Base.add()` invalidates endpoints and its cached record. `kanger/src/org/kanger/storage/Sapato.java` exposes `setNext(IStep)`, which changes resident `next` without calling `update()` or `append()`.

The fixture uses real native User/Mind/Base/Sapato/TValue instances. It writes IDs 40, 7, 91 through native storage, flushes, and warms the whole cache with direct native `Base.get` calls while both native endpoint fields remain null. A read-only candidate copies cache entries through iteration, checks exact step/unit classes and base/ID identities, uses the manifest ID universe, rejects dangling links and cycles, requires exactly one root and tail, and traverses the entire universe. It accepts the pristine order.

The fixture then calls only public native `setNext` methods to form 7 → 40 → 91. This is a controlled, quiescent unsaved resident mutation, not a claim that the preceding composite finalization produces that mutation. No reflected field writes or custom units are used. The same candidate accepts this equally complete chain. All manifest lengths/CRCs are unchanged. Full native storage/owner/resident fingerprints are identical before and after each candidate observation; the candidate does not call getters, pack, hydration, endpoint repair, or storage reads. Assertions are fixture checks rather than journal admissions.

The existing reader rejects both unresolved-endpoint states with its original error and an unchanged fingerprint. Only after these checks does the fixture explicitly invoke native `getRoot()`/`getTop()`: their endpoint fields resolve to stored 91/40. Closing/reopening and native traversal then confirm the original stored chain 91 → 7 → 40. The unsaved link edits did not change stored records.

## Consequence and limits

A complete integrity ID universe plus a valid resident topology does not prove equivalence to stored links. Quiescence prevents concurrent changes during observation; it does not establish that pre-existing resident links were saved. The manifest CRC may support a separate byte-equivalence check, but this candidate neither reconstructs the serialized record nor verifies that CRC. CRC is not itself an endpoint map or a collision-free provenance proof.

The final accepted composite release from the parent stage therefore remains unqualified under the existing strict reader. Its recorded `unresolved endpoints` failure is retained. No replacement reader, journal hook, factory wrapper, production source, default flag, or develop branch changed. Prior failed concurrent corpora remain unqualified; their evidence is untouched. The parent 24-JVM/150-view matrix and older regression matrices were not rerun because their runtime implementation is unchanged. Prior reader and all 696 clean/shadow native class hashes are verified by the new run script.

A next concrete design must either establish saved-link provenance at the native write/decode boundary or separately validate the resident serialization against the saved representation without owner changes or callbacks. Neither design is qualified by this experiment. Reopen, arbitrary corruption/fault injection, concurrency, and reentrancy remain outside admission scope.

## Reproduction and evidence

Run from the worktree root: `python docs/tvalue-resident-endpoints/run.py`. Requires the frozen prior class directories under `../build`, Java, and `../tooling/ecj.jar` (ECJ 3.33.0).

`docs/tvalue-resident-endpoints/` contains the candidate/fixture source, build/run command, six complete stdout/stderr files, six JVM result records, summary with class hashes, and a SHA-256 artifact manifest. No candidate failure was relabeled as a positive journal trace; no journal trace is emitted or claimed by this stage.
