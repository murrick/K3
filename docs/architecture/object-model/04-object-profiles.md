# KANGER Object Profiles

## Status

Normative object-profile section of the `3.6.0` KANGER Architectural Object Model.

Baseline:

```text
develop/3.5.0.7
```

Working branch:

```text
arch/3.6.0-object-model
```

Profiles in this document follow the approved Object Meta Model. Each profile describes one architectural entity independently of Java implementation detail. Implementation names are used only where they are necessary to identify the production realization of the entity.

---

# 1. Mind

## Name

```text
Mind
```

## Family

```text
Governing Family
```

## Purpose

`Mind` establishes the active logical and transactional context in which KANGER knowledge is observed and processed.

It provides the visibility domain for semantic-object projections, coordinates inference services, owns runtime working state and governs child-transaction completion. It is not itself knowledge and is not a persistent copy of the knowledge base.

## Identity

A `Mind` is identified by its explicit runtime identity and by its position in one finite root/child transaction chain.

A root Mind and each child Mind are distinct architectural objects even when they expose the same inherited semantic state. Mind identity is not derived from database contents, storage generation identity, query text or the set of currently visible semantic units.

For a child Mind, the parent relation is an identity-relevant coordinate because it determines the inherited visibility domain and the completion target.

## Owner

The root Mind lifecycle is owned by the caller through the associated `User` context.

The completion lifecycle of a child Mind is governed by its parent Mind transaction protocol. The child shares the same `User` but is not independently owned by storage, a factory or a query.

`User` owns external storage resources and coordinates full-chain cleanup. `Mind` borrows those resources through root factories and does not close or replace the storage plugin independently.

## Spaces

A Mind exists in runtime space.

Two runtime subspaces are distinguished:

```text
root Mind space
child Mind transaction-overlay space
```

A root Mind establishes the current user-visible logical context. A child Mind establishes a provisional overlay over exactly one parent visibility domain.

Query-local execution state exists inside the owning Mind but is a subordinate runtime space, not a separate persistent Mind.

## Representations

The canonical representation of Mind is runtime-only.

A Mind has no independent persistent representation and is not hydrated as a durable object after shutdown. Durable knowledge visible through a root Mind is represented through factories and storage bases owned by `User`.

Diagnostic text, query output and external API handles may represent selected Mind state, but none of them defines Mind identity.

## Lifecycle

The root lifecycle is:

```text
constructed
    -> initialized
    -> active
    -> quiescent
    -> cleared or disposed
```

The child lifecycle is:

```text
constructed
    -> transaction reserved in parent
    -> active provisional overlay
    -> committed | released
    -> detached/disposed
```

Every child Mind must terminate exactly once through commit or release semantics. Constructor failure and partial-completion failure must also discharge exactly one parent transaction reservation.

Root persistence operations may proceed only after transaction quiescence, when no child reservation remains active.

`clearMind()` disposes query-local and execution-local state. It does not redefine canonical semantic identities or destructively clear external storage.

## Relations

### Lifecycle ownership

```text
User -> root Mind
parent Mind -> child completion protocol
Mind -> query-local runtime state
```

### Coordination

Mind coordinates:

```text
Factories
Linker
Analyzer
Calculator
Compiler
query-local stores
```

Coordination does not imply ownership of external storage resources or canonical semantic identity.

### Projection authority

Mind provides the observing space for projections including:

```text
semantic visibility
deletion and restoration state
current TVariable/TValue state
Function/FValue applicability
Rule promotion state
query-result membership
provenance and execution state
```

### Reference dependency

Mind references one `User`, its parent/next transaction coordinate and the factories/services required by its active execution context.

### Storage relation

The root Mind interacts with storage through `User`-owned schema bases and root factories. A child Mind has no independent storage connection.

## Invariants

### M-001 — Mind is not knowledge

`Mind` shall not be classified as a semantic object or persistent knowledge unit.

### M-002 — One user across a chain

All Minds in one root/child chain shall reference the same `User`.

### M-003 — Explicit active context

Active Mind context shall be passed or selected explicitly. A compatibility slot such as `User.currentMind` shall not become hidden lifecycle authority.

### M-004 — Finite transaction chain

The root/child chain shall remain finite and acyclic.

### M-005 — Exactly-once completion

Each child Mind shall discharge exactly one parent transaction reservation through one terminal completion path.

