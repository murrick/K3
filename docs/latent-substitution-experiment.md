# Latent substitution index: first experiment

Date: 2026-09-15. Base: `develop/3.7.0` at
`3ad50f1e5253304f6b530de11c078f332ea4db89`.
Branch: `experiment/latent-substitution-index`. Post-3.7 experiment only.

## Outcome

The conservative topology pre-filter preserves the ordered candidate sequence
on the exercised corpus and removes substantial inner domain scanning on
`natives.k`. It does **not** remove unification attempts. End-to-end speedup is
not established. The experiment remains disabled by default.

This is a snapshot-build prototype, **not yet a compiler-owned index**.
It deliberately retains both Rule traversals, variable rotation, the existing
resolved candidate selector and all semantic kernels. No persistence format,
knowledge representation, public SDK contract or product version changes.

## Forensics: what the current engine actually does

1. `Linker.link` builds the active Rule set each saturation pass. A scoped
   invocation starts with the query/seed and expands via opposite native
   candidates, used rules and newly generated rules. A full invocation includes
   visible, non-deleted rules.
2. It traverses this set in descending and ascending **long Rule ID** order.
   These are propagation/scheduling passes, distinct from substitution direction.
3. `rotator` builds a predicate/polarity-to-Rule index, rotates current TValue
   bindings and processes each terminal branch.
4. `selectDomainCandidates` only accepts a singleton slave branch. It
   intersects that index with `RuleFactory.findByResolvedDomain`.
5. `linkDomains` visits every branch/domain of each selected master Rule,
   admitting a domain when predicate IDs match and polarities differ.
6. Within each admitted pair, it tries substitution into the master and into
   the slave. `master/slave` are traversal roles, **not** synonyms for
   antecedent/succedent. The slave itself may have either polarity.
7. Existing TValue/FValue checkpoints, CVar child creation, used/excluded marks,
   causes and deferred TSolve contributions remain part of the same operation.
8. `linkDatabase`, function/system evaluation and deferred `updateDatabase`
   determine hypotheses, produced rules and continuation of saturation.

`Domain.isAntc() == false` identifies a succedent occurrence, `true` an
antecedent occurrence. `Domain.isSubstitutable()` records the presence of
t-variables; it does not mean the other endpoint of a useful pair must also
contain t-variables. Ground occurrences must remain in the graph: they supply
values and can produce used-state effects without new TValue objects.

### Static versus dynamic boundary

| Check / effect | Owner | Classification / treatment |
|---|---|---|
| Predicate ID, opposite polarity | Linker | Stable structural gate, indexed here |
| Predicate/polarity/arity | RuleCandidateIndex | Existing signature index; arity is already enforced before Linker |
| Singleton slave branch | Linker | Structural eligibility; retained unchanged |
| Rule/branch/occurrence order and multiplicity | Linker | Preserved exactly; no Set deduplication of occurrences |
| Direct TERM positions in primary, non-query singleton Rules | RuleCandidateIndex | Already indexed; generated/query/multi-domain Rules deliberately fall back |
| Resolved TValue IDs and source bindings | RuleCandidateIndex | Runtime context; keep existing selector |
| Generated, non-substitutable pair batching | RuleCandidateIndex | **Semantic side effects**, including marking source/candidate used |
| `blockRight` / `blockLeft` | Linker | Direction-specific, binding-dependent; not a generic pair rejection |
| TValue/FValue, CVar children, used/excluded, Cause/TSolve | Linker and factories | Runtime semantics; untouched |
| `isValidFor`, function/system checks, collisions, hypotheses | Existing kernels | Runtime checks; untouched |
| Deletion, transaction visibility, generated/query lifetime | Mind / factories | Context-sensitive; not persistent compile metadata |

Two particularly important observations from executable code:

* `findByResolvedDomain` is **not a pure lookup**: generated non-substitutable
  batching marks used state and removes those candidates from the returned set.
  Bypassing it would omit behavior even with an otherwise complete adjacency.
* `success` in the current `linkDomains` body is not set false by constant
  mismatch. Direction blockers do not suppress the entire pair; non-substitutable
  pairs and Rule used marks still have effects. The `result` used for checkpoint
  handling is accumulated across the call. This experiment does not change or
  reinterpret that historical behavior.

