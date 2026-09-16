# Membership filtering experiment

Parent `91d34bd`. Enable `kanger.experiment.membershipFilter=true` to replace
temporary exact/wildcard/fallback unions in resolved selection with direct
membership tests. Default remains the reference copy/union/retainAll path.

## Equivalence and ownership

For each resolved argument position the reference keeps
`selected intersect (exact union wildcard union fallback)`.
The experiment removes an ID only when it belongs to none of the three sets.
Null resolved positions remain unrestricted. Positions are visited in the
same order; both implementations stop when selected becomes empty. Iterator
removal preserves the copied signature set's insertion order.

The one selected-signature copy remains. Fallback/exact/wildcard sets are
borrowed only inside the existing candidate-index write lock. They are never
modified or retained outside that scope. No new cache or ownership exists.
The change avoids their temporary copies and unions, but still allocates keys
and iterators; it is not allocation-free. No heap/allocation volume was measured.

Resolution still precedes filtering in each factory's own Mind. Parent-first
recursion, empty-signature handling, summary lookup, batching, used effects,
final materialization and lock boundaries are unchanged. The ordinary
non-resolved collectLocal method is unchanged.

In factory-verify, each nonempty signature snapshot is filtered by both methods
under the same lock using the same resolved arguments. Ordered ID lists must
match exactly before any batching occurs. The verifier does not repeat
side-effecting lookup or batching. The old filter is retained as reference.

## Qualification

With factory-verify, factoryCandidates, indexedIntersection and membershipFilter:

- all 123 corpus cases pass;
- lifecycle qualification passes, including storage reopen and replacement generation;
- all 16 state operations match existing reference stdout byte-for-byte.

This is evidence for the corpus, not a universal proof of inference semantics.
The local set transformation also has the equivalence argument above. Toolchain:
OpenJDK 17.0.20, ECJ 3.33 Java 8 target. Full Maven and canonical Java 8/21
qualification were not run.

## Measurements and decision

Factory mode with factoryCandidates and indexedIntersection on; separate JVMs,
three warmups then seven measured fresh-Mind samples. Qualification completed
before timing. Median milliseconds with all phase timers off:

| Fixture | Reference compile | Membership compile | Reference query | Membership query |
|---|---:|---:|---:|---:|
| natives | 89.680 | 103.486 | 111.070 | 119.909 |
| 100 facts | 14.697 | 14.334 | 22.693 | 20.864 |

Separate profiled JVMs with timeStages/profileResolved on, invocation tracing off:

| Fixture | Reference filter | Membership filter | Reference lookup | Membership lookup | Reference whole query | Membership whole query |
|---|---:|---:|---:|---:|---:|---:|
| natives | 1.985 | 1.543 | 5.540 | 5.340 | 112.268 | 119.512 |
| 100 facts | 2.908 | 2.940 | 4.208 | 4.883 | 22.211 | 33.517 |

Filter/lookup columns cover the exported CHECKTRUE snapshot, while query time
includes CHECKFALSE and other query work. The timer-enabled facts run varies
substantially; these separate short JVM runs do not isolate instrumentation
cost or establish stable speed ratios. Rows, domain-pair and unification counts
match every paired sample. Profiled runs additionally match lookup calls,
layers, empty signatures, selected IDs, batch hits/misses and returned IDs.

There is no general speedup. The experiment removes some temporary sets but
must scan selected IDs with up to three membership probes; fewer collections
alone do not imply less runtime. Keep it disabled by default, retain the
reference, and do not promote based on the improved unprofiled facts sample.

Next investigation should return to unattributed work inside Linker (rule
preparation and variable rotation), with both query-check invocations covered.
The original derived-topology goal remains; this result does not justify
removing traversal or introducing more caches.

Raw evidence is in `latent-substitution-evidence/membership-filter/`.
Benchmark filenames encode `profile-enabled` then `membership-enabled`.
Live develop was rechecked at `3ad50f1e5253304f6b530de11c078f332ea4db89`.
No develop changes, merge, release, tag or deploy.
