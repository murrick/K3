# Factory and Storage Object Model

## Status

Normative chapter of `KANGER Architectural Object Model`, artifact `3.6.0`.

Baseline:

```text
develop/3.5.0.7
```

This chapter defines the boundary between semantic identity, runtime canonicalization, transaction-local projection and physical persistence.

---

## 1. Fundamental separation

KANGER distinguishes three authorities that must not be collapsed into one abstraction:

```text
Mind
    defines the active logical and transactional space

Factory
    canonicalizes semantic candidates and controls their visible registry projection

Storage
    owns durable representations and their physical lifecycle
```

None of these authorities replaces the others.

A `Mind` does not own the physical storage generation. A factory does not define the external storage lifecycle. A storage backend does not decide semantic equality.

The canonical boundary is:

```text
semantic identity
    governed by the unit-specific equality contract

canonical runtime representative
    selected and registered by the owning factory

transaction-visible projection
    observed through the factory overlay of a particular Mind

persistent representation
    stored and hydrated through IBase inside an IData generation
```

---

## 2. Factory as an architectural object

A KANGER factory is a **canonical registry and projection authority** for one semantic object family.

The word `Factory` must not be interpreted merely as a constructor. A Java constructor can allocate a candidate representation, but only the specialized factory can determine whether that candidate denotes:

- an already visible canonical object;
- a logically deleted canonical object that must be restored;
- a new semantic object that must receive an operational ID and be published;
- an illegal or conflicting candidate.

The canonical transition is:

```text
constructed representation
        ↓
semantic candidate
        ↓  hash/index lookup
candidate set
        ↓  equalsTo / structural equality
canonical representative
        ↓
transaction-visible publication
```

Hash lookup and indexes only reduce the candidate set. They never establish semantic identity by themselves.

### 2.1 Factory identity

A factory is not semantic knowledge. It is a service/governing object whose identity is determined by:

- semantic family or schema;
- owning `Mind`;
- position in the parent/child factory-overlay chain;
- attached root storage base, where applicable.

A child factory is not the same runtime object as its parent factory, even though both expose one layered canonical namespace.

### 2.2 Factory ownership

A factory owns:

- its local cache projection;
- transaction-local registrations;
- local deletion/restoration state according to the family contract;
- checkpoints and rollback journals;
- derived lookup indexes;
- family-specific continuation metadata.

A factory does **not** own:

- the semantic meaning of the registered object;
- the physical storage generation;
- `IData` lifecycle;
- objects merely referenced by the registered unit;
- the query or transaction as a whole.

The factory's lifecycle is coordinated by its owning `Mind`. The physical bases used by a root factory are borrowed from the `User`-owned storage subsystem.

---

## 3. Factory overlay

Each child `Mind` receives child factories layered over the factories of its parent.

The child factory provides a transaction-visible projection of one canonical namespace:

```text
visible(child factory)
    = inherited parent state
    + child-local registrations
    + child-local deletion/restoration state
    + family-specific local projections
```

This is not a full copy of the parent registry.

The parent remains the authority for inherited representations. The child stores only the additional state required to produce its effective view.

### 3.1 Typed commit

A typed child-to-parent factory commit publishes surviving child state into the parent factory according to the semantic-family contract.

It may perform:

- duplicate elimination;
- restoration of an existing canonical identity;
- transfer of newly registered units;
- publication of promotion intent;
- reconciliation of indexes and continuation state.

Typed factory commit is not physical storage flush.

### 3.2 Checkpoint commit

A no-argument cache/factory checkpoint commit accepts changes made since a local `mark()` inside the same factory instance.

It is not equivalent to child-to-parent publication.

The two operations belong to different lifecycle dimensions:

```text
checkpoint commit
    accepts a speculative frame inside one factory

typed factory commit
    publishes a child overlay into its parent

Mind commit
    coordinates all participating factories and runtime state

storage flush
    establishes physical durability
```

These terms must never be used as synonyms.

---

## 4. Cache as a projection object

`ICache` is the transaction-local snapshot/projection layer used by a factory.