Consequently, a stricter constant-based "compatible pair" relation has **not**
been proven safe. The measured P is the permissive structural relation, not the
number of successful substitutions or a minimal semantic graph.

## Topology measurements

Snapshots are taken **after successful compilation and saturation**, including
generated knowledge, not just the text's original rules. Domain IDs are unique
within a snapshot. R counts visible rules; S/A count all distinct succedent /
antecedent domains. St/At additionally count those containing t-variables.
`C = S*A`; P counts pairs sharing a predicate. Polarity follows the S/A partition.
Percentiles use nearest rank and include zero-degree succedent domains.

| Fixture | R | S | A | P / C | Density | avg | p50 | p95 | p99 | max |
|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|
| empty | 0 | 0 | 0 | 0 / 0 | n/a | 0 | 0 | 0 | 0 | 0 |
| one | 3 | 1 | 3 | 1 / 3 | 33.33% | 1 | 1 | 1 | 1 | 1 |
| dense-100 | 10,200 | 100 | 10,200 | 10,000 / 1,020,000 | 0.98% | 100 | 100 | 100 | 100 | 100 |
| sparse-100 | 300 | 100 | 300 | 100 / 30,000 | 0.33% | 1 | 1 | 1 | 1 | 1 |
| symmetric-chain | 9 | 3 | 9 | 9 / 27 | 33.33% | 3 | 3 | 3 | 3 | 3 |
| natives.k | 128 | 116 | 43 | 492 / 4,988 | 9.86% | 4.241 | 3 | 8 | 8 | 8 |

For natives: St=24, At=21; 290 P pairs have at least one t-containing endpoint;
439 have at least one endpoint occurring in a singleton branch. These are
structural upper bounds, not observed runtime candidate counts.

The supplied `labyrinth.k` is rejected by baseline compilation in this isolated
environment and is excluded. The runner emits `REJECTED_SOURCE`; it does not
silently treat rejection as an empty successful fixture. This is not classified
or fixed as a product defect here. The synthetic symmetry/chain case covers a
small recursive graph, not a representative large labyrinth workload.

### Memory and build cost

Measured with `Instrumentation.getObjectSize`: retained size of the explicit
`int[][]` adjacency and its owned primitive rows only. There are no references
from those rows to knowledge objects. This excludes the domain-ID dictionary,
temporary construction buckets, reverse index and the runtime prototype's maps.
It is **not** a measurement of total Mind heap or the runtime prototype's retained
heap. CSR estimate is `4*(S+1+P)` payload bytes, excluding object headers and IDs.

| Fixture | Snapshot index build | Owned adjacency bytes | CSR payload bytes |
|---|---:|---:|---:|
| dense-100 | 2.404 ms | 42,016 | 40,404 |
| sparse-100 | 0.049 ms | 2,816 | 804 |
| natives.k | 0.027 ms | 4,616 | 2,436 |

These are single-run observations, not warmed benchmark estimates. Build timing
excludes snapshot collection, independent exhaustive validation and compilation.
Zero candidate-space density is emitted as 0 in CSV by convention; mathematically
0/0 is undefined. Full raw measurements accompany this report.

All S domains sharing a predicate currently have identical adjacency. Explicit
per-domain arrays duplicate shared buckets. Low global density alone therefore
does not justify a fully expanded pair graph, especially after saturation.

## Minimal runtime prototype

Internal JVM switch `kanger.experiment.latent`:

* `off` (default): reference traversal.
* `index`: per-rotator immutable-use snapshot of ordered Domain occurrences,
  indexed by Rule identity and predicate/polarity.
* `verify`: same indexed execution, with exhaustive reference filtering before
  each candidate Rule is processed. Identity, order and multiplicity must match;
  an AssertionError aborts on mismatch (not swallowed by the Exception handler).

The pre-filter is applied **after** the existing resolved Rule selection.
Only unrelated Domain occurrences are skipped; the old admission condition and
entire accepted-pair body are retained. Empty buckets are valid. Each direction
rebuilds its snapshot, so generated rules appearing between passes are picked up
through the original active-set protocol. No index survives a rotator invocation.

### Evidence

