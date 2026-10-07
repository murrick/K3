# Resident persistent TValue metadata boundary (3.8.0 diagnostic)

Parent: `c5551eae10ded5a975043e829360c32daf623881`.

A complete physical chain of already decoded, native DUMB `Sapato` records can
be read without calling `IBase.get`, `Sapato.getNext`, `getData(Mind)` or any
TValue hydration method. The isolated helper returns immutable metadata rows
`TValueID:TVariableID:TermID:MindID` in physical newest-first chain order.
It does **not** implement context deletion, variable bucket order, factory
lookup, dirty events, inference filtering or persistent journal admission.
The previous journal still rejects every connected factory and every Sapato.

## Admission and limits

The caller must exclude concurrent storage operations **and** resident unit
mutation for the entire read. The helper cannot establish this precondition.
Although it takes the real Base locker and cache monitor, native Base writes
invalidate cached records after leaving the locker. The locks alone therefore
do not prove a coherent concurrent snapshot. No concurrent run is qualified.

The candidate must be exactly native `Base`, with native open `Data` and native
integrity metadata. Nonempty bases need enabled caching, resolved endpoints,
and every physical chain record already resident in the actual Base cache.
The helper copies cache entries by iteration; it never calls the access-ordered
map's `get`. It resolves physical next IDs in that temporary copy, validates
exact Sapato/TValue classes, base identity, cache/step/unit IDs, cycles and the
tail, and requires the visited ID set to equal the full in-memory integrity
universe. No missing records are fetched and no endpoints or indices repaired.
The native initialized empty base returns an immutable empty result.

The result contains scalar metadata only, including stored MindID; this is
distinct from the mutable attached Mind pointer. No resident objects escape in
the result. ID metadata does not establish deletion visibility or native
factory lookup equivalence. `Escalera` can retain persistent IDs separately from
the physical chain, and its native lookup delegates to storage. Integration
requires a separate proof of that lookup and its active storage identity.

## Evidence

Six fresh JVMs cover clean and preceding shadow builds with all eight flags ON,
OFF and VERIFY. Each passes 40 assertions, including 15 required refusals.
All six native stdout files are byte-identical; stderr is empty. The helper is
compiled for Java 8 with ECJ 3.33.0 and run under Java 17.0.20. Every class in
each preceding build is checked against its committed SHA-256 manifest; no
native source or existing compiled class is modified. The shadow run uses the
preceding hooks but starts no diagnostic journal session.

The fixture writes real TValue packets through Base/Data/integrity/WAL, flushes,
closes and reopens actual files. IDs are deliberately allocated in chain order
`40,7,91`; physical traversal yields `91,7,40`, while the warmed cache has LRU
order `7,91,40`. The resident result matches a separate actual native
`getRoot`/`getNext` traversal exactly. That native traversal changes cache
counters/order; the helper preserves them.

Successful and rejected reads preserve the fixture fingerprint: Base endpoints,
cache order/entry identity, cache size/hits/misses/evictions, seven Base read/write/
delete/flush telemetry fields, Sapato IDs/links/base/data identities, TValue
scalar IDs and owner/term/variable pointer identities, plus Data read/write
counters, cache size/hits/misses/order and current record identity. Identity
hashes are within-JVM comparisons, not a complete heap-purity proof. Disk I/O
absence follows from the inspected helper call graph; no OS I/O tracer is used.

The decoded term and variable pointers remain null. An attached child owner
survives the resident read. A separate native `getData(owner)` control still
rebinds it, showing that the fixture can expose the earlier owner problem.

| Required refusal | Preserved native state |
| --- | --- |
| Endpoints invalidated by writes | Endpoints remain unresolved |
| Cold reopened cache; missing oldest link; native cache clear | No loading or cache repair |
| Null unit; custom TValue; custom Sapato | No extension callback |
| Foreign Base pointer | No cross-base resolution |
| Cycle; step/unit ID alias; wrong tail | No chain repair |
| Integrity-universe mismatch | No acceptance of partial physical projection |
| Closed Base; disabled cache | No reopening or cache enabling |
| Proxy storage implementation | Rejected before any proxy method |

The initial compilation used an incorrect IBase import and was rejected by ECJ
before any JVM ran. Its source and explanation are retained in `setup-attempts`.
After correcting the import, the six-case matrix ran once without failures.

## Reproduction and status

Prepare the parent stage's clean/shadow class directories using its committed
`build.py` in an isolated parent worktree with the shared `../build` directory
and `../tooling/ecj.jar`. Then, from this worktree, run:

```
python docs/tvalue-resident-persistent/run.py
```

The script verifies prior class hashes, compiles only the new helpers into a
separate directory and writes all six native logs/result manifests and summary.
`evidence-sha256.json` covers the report and stage files except itself.

No production code, defaults, develop branch, existing journal instrumentation,
or consumer changes. The previously rejected overlapping-preparation concurrent
corpus remains unqualified. This result establishes a bounded physical metadata
reader, not persistent dirty-journal coverage or an optimization speedup.
