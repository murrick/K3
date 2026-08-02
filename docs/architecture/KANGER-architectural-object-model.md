# KANGER Architectural Object Model

## Status

Working architectural specification for the `3.6.0` documentation artifact.

Baseline:

```text
develop/3.5.0.7
```

Working branch:

```text
arch/3.6.0-object-model
```

This document is the second layer of the KANGER documentation system:

```text
Javadoc
    local engineering contracts and entity passports

Architectural Object Model
    coherent model of object kinds, identity, ownership, visibility and lifecycle

Monograph, volume II
    philosophical interpretation of the KANGER world
```

The object model does not replace Javadoc and does not duplicate class-by-class descriptions. It describes the laws by which KANGER entities exist and relate to one another independently of individual Java implementation details.

---

## 1. Scope and method

The model answers four canonical questions for every object family:

1. What makes two representations the same object?
2. In which space or spaces does the object exist?
3. Who owns the object and controls its lifecycle?
4. How is the object created, materialized, published, hidden, committed, released or restored?

The analysis must distinguish at least four coordinates that Java frequently collapses into one reference:

- semantic identity;
- runtime representation;
- transaction-visible projection;
- persistent representation.

A Java instance is therefore not automatically a KANGER object. It may be only a temporary representation, a compiler node, a storage packet, a lookup key or a diagnostic structure.

Conversely, one KANGER object may have several representations during its lifetime without losing semantic identity.

---

## 2. What counts as an object in KANGER

### 2.1 Working definition

A KANGER object is a system-recognized entity that has a defined form of identity and participates in at least one controlled relation of ownership, visibility or lifecycle.

The definition is intentionally broader than persistent semantic units and narrower than all Java allocations.

The presence of a Java class, an object reference or serialized bytes alone is insufficient. The entity must have system-level rules that answer at least part of the canonical matrix:

```text
identity
ownership
visibility
lifecycle
representation
```

### 2.2 Preliminary object classes

The first working classification contains five classes.

#### A. Canonical semantic objects

Entities whose semantic identity is recognized by KANGER and whose duplicate construction is prevented or collapsed by a canonical registry.

Typical families:

- `Predicate`;
- `Rule`;
- `Domain`;
- `Term`;
- `TVariable`;
- `TValue`;
- `Function`;
- `FValue`;
- provenance-bearing semantic units.

Their Java reference is an implementation-level handle. Canonical equality is determined by the unit-specific identity contract, not by reference equality alone.

#### B. Contextual semantic objects

Entities whose meaning or identity is inseparable from a particular `Mind`, `Rule`, transaction level or execution context.

Typical cases include:

- Rule-owned variables;
- Rule-scoped U-children;
- transaction-local promotion or deletion projections;
- materialized values whose visibility depends on the observing `Mind`.

The same underlying semantic content may therefore have different transaction-visible states without becoming a different durable entity.

#### C. Runtime execution objects

Entities that coordinate inference or represent an active computation but are not necessarily durable semantic knowledge.

Typical families:

- `Mind`;
- `Linker`;
- `Analyzer`;
- `Calculator`;
- `Solve`;
- execution continuations and query-local stores.

Some runtime objects have strong identity and lifecycle; others are transient projections over canonical units. They must not be classified solely by whether they implement `IUnit`.

#### D. Representation objects

Objects that represent another entity in a particular layer but do not define its semantic identity.

Examples:

- `ByteBuffer` storage packets;
- map projections created by `createMap()`;
- compiler `Token` and `Leaf` structures;
- persistent IDs and storage discriminators;
- cached or partially hydrated shells.

A representation may be destroyed and recreated while the represented semantic object remains the same.

#### E. Service and infrastructure objects

Objects that own registries, perform transitions or provide access to object spaces.

Examples:

- `User`;
- factories;
- runtime stores;
- `IData`, `IBase` and storage implementations;
- libraries and UDF bindings.

These objects are architecturally significant not because they denote knowledge, but because they govern the existence, visibility, canonicalization or persistence of other objects.

---

## 3. Non-objects and boundary cases

The following Java allocations are not automatically independent KANGER objects:

- temporary collections used inside one algorithm;
- immutable constants without lifecycle;
- diagnostic DTOs;
- qualification runners;
- serialized byte arrays considered apart from the unit they encode;
- transient parser helpers;
- hash values and lookup buckets;
- vendored Rhino implementation objects unless they cross the KANGER UDF boundary.

Boundary cases must be decided by contract, not naming. For example, a compiler node may be only a temporary representation, while a `Solve` may carry identity relevant to provenance or execution deduplication even though it is not a durable fact.

---

