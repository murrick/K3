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

Monograph
    semantic and philosophical interpretation of the KANGER world
```

The Object Model does not replace Javadoc and does not duplicate class-by-class descriptions. It describes the laws by which KANGER entities exist and relate to one another independently of individual Java implementation details.

---

## 1. Scope and method

The model answers four canonical questions for every object family:

1. What makes two representations the same object?
2. In which space or spaces does the object exist?
3. Who owns the object and controls its lifecycle?
4. How is the object created, materialized, published, hidden, committed, released or restored?

The analysis must distinguish coordinates that Java frequently collapses into one reference:

- semantic identity;
- runtime representation;
- transaction-visible projection;
- persistent representation.

A Java instance is therefore not automatically a KANGER object. It may be only a temporary representation, compiler node, storage packet, lookup key or diagnostic structure.

Conversely, one KANGER object may have several representations during its lifetime without losing semantic identity.

### 1.1 Canonical terminology contract

The KANGER Encyclopedia is the normative source of terminology for the whole documentation corpus.

For every term, the Encyclopedia contains one current and unambiguous definition. Historical meanings, superseded interpretations and abandoned names do not form part of the normative definition and must not burden the reader of current documentation.

The four documentation layers have distinct responsibilities:

```text
KANGER Encyclopedia
    defines the canonical vocabulary

Architectural Object Model
    builds the system model using that vocabulary

Javadoc
    applies the vocabulary to local engineering contracts

Monographs
    develop semantic and philosophical ideas without redefining terms
```

Every canonical encyclopedia entry should provide only what is needed for precise current usage:

- the preferred term;
- one normative definition;
- the boundary of applicability;
- relations to other canonical terms;
- implementation names that a reader must know when code compatibility prevents renaming;
- references to the owning Object Model sections, Javadoc contracts and monograph chapters.

An implementation identifier may retain a historical or imperfect name, but this does not create a second architectural meaning. In such a case the documentation states the current meaning directly and treats the code name only as a compatibility identifier.

No chapter of the Object Model is considered complete until every new or refined term has been checked against the Encyclopedia and all conflicting current definitions have been eliminated.

The initial synchronization set is:

- object;
- identity;
- space;
- ownership;
- visibility;
- lifecycle;
- canonicalization;
- hydration;
- materialization;
- publication;
- projection;
- transaction overlay;
- commit;
- release;
- deletion;
- restoration;
- resurrection;
- Mind;
- Factory;
- Unit;
- semantic object;
- contextual object;
- runtime object;
- representation object.

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

Entities whose identity, meaning or visible state includes a `Mind`, `Rule`, transaction level or execution context as a required coordinate.

Typical cases include:

- Rule-owned variables;
- Rule-scoped U-children;
- transaction-local promotion or deletion projections;
- materialized values whose visibility depends on the observing `Mind`.

The same canonical entity may have different transaction-visible states without becoming a different durable entity.

#### C. Runtime execution objects

Entities that coordinate inference or represent an active computation but are not themselves durable semantic knowledge.

Typical families:

- `Mind`;
- `Linker`;
- `Analyzer`;
- `Calculator`;
- `Solve`;
- execution continuations and query-local stores.

Some runtime objects have strong identity and lifecycle; others are transient projections over canonical units. They must not be classified solely by whether they implement `IUnit`.

#### D. Representation objects

Objects that represent another entity in one layer but do not define its semantic identity.

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

These objects are architecturally significant because they govern the existence, visibility, canonicalization or persistence of other objects.

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

Boundary cases are decided by contract, not naming. A compiler node may be only a temporary representation, while a `Solve` may carry identity relevant to execution deduplication even though it is not durable knowledge.

---

## 4. Identity coordinates

### 4.1 Durable identity

Identity preserved across persistence, shutdown and hydration. It may be represented by a persistent or operational ID, but it is never defined by the numeric value alone.

### 4.2 Canonical runtime identity

The unique live representative selected by the owning factory or registry for a visible semantic entity.

Candidate hash narrows lookup; semantic equality confirms the match. Hash equality is not identity.

### 4.3 Structural identity

Identity determined by normalized semantic structure, for example a Rule independently of source-level variable names.

### 4.4 Contextual identity

Identity whose key includes an owning context, such as:

```text
(parent C-variable, target Rule id) -> canonical U-child
```

Removing the context coordinate produces illegal aliasing.

### 4.5 Transaction-visible projection

The visible state of one canonical entity when observed through a transaction overlay containing local publication, promotion, deletion, restoration or replacement state.

A different projection does not automatically imply a different semantic object.

### 4.6 Transient execution identity

Identity valid only within one query, compilation, linking pass or continuation lifecycle.

---

## 5. Object spaces

The model distinguishes at least these spaces:

1. persistent storage;
2. root `Mind`;
3. child `Mind` transaction overlay;
4. query-local execution space;
5. compiler representation space;
6. external caller/API space.

An object may be represented in several spaces simultaneously. Movement between spaces is not necessarily object creation. Hydration, publication, commit and serialization are distinct transitions.

---

## 6. Ownership

Ownership is the authority to control visibility or lifecycle, not merely the presence of a Java field.

Preliminary ownership graph:

```text
User
  -> root Mind lifecycle
  -> external storage resources

Mind
  -> factory overlays
  -> runtime stores
  -> transaction-visible state
  -> Linker / Analyzer / Calculator execution context

Rule
  -> semantic containment of Domains
  -> Rule-local TVariable definitions
  -> Rule-scoped U-child context

