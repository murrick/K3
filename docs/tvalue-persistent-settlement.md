# Composite persistent TValue settlement (3.8.0 diagnostic)

Parent: `c137746803bc94d0441bc97237301e463a5aa3e8`.

Actual composite Mind.commit over a resident DUMB root qualifies under a held
sibling reservation for acceptance, ordinary rejection, user-transaction
rejection and a controlled analyzer exception. Accepted child effects produce
one parent bucket delta; rolled-back effects produce none. The final release of
the held reservation is a separate boundary: accepted new TValue publication
completes natively, but the diagnostic session rejects **unresolved Base
endpoints**. The prior root-update hook already registers the stored replacement;
this new limit is persistent reader admission, not a missing materialization
signal or a native commit failure.

**24 fresh JVMs meet all expectations** under clean/shadow × ON/OFF/VERIFY for
four modes. Independent replay validates **33 successful traces / 150 authority
views**. Three rejected finalization prefixes/errors are retained separately.
No runtime hook, reader, production source, optimization default or develop
branch is changed; only a new qualification runner is compiled.

## Native fixture and two settlement boundaries

The fixture opens an actual DUMB DB with Data caching disabled. A compiled
universal owner Rule creates a real TVariable; its initial TValue is updated,
flushed and decoded through native storage. Root Rules are also stored, then
native RuleFactory.transaction(null) starts its new factory generation before
children are prepared. This leaves native root top unset; a subsequently
published sibling naturally establishes a different top and forces the
non-sequenced path for the original child. No top/bottom/index fields are edited.

A held sibling keeps root transactionCounter above zero while the tested child
settles. The child adds a second TValue. The accepted child introduces keep(a);
a divergent sibling supplies keep(a) for rejected/exception modes. These native
Rules retain the original term at final pack; keep(b) and the owner are rooted
before preparation. The fixture therefore checks surviving real bindings after
native cleanup rather than relying on orphaned dummy variables.

| Mode | Composite API and native outcome | Parent effect | Reservation behavior |
| --- | --- | --- | --- |
| accept | Mind.commit returns true | Second TValue visible, one delta | Only held sibling remains |
| reject | Contradictory stored operand; Mind.commit returns false | Original TValue only, zero delta | Rejected child consumed; held sibling remains |
| user-reject | Mind.commitUserTransaction returns false | Original TValue only, zero delta | Rejected child and held sibling remain; explicit child release consumes the former |
| exception | Non-sequenced analyzer throws the original controlled exception | Original TValue only, zero delta | Failed child consumed; held sibling remains |

The contradiction and analyzer exception exercise actual non-sequenced native
composite checkpoint creation, typed factory promotion and rollback. The
exception is test-controlled via an Analyzer subclass, with its original message
verified. It is not an arbitrary persistence failure or post-settlement
TransactionSettlementException qualification. No callback-purity assumption is
introduced. Native iteration, hydration, owner changes and native inference
side effects remain permitted. Diagnostic observations preserve owner/index
fingerprints; the unchanged reader still performs no native repair or I/O.

The child was prepared before the observed session. Parent and child receive
explicit baselines; settlement hooks retire the completed child. A user-rejected
child is observed while still live, then released explicitly. Finishing these
sessions succeeds, including their independent full-oracle comparisons.

## Quiescent finalization stays a closed boundary

A distinct session observes parent and held sibling before releasing the final
reservation. Native Mind.release consumes that reservation, invokes root pack
and root update, and returns normally with zero pending transactions.

For an accepted publication, update writes and decodes the second TValue.
The previous post-update bracket emits one materialize TOUCH in the parent.
The held sibling had no route to that second value, so it is not registered
there. Both Base rootId and topId admission data have been invalidated by the
native write and have not yet been resolved by a native endpoint lookup. The
full resident reader refuses this state at the settlement end and session end;
the retained error has exactly two unresolved-endpoint failures and no dirty
projection mismatch. The seven-row prefix is not counted as a successful trace.

Rejected/exception cases have no new surviving TValue to write. Their existing
persistent endpoints remain admissible and their finalization traces qualify
with zero changes. Native values are verified in all modes after explicit native
getRoot/forEach. A separate fresh session then seeds and closes normally. This
is an explicit new baseline after native endpoint resolution, not continuation
or repair of a failed incremental session. There is no complete database
close/reopen, crash-durability or storage-recovery claim.

## Evidence and preserved scope

Clean/shadow assertion counts are 15/18 for accept, 19/22 for reject, 21/24 for
user-reject and 19/22 for exception. Every final exit code is zero and stderr
empty. Native outcome rows agree across clean/shadow and all flag modes.
Successful settlement, finalization and fresh-session traces are byte-identical
across ON/OFF/VERIFY within each mode. The original committed replay algorithm
checks each successful authority view, checkpoint accounting, dirty selection,
retirement and canonical delta; it was not relaxed for this fixture.

All prior native classes, setter wrappers, Base wrapper, factory-update wrapper,
journal and resident reader overlays remain hash-identical. Their evidence
manifests are verified unchanged. The preceding 83-JVM/9,267-view evidence was
not rerun because this stage changes only its new runner. The two older
concurrent corpus failures remain unqualified; nothing broadens inference
frontier, concurrency or performance claims.

Fourteen preliminary JVM results are retained: twelve accepted controls passed,
and two rejection setups failed before the observed commit. The first attempted
to compile an already rooted keep(a), which native compileLine reports as a
duplicate. The second still had the old native RuleFactory.top and therefore did
not reach a non-sequenced branch. The final fixture moves keep(a) to the accepted
child or divergent sibling and uses the native stored-Rule generation transition
before children are created. Neither branch guard nor semantic oracle was waived.

Qualification is limited to this externally quiescent, fully resident DUMB
fixture, unchanged TValue identities/variable routes and the explicit settlement
sequence. Arbitrary raw aliases after rollback, concurrent preparation,
reentrancy, storage generation replacement, other failure sites and complete
quiescent persistent publication need separate contracts. In particular, a
promoted observer registration may conservatively remain after rollback; these
controls establish zero false canonical deltas, not heap-wide retirement of
all rolled-back aliases.

Next: read-only persistent endpoint qualification when a successful native
write invalidates endpoint admission metadata, while preserving the actual
native physical chain and leaving native caches/indices untouched.

## Reproduction

Prepare the previous committed class directories, then:

```
python docs/tvalue-persistent-settlement/build.py
python docs/tvalue-persistent-settlement/run.py
python docs/tvalue-persistent-settlement/analyze.py
```

ECJ 3.33.0 targets Java 8; runtime Java 17.0.20. The evidence manifest covers the
report, source/scripts, final results and both retained setup attempts.