* Five existing core corpora: 102 completed + 7 stabilization + 5 promotion +
  3 interval + 6 binding = **123 passing in both off and verify**, zero failures.
* Six semantic fixtures, 16 operations: independent JVM runs in off/index/verify
  produce byte-identical sorted projections of final result, visible rules with
  generated flag, hypotheses, Solutions and Values, plus unification/TValue and
  Cause/TSolve/generated-rule contribution counts.
* Fixtures include missing candidates, single/multiple values, symmetry, a
  recursive rule, functions, unknown queries, accepted facts and rejected
  conflicting additions. These comparisons do not serialize every internal
  TValue or collision witness; they are not a universal equivalence proof.
* Existing Linker long-ID ordering and checkpoint-balance runners pass in verify.
* Existing 30/100-row Linker profile: all counters except permissible domain-pair
  reduction match; these singleton workloads have **no domain-pair reduction**.
* `tools/qualify-latent-substitution.sh` reproduces the corpus/state/profile
  comparison and fails on differing semantic projections or profile counters.

Representative first run (elapsed observations are not statistically qualified):

| Query | Old domain visits | Indexed visits | Unification attempts, both | Old / index elapsed |
|---|---:|---:|---:|---:|
| natives `?$x male(x);` | 14,282 | 3,251 | 3,251 | 271 / 209 ms |
| natives `?male(Tom);` | 9,072 | 2,015 | 2,015 | 117 / 129 ms |
| chain `?$x $y path(x,y);` | 108 | 71 | 71 | 17.4 / 17.3 ms |

Elapsed includes query plus snapshot formatting/telemetry in this diagnostic
runner. It must not be advertised as isolated Linker time or a speedup factor.

Environment: OpenJDK 17.0.20, Linux; ECJ 3.33.0 targeting Java 8 bytecode.
This workspace had a JRE but no javac/Maven. Production sources and the selected
qualification sources compiled using ECJ and the repository's JLine dependency.
**Full Maven reactor and canonical Java 8/21 qualification were not run locally.**
No successful canonical CI result for the new indexed mode is claimed.

## Reproduce with the normal Maven toolchain

From the repository root (JDK and Maven installed):

```sh
mvn --batch-mode --no-transfer-progress -pl kanger-qualification -am install
mvn -f kanger-qualification/pom.xml dependency:build-classpath \
  -Dmdep.outputFile=target/latent-classpath.txt
export LATENT_CLASSPATH="$PWD/kanger-qualification/target/test-classes:$(cat kanger-qualification/target/latent-classpath.txt)"
bash tools/qualify-latent-substitution.sh
java -Xmx1g -cp "$LATENT_CLASSPATH" org.kanger.LatentSubstitutionTopologyRunner natives.k labyrinth.k
```

For exact owned adjacency sizes, package the topology runner as a tiny agent:

```sh
printf 'Premain-Class: org.kanger.LatentSubstitutionTopologyRunner\n\n' > target/latent-agent.mf
jar cfm target/latent-agent.jar target/latent-agent.mf \
  -C kanger-qualification/target/test-classes org/kanger/LatentSubstitutionTopologyRunner.class
java -Xmx1g -javaagent:target/latent-agent.jar -cp "$LATENT_CLASSPATH" \
  org.kanger.LatentSubstitutionTopologyRunner natives.k labyrinth.k
```

Without the agent `adjacency_retained_bytes=-1` explicitly means unmeasured.

## Decision and remaining architectural step

GO for further investigation of **lifetime and ownership**, not removal of
bidirectional mechanics. The structural pre-filter has a concrete benefit on
multi-domain Rules, but a compiler-only graph would omit ephemeral queries and
newly produced knowledge. A subsequent implementation must define incremental
extension, rollback/commit, deletion, hydration/reopen, and storage generation
invalidation without retaining discarded Mind graphs.

Prefer reusing Rule/Domain factory lifecycle and existing shared signature
buckets over adding a new manager. Preserve the resolved selector's side effects.
Measure allocation and end-to-end cost of that persistent-in-memory derived
topology before deciding whether it is worth keeping. A stricter structural
relation needs a separate proof against all existing used-state effects.

No develop update, merge, release, tag, deployment or old-Linker removal is part
of this experiment.
