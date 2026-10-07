# Native TValue storage transition boundary (3.8.0 diagnostic)

Parent: `8ca65c10e6d1cbe3a1cb4f3d46140ea65fe4b187`.

The existing post-Base.get signal does **not qualify an entire native
TValueFactory.update transition**. Nine shadow controls reject transient lookup
metadata during that operation. Native update completes and its replacement
objects and payloads survive. Subsequent generation-reset and typed-factory
publication controls qualify in six explicitly new sessions. Rejected update
sessions remain separate evidence; a fresh session does not erase their failure.

## Native transition and observed boundary

Escalera.update walks pending memory Steps oldest first. For each Step it appends
a Sapato, calls native Base.get, obtains the decoded unit and assigns a new root.
Only after the loop does it synchronize indexedRoot with root. During that loop,
memoryById removal, persistentIds addition and root replacement occur in stages.
The diagnostic materialized guard checks both indexValid and indexedRoot identity
before considering a memory entry that might mask the returned stored object.
An intermediate mismatch therefore records a qualification error even though
native update proceeds successfully. This is a journal error, not a native API
exception or compensation. This stage adds no hook and changes no guard.

The initial hypothesis was that memory masking could hide a replacement until
a later setter. Actual evidence encounters the earlier invalid-metadata guard.
The isolated missing-setter hypothesis is **not proven** and must not be inferred
from these runs. A blanket suppression of guard errors, an oracle reseed inside
the failed session, or registration copied from the full oracle is not justified.

The fixture creates an actual DUMB DB with Data caching disabled and three native
TValues. The middle ID is the witness. Native factory update redecodes that ID
as a different TValue object and removes its memory lookup entry. An explicit
native Base.getRoot resolves persistent endpoints by looking up the newest ID;
the middle witness is selected by raw resident-cache iteration, not another get.
Read-only observations preserve owner/index fingerprints and Base read counters.
All native reservations finish at zero. A sibling reservation keeps fixture
bindings alive until cleanup; final native pack may remove them. This remains
synthetic diagnostic storage, not a full semantic database reopen qualification.

| Mode | Native operation | Diagnostic outcome |
| --- | --- | --- |
| update | Memory-to-storage factory update | Initial shadow session rejects intermediate metadata; native result retained |
| generation | New session, root factory transaction(null), explicit native forEach | RESET generation 1 reseeds canonical registration; later setter tracked |
| publication | New session, child addition, parent factory commit(childFactory) | Existing promote hook records four IDs in parent and child; later setter tracked |

Each mode runs clean/shadow under all flags ON/OFF/VERIFY: **18 fresh JVMs**.
All exit codes are zero and stderr empty. Native behavior rows are identical
between clean/shadow and flag modes. Clean checks are 12/14/15 for
update/generation/publication; shadow checks are 13/17/18 respectively. Each of
the nine shadow initial sessions retains exactly two invalid-metadata errors.
The six positive new-session traces replay independently against every one of
**33 authority views**, using the previously committed parser/replay algorithm
with the already qualified TermID-change extension. Generation traces contain
one RESET and one payload delta. Publication traces contain eight promote
TOUCH records and payload changes in both parent and child. Traces are identical
across ON/OFF/VERIFY within each mode.

## Scope and preserved failures

Only a new qualification runner is compiled. All previous native classes,
setter wrappers, Base wrapper, journal methods and reader classes are unchanged.
The preceding 71-JVM/9,216-view evidence manifest is verified byte-for-byte;
those runs were not repeated. The two older overlapping-preparation concurrent
corpus failures remain unqualified. There is no performance or inference
frontier completeness claim, production change, default change or develop merge.

The publication mode invokes typed TValueFactory.commit directly. It does not
qualify composite Mind.commit, its rejected/exception paths, durable publication
or concurrent generations. Both positive modes start from a new session baseline;
they do not show that an already rejected update session can be resumed. Native
iteration may hydrate owners and perform Base gets; that is explicit native
fixture work, not a claim of pure publication. Only diagnostic observations are
read-only. Caller-enforced quiescence and a resident current storage remain
mandatory.

Two preliminary fixtures are retained. The single-record fixture failed before
a setter because native persistent endpoints were unresolved. The next fixture
passed three clean JVMs, then failed its anticipated dirty-mismatch-only
assertion: the actual error was the earlier transient-metadata rejection.
The final fixture retains that actual rejection explicitly and closes it before
starting any generation/publication session. No oracle mismatch was waived.

Next: a post-successful factory-update boundary, after native lookup metadata
has settled, with separate contracts for already known IDs, object replacement,
retired identities, partially failed updates and descendant contexts.

## Reproduction

Prepare the preceding committed class directories using their builders, then:

```
python docs/tvalue-storage-transition/build.py
python docs/tvalue-storage-transition/run.py
python docs/tvalue-storage-transition/analyze.py
```

ECJ 3.33.0 targets Java 8, runtime Java 17.0.20. The evidence manifest covers the
report, runner, scripts, final outputs and both retained preliminary attempts.