### M-006 — Child is an overlay

A child Mind shall represent a provisional transaction overlay, not an independent database copy or persistent version.

### M-007 — Identity preservation

Commit and release shall not change the semantic identity of inherited canonical objects.

### M-008 — Commit coherence

Commit shall combine participating factory overlays, deletion/restoration state and surviving execution result as one coordinated transition. Partial publication shall not be treated as successful completion.

### M-009 — Release boundary

Release shall discard child-local logical changes while preserving only runtime or diagnostic state explicitly permitted by the release contract.

### M-010 — Persistence quiescence

Root pack, update and flush operations shall execute only after all active child transactions have completed.

### M-011 — Storage ownership boundary

Mind shall not close, replace or independently lifecycle-own the external storage plugin.

### M-012 — Runtime/persistent separation

Query-local stores, hypotheses, solutions, values, C-variable links, usage maps, flood control and linker indexes shall not be treated as durable semantic state merely because they are held by Mind.

### M-013 — Lock-order preservation

Mind-dependent hydration and semantic effects shall not execute while holding incompatible factory metadata locks. Established lock ordering shall be preserved.

## Architectural Notes

`Mind` is the active regulator of a KANGER world, but not the world’s store of knowledge. It establishes where knowledge is observed, which provisional state is visible and how a transaction resolves.

The root Mind represents the current user working context. A child Mind represents a bounded alternative visibility state whose logical effects either become visible to the parent through commit or disappear through release.

---

# 2. Rule

## Name

```text
Rule
```

## Family

```text
Semantic Family
```

## Purpose

`Rule` is the canonical semantic object that expresses one normalized logical rule of KANGER.

It combines an ordered branch structure of `Domain` objects with Rule-local variable definitions and semantic properties required for matching, inference and provenance. A Rule may be entered explicitly or generated during inference, but these origins do not by themselves create different semantic identities.

## Identity

Rule identity is defined by normalized semantic structure as evaluated by the canonical Rule registry.

The defining structure includes the ordered Rule tree, the semantic identity of its Domains and normalized Rule-local variable coordinates. Source spelling, original variable names, Java reference identity and the operational numeric ID do not independently define semantic sameness.

The operational ID names the registered canonical representative within the applicable allocation domain. It preserves reference continuity and persistence linkage but does not replace structural equality.

The origin text is provenance and a persistent representation attribute. It is not a separate canonicalization key when the normalized Rule structure is already equivalent.

The following states do not create a second Rule identity:

```text
query membership
generated status
stored status
primary-promotion status
second/duplicate marker
usage state
causes and solutions
deletion or restoration projection
```

## Owner

Canonical registration and lifecycle authority belong to `RuleFactory` in the observing Mind.

A Rule is semantically composed from Domains and Rule-local variable definitions, but those contained objects retain their own canonical identities and factory lifecycles. The Rule does not lifecycle-own a Domain merely because the Domain occurs in its tree.

The active Mind governs visibility, deletion/restoration and transaction-local promotion projections. Root storage owns only the durable representation of the Rule, not its semantic identity.

## Spaces

A Rule may be observed in:

```text
semantic space
root Mind space
child Mind transaction-overlay space
persistent storage representation space
query-local execution space
```

Semantic space contains the canonical Rule identity and normalized structure.

Root and child Mind spaces provide potentially different visible projections of the same canonical Rule. Query-local execution space may record usage, solutions and inference participation without redefining the Rule.

Persistent storage contains a durable representation of the registered Rule, not a separate semantic Rule.

## Representations

A Rule may have the following representations:

```text
canonical runtime representation
partially hydrated runtime shell
transaction-local effective projection
persistent ByteBuffer/record representation
diagnostic or source representation
```

A transaction-local promotion view may expose an existing generated Rule as an effective primary Rule without receiving a new ID or becoming a second canonical object.

The persistent representation records operational identity, owning Mind generation, deletion state, origin reference, variable-index boundary, classification flags, Domain references and embedded Cause representations.

No representation, including source text or serialized bytes, independently defines Rule identity.

## Lifecycle

The canonical Rule lifecycle is:

```text
constructed candidate
    -> structural lookup
    -> existing canonical Rule | new registration | resurrection
    -> visible projection
    -> optional transaction-local state changes
    -> committed | released
    -> optional durable materialization/update
```

