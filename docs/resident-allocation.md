# Allocation prototypes with resident comparison enabled

Base: live develop/3.8.0 verified at 3a2a6f0bccac11ac0d646178c4384ba73d7a5260.
Branch: experiment/3.8.0-resident-allocation.
All seven integrated optimizations are enabled by default. The two additional
prototype properties remain default OFF:
`-Dkanger.experiment.directPredicateName=true`
`-Dkanger.experiment.compactFindSnapshots=true`
Restart the JVM when changing either property.

The recent warm profile attributes about 22–23% of allocation sample weight to
Predicate.getName and Escalera.find. That is an attribution lead, not a claimed
time or allocation gain. The prior combined-allocation branch used the old
six-flag baseline. Only its four-file minimal production slice is carried here;
no old experimental branch is merged wholesale.

## Preserved boundaries

Predicate.getName resolves the name donor and its value on every call. Only an
already String value is returned directly. All other values retain the original
`value + ""` conversion, including null and custom toString behavior. No name
cache, value identity or persistence semantics is added.

Escalera.findCandidates is an internal iteration snapshot for three factory
loops. Exact Escalera uses empty/singleton immutable snapshots for cardinality
zero/one; larger sets retain the same HashSet copy and order as reference find.
Custom cache subclasses retain their find callback. Public find remains a fresh
mutable caller-owned set; no internal live collection escapes.

The seven integrated defaults, guards and fallbacks are unchanged. Existing
resident comparison still falls back for different CVar IDs and custom/context
cases, including parent key mutation/failure callbacks.

## Qualification and measurement

Local Java17 checks all four combinations 00/10/01/11 with the seven integrated
flags ON: 24 name boundaries, 23 snapshot boundaries, 48 resident boundaries,
15 single-lookup boundaries, latent corpus, DB reopen/hydration/rollback/collision,
20 transaction operations and byte-equal saved states. Dedicated CI runs these
combinations on Java 8/21/26 with all seven prior flags explicitly OFF/ON, plus
cross-context projection and candidate concurrency in combined mode.

Timing uses ten sequential fresh JVMs: clean pinned reference, 00/10/01/11,
11/01/10/00, clean reference. Six samples each, discard first two, compare
last-four medians; seven integrated flags ON, 512 MB heap, Java17.0.20. No JFR,
counter patch, verify or shadow options run during timing. Compare complete raw
and final hypothesis texts and logical result/solution/value counts.
Clean reference classes and the identical SonProfileRunner come from the enabled
defaults qualification build. Sources and input are the pinned base; no new
optimization code is present in that control. Allocation is measured as actual
main-thread allocated bytes, not retained heap. Do not add these gains to earlier
experiments or assume the same effect on other workloads.

Results will be recorded after qualification and measurements complete.
No develop change, merge or default enable of these two prototypes in this checkpoint.
