# Repeated factory occurrence materialization: observation

2026-09-16. No candidate selection or reuse optimization introduced. Optional diagnostic `kanger.experiment.profileOccurrences=true` observes the existing factory path.

## Remaining runtime work

RuleFactory.getLatentDomainCandidates borrows an immutable ordered long[] row, allocates a fresh ArrayList, and resolves every ID through the current Mind's DomainFactory. Linker wraps that result as one branch, then performs existing semantic checks. This removes the full Rule-tree scan but still repeats materialization of the same selected row during variable rotation.

DomainFactory.get is not merely an array read: Escalera lookup ensures its ID index, follows the current cache/storage context and calls step.getData(mind); storage fallback can hydrate a Domain. DomainFactory transaction/commit/clear/release affect that context. A global Domain-reference cache would need lifecycle and ownership reasoning absent from the immutable ID-only index.

Crucially, both Step.getData(mind) and Sapato.getData(mind) call IUnit.setMind(mind) even for an already materialized object. Domain.setMind rebinds its context and its TVariables. Thus identical references do not prove that repeated factory lookups are semantically removable: their context-rebinding effect must be preserved or independently proven unnecessary. This is an additional gate before any actual-skip prototype.

The other major candidate boundary, findByResolvedDomain, has runtime binding checks and used/batching effects. Neither this observation nor row reuse permits skipping it, changing Rule order, sharing pair results, or removing either traversal direction.

## Observation scope

For each rotator, an identity-keyed map records the most recently materialized list per Rule object and predicate/opposite polarity. Every lookup still calls the existing factory method; repeated lists are compared by length and per-position object identity. Duplicates and order matter. The map dies with the rotator and is never consulted to select candidates. Counters reset per Linker invocation and are emitted in finally with a completion flag.

`counts=[calls, hydrated slots, repeated calls, repeated slots, changed ordered identities]`.

All runs use factory-verify, so every selected occurrence list is also checked against the original exhaustive Rule-tree traversal. No other optimization flags are enabled. Numbers aggregate compilation and query invocations, not just query work. Profiling retains extra references and allocates maps, so these runs provide counts, not performance estimates.

| Run | Completed invocations | Calls | Domain slots | Repeated calls | Repeated slots | Changed lists |
| --- | ---: | ---: | ---: | ---: | ---: | ---: |
| 123-case corpus | 1,246 | 381,995 | 437,729 | 303,132 | 344,680 | 0 |
| 16-operation state scenario | 23 | 9,455 | 10,851 | 7,362 | 8,382 | 0 |
| 20-operation nested transactions | 39 | 2,686 | 2,686 | 1,954 | 1,954 | 0 |
| 13-stage lifecycle | 16 | 72 | 72 | 16 | 16 | 0 |

About 78.7% of corpus Domain-resolution slots repeat within a rotator. They are **potentially** avoidable materializations, not proven redundant inference steps or a predicted speedup. Zero observed identity changes is necessary evidence, not a general guarantee across arbitrary extensions, subclass behavior or untested lifecycle paths.

Corpus and lifecycle completion markers passed. State stdout and nested-transaction direct state output are byte-identical to their old-path baselines. No incomplete invocation was found in emitted observations. Evidence: [occurrence reuse](latent-substitution-evidence/occurrence-reuse/).

## Next bounded prototype

First audit context rebinding and observe the pre-lookup Mind of each repeated Domain/TVariable. Only then consider a default-off, per-rotator borrowed-list memo using the same Rule identity and signature key, preserving required rebinding. Keep the factory's persistent derived representation ID-only. A verification mode must still resolve every repeated request and assert identical ordered references before using a memoized list; identity equality alone is not sufficient verification of skipped side effects. Check storage hydration, nested lifecycle, Rule publication and collision paths before allowing actual skipped lookups. Do not lazily resolve IDs inside pair checkpoints: that changes hydration timing relative to the current eager-list path.

Even after equivalence checks, separately measure end-to-end performance and temporary reference retention. This checkpoint only identifies and quantifies the next candidate optimization; it does not implement it or remove old mechanics.