A new Rule created in a child Mind is provisional until transaction resolution. Commit publishes its surviving canonical identity into the parent visibility domain; release removes the child-local projection.

A matching logically deleted Rule may be restored or resurrected with its existing canonical ID rather than replaced by a duplicate.

Generated-to-primary promotion changes the effective projection of an existing Rule. It becomes a durable raw-state change only during root persistence materialization.

Logical deletion hides the Rule in an observing Mind. Physical removal of its persistent record is a later storage operation and does not occur merely because the deletion projection was set.

## Relations

### Canonical registration

```text
RuleFactory -> Rule canonical registration and lookup
```

`RuleFactory` resolves candidates through structural equality after hash or index narrowing. Candidate indexes are acceleration structures, not semantic authorities.

### Semantic containment

A Rule semantically contains:

```text
ordered branches of Domain references
Rule-local TVariable definitions
Function occurrences inside argument structure
```

Containment does not transfer canonical lifecycle ownership of contained semantic units to the Rule.

### Reference dependency

A Rule references:

```text
origin Term
Domain objects
Predicate and Term identities derived from its structure
Cause relations
TValue solutions
```

Derived predicate and term indexes reference Rule IDs and do not own Rules.

### Projection

A Rule may have Mind-relative projections for:

```text
visibility
deletion and restoration
generated-to-primary promotion
query status
stored status
usage
causes
solutions
continuation effects
```

### Representation

```text
persistent record represents Rule
source text represents Rule origin
diagnostic rendering represents visible Rule state
```

None of these representation relations changes canonical identity.

## Invariants

### RUL-001 — Structural canonical identity

Two Rule candidates with equivalent normalized semantic structure shall resolve to one canonical Rule identity within the applicable registry domain.

### RUL-002 — Operational ID is not semantic equality

A Rule ID shall identify a registered representative but shall not replace normalized structural comparison during canonicalization.

### RUL-003 — Source spelling is not identity

Origin text, variable spelling and diagnostic rendering shall not independently define Rule identity.

### RUL-004 — Canonicalization before publication

A newly constructed Rule candidate shall not be published as a distinct Rule until canonical lookup has excluded an existing equivalent visible or restorable Rule.

### RUL-005 — Projection preserves identity

Deletion, restoration, query membership, usage, generated state, stored state and primary promotion shall not create a second canonical Rule.

### RUL-006 — Promotion preserves identity

Generated-to-primary promotion shall preserve the Rule ID and semantic structure. A transaction-local promotion view shall not be treated as a durable independent Rule.

### RUL-007 — Containment is not lifecycle ownership

Occurrence of a Domain, TVariable, Function or Term in Rule structure shall not transfer that object’s canonical registry lifecycle to the Rule.

### RUL-008 — Child isolation

A child Mind shall not directly mutate parent-visible raw Rule state when the change belongs to a child-local projection.

### RUL-009 — Coherent transaction resolution

Rule creation, resurrection, deletion/restoration state, derived indexes, promotion intent and continuation action shall resolve coherently across commit or release.

### RUL-010 — Derived indexes are non-authoritative

Predicate, Term and candidate indexes shall contain references or candidate IDs only. They shall not define Rule existence, ownership or semantic equality.

### RUL-011 — Hydration outside incompatible locks

Rule hydration and Mind-dependent semantic checks shall not execute while holding locks whose ordering can re-enter Mind or factory lifecycle locks.

### RUL-012 — Logical deletion precedes physical reclamation

A logically deleted Rule shall remain distinguishable from a nonexistent or physically removed Rule until the root persistence lifecycle performs reclamation.

### RUL-013 — Persistence preserves canonical continuity

Packing, updating, hydration and restoration shall preserve the registered Rule identity and shall not create duplicate semantic Rules.

### RUL-014 — Ordering is explicit

Execution order over Rule IDs shall be specified by the consuming process. Cache insertion order and candidate-index order shall not be treated as a canonical Rule-order contract.

## Architectural Notes

`Rule` is the principal durable logical structure of KANGER, but not an isolated text statement. Its architectural identity is the normalized semantic graph registered by `RuleFactory`.

A Rule can be visible differently in root, child and query-local spaces while remaining one canonical object. Promotion, deletion, restoration, provenance and execution participation describe its projections and history; they do not replace its identity.