## 4. Identity coordinates

The object model will distinguish the following identity forms.

### 4.1 Durable identity

Identity preserved across persistence, shutdown and hydration. Usually represented by a persistent or operational ID, but never defined by the numeric value alone.

### 4.2 Canonical runtime identity

The unique live representative selected by the owning factory or registry for a visible semantic entity.

Candidate hash narrows lookup; semantic equality confirms the match. Hash equality is not identity.

### 4.3 Structural identity

Identity determined by normalized semantic structure, for example a Rule independent of source-level variable names.

### 4.4 Contextual identity

Identity whose key includes an owning context, such as:

```text
(parent C-variable, target Rule id) -> canonical U-child
```

Removing the context coordinate produces illegal aliasing.

### 4.5 Transaction-visible identity

The same canonical entity observed through a transaction overlay with local publication, promotion, deletion or replacement state.

### 4.6 Transient execution identity

Identity valid only within one query, compilation, linking pass or continuation lifecycle.

---

## 5. Object spaces

The full model will describe at least these spaces:

1. persistent storage;
2. root `Mind`;
3. child `Mind` transaction overlay;
4. query-local execution space;
5. compiler representation space;
6. external caller/API space.

An object may be represented in several spaces simultaneously. Movement between spaces is not always object creation. Hydration, publication, commit and serialization are distinct transitions.

---

## 6. Ownership

Ownership is the authority to control visibility and lifecycle, not merely the presence of a Java field.

Preliminary ownership graph:

```text
User
  -> root Mind

Mind
  -> factories
  -> runtime stores
  -> transaction overlay
  -> Linker / Analyzer / Calculator context

Rule
  -> Domains
  -> Rule-local TVariables
  -> Rule-scoped U-children through parent C-variable relation

Function
  -> Arguments
  -> result projection

Storage plugin
  -> physical generations
  -> logical bases
  -> serialized unit representations
```

This graph is provisional. Every arrow must later be classified as one of:

- lifecycle ownership;
- semantic containment;
- visibility authority;
- reference dependency;
- representation ownership.

---

## 7. Lifecycle vocabulary

The object model will use the following terms consistently:

- **constructed** — a Java representation has been allocated;
- **candidate** — a representation is being checked against canonical state;
- **canonicalized** — resolved to the unique visible semantic representative;
- **hydrated** — required state has been materialized from a representation or storage;
- **published** — made visible in a `Mind` or registry;
- **provisional** — visible only in a child transaction;
- **committed** — merged into the parent visibility domain;
- **released** — local transaction state discarded or detached according to contract;
- **deleted** — hidden by a transaction-visible deletion state;
- **physically removed** — storage representation reclaimed or rewritten;
- **restored** — a hidden or unloaded entity becomes visible/materialized again;
- **resurrected** — canonical identity is reused after prior deletion or loss of live representation.

These terms are not synonyms. In particular:

```text
constructed != canonicalized
canonicalized != hydrated
hydrated != published
deleted != physically removed
restored != newly created
```

---

## 8. Planned chapters

1. Scope, terminology and object criteria.
2. Object spaces and boundaries.
3. Identity model.
4. Ownership and containment.
5. Canonicalization and factory ecosystem.
6. Hydration and representation transitions.
7. Mind and transactional overlays.
8. Semantic object families.
9. Runtime execution objects.
10. Compiler and parser representation model.
11. Storage object model.
12. External API and UDF boundaries.
13. Lifecycle state machines.
14. Cross-object invariants.
15. Consolidated relationship map.

---

## 9. Immediate next step

The next revision must validate the preliminary classification against the actual production types and produce the first object matrix:

| Object family | Identity | Owning space | Lifecycle owner | Persistent | Canonicalized | Context-dependent |
|---|---|---|---|---|---|---|
| Mind | pending | pending | pending | pending | pending | pending |
| Rule | pending | pending | pending | pending | pending | pending |
| Domain | pending | pending | pending | pending | pending | pending |
| Term | pending | pending | pending | pending | pending | pending |
| TVariable | pending | pending | pending | pending | pending | pending |
| TValue | pending | pending | pending | pending | pending | pending |
| Function | pending | pending | pending | pending | pending | pending |
| FValue | pending | pending | pending | pending | pending | pending |
| Solve | pending | pending | pending | pending | pending | pending |
| Cause | pending | pending | pending | pending | pending | pending |
| Factory | pending | pending | pending | pending | pending | pending |
| Storage unit | pending | pending | pending | pending | pending | pending |

The matrix will be filled only from current production code, closure documents and already qualified contracts. Philosophical interpretations are intentionally deferred to the second volume of the monograph.