Function
  -> Arguments
  -> result projection

Storage plugin
  -> physical generations
  -> logical bases
  -> serialized unit representations
```

Every arrow must ultimately be classified as one of:

- lifecycle ownership;
- semantic containment;
- visibility authority;
- reference dependency;
- representation ownership.

---

## 7. Lifecycle vocabulary

The Object Model uses the following terms consistently:

- **constructed** — a Java representation has been allocated;
- **candidate** — a representation is being checked against canonical state;
- **canonicalized** — resolved to the unique visible semantic representative;
- **hydrated** — required state has been reconstructed from storage or another representation;
- **published** — made visible in a `Mind` or registry;
- **provisional** — visible only in a child transaction;
- **committed** — merged into the parent visibility domain;
- **released** — local transaction state discarded or detached according to contract;
- **deleted** — hidden by transaction-visible deletion state;
- **physically removed** — storage representation reclaimed or rewritten;
- **restored** — a hidden or unloaded entity becomes visible or materialized again;
- **resurrected** — canonical identity is reused after prior deletion or loss of its live representation.

These terms are not synonyms:

```text
constructed != canonicalized
canonicalized != hydrated
hydrated != published
deleted != physically removed
restored != newly created
```

---

## 8. First production-validated object matrix

The matrix records the architectural object, not merely the Java class. `Persistent` means that the object's defining state has a durable representation; it does not mean that every runtime projection is stored.

| Object family | Object class | Defining identity | Primary owning space | Lifecycle authority | Persistent | Canonicalized | Context-dependent |
|---|---|---|---|---|---|---|---|
| `Mind` | runtime and governing object | explicit Mind identity plus its position in the parent/child transaction chain | User-owned root space or parent-owned child overlay | caller/User for root; parent transaction protocol for child | root state indirectly through owned factories/storage; child itself is not durable | no semantic factory canonicalization | yes, intrinsically |
| `Rule` | canonical semantic object | normalized rule structure confirmed by RuleFactory equality contract; operational ID names the registered representative | factory registry visible through a Mind | RuleFactory and observing Mind overlay | yes | yes | structure is canonical; visibility, promotion, causes and deletion are contextual |
| `Term` | canonical semantic object with contextual subtypes | typed normalized value for ordinary terms; contextual key for C/U terms includes rule-related coordinates | DictionaryFactory visible through a Mind | DictionaryFactory and Mind overlay | yes | yes | ordinary terms mostly no; C/U terms yes |
| `TVariable` | Rule-owned canonical semantic object | variable definition: owning Rule plus rule-local name/index coordinates and registered ID | Rule semantic space, represented through TVariableFactory | Rule definition and TVariableFactory; active values belong to Mind runtime state | yes, as a variable definition | yes | definition is Rule-dependent; current value and deletion are Mind-dependent |

### 8.1 Mind

`Mind` is an object of the architecture, but not a semantic unit of knowledge.

Its defining role is to establish an active logical and transactional space. A root Mind represents the user's current working logical context. A child Mind represents a provisional overlay over one parent.

A child Mind is not a durable copy of the database. Its lifetime begins with transaction reservation and ends exactly once through `commit` or `release`. Query-local hypotheses, values, solutions, C-variable links and linker indexes belong to this runtime space rather than to persistent semantic identity.

Consequently:

```text
Mind identity != database identity
child Mind != persistent version
Mind ownership of factories != ownership of the external storage plugin
```

### 8.2 Rule

`Rule` is a canonical semantic object.

Its operational ID identifies the registered representative, while canonical sameness is established by normalized semantic structure through the Rule factory contract. Source spelling and variable names are representations and do not independently define the Rule.

A Rule contains semantic structure and references to Domains and variable definitions. At the same time, several Rule states are projections relative to a Mind:

- visibility;
- deletion or restoration;
- promotion from generated to basic status;
- causes and generated-state effects;
- query-local usage and solutions.

Thus the Rule is one canonical semantic object with potentially different transaction-visible projections.

### 8.3 Term

`Term` is not one homogeneous identity family.

For ordinary values, identity is based on normalized typed content and is canonicalized by the dictionary factory. A newly allocated `Term` is only a candidate until the factory either reuses an existing representative or publishes a new canonical one.

C-variable and U-child terms introduce contextual identity coordinates. In particular, a U-child is canonical only within:

```text
(parent C-variable, target Rule id)
```

Therefore the general statement "one value means one Term" is valid only after the exact term kind and its required context coordinates have been specified.

### 8.4 TVariable

`TVariable` is the persistent definition of one substitution position owned by a Rule. It is not the current substitution value.

Its stable inside consists of:

- owning Rule;
- original variable name;
- rule-local index;
- registered identifiers.

Its current value exists outside the variable as a `TValue` projection in an active Mind. Setting a value therefore does not mutate TVariable identity.

The crucial separations are:

```text
variable definition != current value
Rule ownership != query membership
owner Mind != active execution Mind
undefined value != deleted variable
```

---

## 9. Planned chapters

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
16. Encyclopedia synchronization appendix.

---

## 10. Immediate next step

The next revision extends the production-validated matrix with:

- `Domain`;
- `TValue`;
- `Function`;
- `FValue`;
- `Solve`;
- `Cause`;
- Factory;
- storage objects.

Each classification must be supported by current production code, qualified Javadoc contracts and closure documents. Philosophical interpretation remains outside the normative Object Model and will be developed in the monograph using the same canonical vocabulary.