It can hold:

- newly constructed canonical units;
- hydrated runtime representatives;
- locally modified representations;
- deletion state;
- checkpoint journals;
- linked snapshot endpoints.

The cache is neither the semantic object space nor persistent storage.

Its principal relation is:

```text
cache
    represents and projects semantic units
    inside one factory lifecycle
```

A cache entry may disappear while the semantic object remains persistent. A persistent record may exist without currently having a hydrated cache entry.

Therefore:

```text
cache presence != semantic existence
cache absence  != semantic absence
cache deletion != physical deletion
cache commit   != storage durability
```

`ICache.find(hash)` returns candidate IDs only. The owning factory must perform the final semantic equality check.

---

## 5. Storage hierarchy

The storage object model contains three distinct levels.

```text
IData
    physical generation and storage-wide lifecycle

IBase
    one schema-specific persistent address space

IStep / serialized packet
    one durable representation node
```

### 5.1 IData

`IData` is the owner of one selected physical storage generation and the set of logical bases opened inside it.

It owns:

- generation acquisition and selection;
- the registry of `IBase` instances;
- storage-wide flush and close;
- physical generation removal;
- migration/reindex sequencing;
- backend-specific recovery resources.

`IData` is owned and published by `User`, not by `Mind` or a factory.

It is not:

- a logical transaction;
- a knowledge base in the semantic sense;
- a factory registry;
- a current query context.

### 5.2 IBase

`IBase` is one schema-specific persistent address space inside the selected generation.

It owns:

- physical records for one schema;
- schema-local ID allocation;
- persistent linked-chain endpoints;
- backend hydration cache;
- physical add/update/delete operations;
- schema-local durability participation.

A root factory borrows its corresponding `IBase`. A child factory has no independent storage connection.

`IBase.containsKey(id)` proves only that a physical record exists in that schema namespace. It does not prove that the record is semantically visible in the observing `Mind`.

### 5.3 Persistent step and packet

An `IStep`, `Sapato`, `ByteBuffer` or backend record is a representation object.

It carries enough information to reconstruct a runtime unit and linked storage topology, but it does not define semantic identity independently.

The relation is:

```text
persistent record
    represents
semantic unit
```

not:

```text
persistent record
    is
semantic unit
```

A record can be rewritten, relocated, cached, evicted or reconstructed without creating a new semantic object.

---

## 6. Identity domains

The storage and semantic layers use related but non-identical identity domains.

### 6.1 Semantic identity

Established by the unit-specific canonical equality contract.

Examples:

- normalized typed value for an ordinary `Term`;
- normalized Rule structure;
- `(TVariable id, Term id)` for `TValue`;
- Function definition plus applicable input stamp for an `FValue` lookup.

### 6.2 Operational unit ID

Assigned by the owning factory/registry to identify the canonical registered representative.

It is stable enough for references and persistence but does not replace semantic equality.

### 6.3 Schema-local persistent ID

Used by `IBase` inside one schema and selected storage generation.

The numeric value is meaningful only together with its schema/generation domain.

### 6.4 Linked traversal position

Defined by `IStep.next` and restored chain endpoints.

It is not determined by numeric ID order.

Therefore:

```text
semantic identity != numeric ID
numeric ID != traversal order
physical address != semantic identity
```

---

## 7. Canonicalization and hydration

Canonicalization and hydration are orthogonal operations.

### 7.1 Canonicalization

Answers:

> Which semantic object does this candidate denote?

It may return an already live representative, restore a deleted one or register a new object.

### 7.2 Hydration

Answers:

> How do we reconstruct the required runtime representation of an already identified object?

Hydration may load a unit from `IBase`, resolve referenced IDs through other factories and publish a live representative in cache.

Hydration failure is not proof of semantic nonexistence. It may indicate corruption, unavailable storage or an unresolved dependency.

### 7.3 Materialization

Materialization publishes a prepared runtime/cache representation into persistent form or turns a potential semantic result into a registered semantic unit, depending on the named layer.

Because the word is used at more than one boundary, every normative use must state the target space:

```text
semantic materialization
    potential result -> registered semantic object

storage materialization
    memory/cache state -> durable record
```

The Encyclopedia entry for `materialization` must preserve this distinction as one term with explicitly named variants, not as ambiguous prose.

---

## 8. Deletion and physical reclamation

KANGER distinguishes at least three states:

```text
transaction-visible deletion
    object hidden in one Mind projection

factory/cache deletion state
    registry records local deletion intent

physical reclamation
    persistent record removed or rewritten by storage materialization
```

A deleted semantic object can retain canonical identity and operational ID. It may be restored before physical reclamation.

Physical deletion is therefore not the first stage of deletion and must never be used as transaction rollback.

The governing invariant is:

```text
deleted != nonexistent
hidden != physically removed
rollback != destructive storage delete
```

---

## 9. RuleFactory as the reference implementation

`RuleFactory` demonstrates the complete separation of authorities.

It combines:

- an `Escalera` cache chain;
- optional root `IBase` connection;
- structural canonical lookup;
- child overlay visibility;
- transaction-local primary-promotion projections;
- ID-only acceleration indexes;
- nested checkpoints;
- typed child-to-parent commit;
- later root persistence materialization.

Its indexes contain Rule IDs only. They neither own Rules nor establish semantic equality.

A generated-to-primary promotion is represented in a child factory as an effective projection with the same canonical Rule ID. It is not a new Rule object. Root persistence later materializes the promotion into the durable representation.

This sequence is canonical evidence for the projection model:

```text
canonical Rule
    + child-local promotion projection
    + parent publication on commit
    + durable record update on root persistence
```

No semantic object travels between spaces. Visibility and representations change under controlled authorities.

---

## 10. Authority matrix

| Operation | Mind | Factory | Cache | IBase | IData |
|---|---|---|---|---|---|
| Establish active logical space | authority | participant | no | no | no |
| Decide semantic equality | coordinates family use | authority through unit contract | candidate lookup only | no | no |
| Assign/register operational unit ID | coordinates transaction | authority | stores projection | persists assigned ID | no |
| Publish child state to parent | transaction authority | family-specific authority | carries local state | no | no |
| Create checkpoint | coordinates composite frame | authority | implementation mechanism | no | no |
| Roll back speculative cache state | coordinates composite rollback | authority | implementation mechanism | no | no |
| Hydrate persistent representation | supplies context | coordinates canonical publication | receives representative | physical read authority | generation owner |
| Add/update persistent record | initiates only at root lifecycle | prepares/materializes units | supplies changed state | schema authority | generation owner |
| Flush durability | requires transaction quiescence | prepares updates | no independent authority | participates | storage-wide authority |
| Close physical resources | no | no | no | subordinate resource | authority |
| Remove generation | no | no | no | no | destructive authority |

---

## 11. Cross-layer invariants

1. A factory is a canonical registry, not merely a constructor.
2. A factory index produces candidates, not semantic truth.
3. Child factories are overlays, not copies of parent registries.
4. Typed factory commit, checkpoint commit, Mind commit and storage flush are distinct operations.
5. Cache presence does not define semantic existence.
6. `IData` owns physical generation lifecycle.
7. `IBase` owns one schema-specific persistent namespace.
8. Root factories borrow bases; child factories do not own storage connections.
9. Hydration reconstructs representation; canonicalization resolves identity.
10. Logical deletion precedes and is distinct from physical reclamation.
11. Persistent records represent semantic objects but are not those objects.
12. No object moves between spaces; authorities publish projections and transform representations.

---

## 12. Terminology synchronization set

The following canonical terms must be synchronized with the KANGER Encyclopedia:

- Factory;
- canonical registry;
- candidate;
- canonical representative;
- factory overlay;
- cache projection;
- checkpoint;
- typed commit;
- hydration;
- semantic materialization;
- storage materialization;
- persistent generation;
- logical base;
- persistent record;
- schema-local ID;
- physical reclamation;
- durability boundary.

Each term must have one current definition and explicit boundaries against adjacent concepts.