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

### 2.2 Object classes

#### A. Canonical semantic objects

Entities whose semantic identity is recognized by KANGER and whose duplicate construction is prevented or collapsed by a canonical registry.

Typical families include `Predicate`, `Rule`, `Domain`, `Term`, `TVariable`, `TValue`, `Function`, `FValue` and provenance-bearing semantic units.

Their Java reference is an implementation-level handle. Canonical equality is determined by the unit-specific identity contract, not by reference equality alone.

#### B. Contextual semantic objects

Entities whose identity, meaning or visible state includes a `Mind`, `Rule`, transaction level or execution context as a required coordinate.

Typical cases include Rule-owned variables, Rule-scoped U-children, transaction-local promotion or deletion projections and materialized values whose visibility depends on the observing Mind.

The same canonical entity may have different transaction-visible states without becoming a different durable entity.

#### C. Runtime execution objects

Entities that coordinate inference or represent an active computation but are not themselves durable semantic knowledge.

Typical families include `Mind`, `Linker`, `Analyzer`, `Calculator`, `Solve`, execution continuations and query-local stores.

#### D. Representation objects

Objects that represent another entity in one layer but do not define its semantic identity.

Examples include `ByteBuffer` packets, map projections, compiler `Token` and `Leaf` structures, persistent IDs, storage discriminators and partially hydrated shells.

#### E. Service and infrastructure objects

Objects that own registries, perform transitions or provide access to object spaces.

Examples include `User`, factories, runtime stores, `IData`, `IBase`, storage implementations, libraries and UDF bindings.

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

## 6. Relations and mappings

The Object Model distinguishes relations between objects from mappings of an object into a space. The generic words "contains", "belongs to" and "has" are insufficient unless the exact relation is named.

### 6.1 Lifecycle ownership

Lifecycle ownership is authority and responsibility for creation, publication, completion, release or destruction.

Examples:

```text
User owns the lifecycle of the root Mind
parent Mind owns completion protocol of a child Mind
Factory owns canonical registration lifecycle of its units
```

A Java field or collection membership does not by itself prove lifecycle ownership.

### 6.2 Semantic containment

Semantic containment means that an object participates as a constituent of another object's definition without surrendering independent identity.

Examples:

```text
Rule semantically contains Domain references
Function semantically contains an argument graph
```

A contained Domain remains a canonical object registered independently of the Rule that uses it.

### 6.3 Reference dependency

Reference dependency allows one object to identify or resolve another without controlling its lifecycle.

Examples:

```text
Cause references a Rule and a donor Solve
TValue references a TVariable and a Term
FValue references a Function and a result Term
```

Removing the reference does not by itself remove the referenced object.

### 6.4 Representation

Representation is a relation between a KANGER object and a carrier encoding some state of that object.

Examples:

```text
ByteBuffer represents persistent fields of a Rule
Map represents an external projection of a unit
Token/Leaf represent compiler structure
```

The representation can be recreated without creating a new semantic object. Serialized bytes, operational IDs and diagnostic text do not define semantic identity on their own.

### 6.5 Projection

Projection is not ordinary containment and not a second semantic object. It is the mapping of one object into the observable state of a specific space.

Canonical form:

```text
projection(object, space) -> observable state
```

A projection may include visibility, current value, deletion, restoration, promotion, query membership, current function result or provenance state. The projection is governed by the observing space and may differ between sibling Minds while the canonical object remains the same.

Examples:

```text
projection(Rule, child Mind)
    includes child-visible deletion/promotion/cause state

projection(TVariable, active Mind)
    includes current TValue or undefined state

projection(Function, active Mind)
    includes current arguments and applicable FValue

projection(TValue, query-local space)
    includes result membership without changing TValue identity
```

Projection is therefore a mapping between an object and a space, not a substitute for either one.

### 6.6 Governing axiom

> A KANGER object does not move between spaces. Its representations are created or reconstructed, and its projections become visible or cease to be visible in those spaces.

Consequences:

- `commit` does not physically move the semantic object; it merges the child's state into the parent's visibility domain and preserves or resolves canonical identity;
- `release` removes the child projection without deleting inherited canonical objects;
- hydration reconstructs a runtime representation of an existing durable identity;
- serialization creates a carrier representation rather than converting the object into bytes;
- a child Mind is not a copied database, but an overlay containing local objects and projections of inherited objects.

The axiom does not deny creation of genuinely new provisional objects in a child Mind. Such objects receive identity in the allocation domain, are visible first through the child projection, and may become visible to the parent after commit.

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

```text
constructed != canonicalized
canonicalized != hydrated
hydrated != published
deleted != physically removed
restored != newly created
```

---

## 8. Production-validated object matrix

`Persistent` means that defining state has a durable representation; it does not mean that every runtime projection is stored.

| Object family | Object class | Defining identity | Primary owning space | Lifecycle authority | Persistent | Canonicalized | Context-dependent |
|---|---|---|---|---|---|---|---|
| `Mind` | runtime and governing object | explicit Mind identity plus position in parent/child chain | User-owned root space or parent-owned child overlay | caller/User for root; parent transaction protocol for child | child itself no | no semantic factory canonicalization | intrinsically |
| `Rule` | canonical semantic object | normalized rule structure confirmed by RuleFactory | factory registry visible through Mind | RuleFactory and Mind overlay | yes | yes | projection state yes |
| `Term` | canonical semantic object with contextual subtypes | normalized typed value; contextual key for C/U terms | DictionaryFactory visible through Mind | DictionaryFactory and Mind overlay | yes | yes | C/U terms yes |
| `TVariable` | Rule-owned canonical semantic object | Rule plus rule-local name/index coordinates | Rule semantic space through TVariableFactory | Rule definition and TVariableFactory | yes | yes | definition Rule-dependent; value Mind-dependent |
| `Domain` | Rule-associated canonical semantic object and predicate occurrence | predicate, polarity, arity/arguments and Rule association under DomainFactory contract | DomainFactory; semantically used by Rule | DomainFactory and Mind overlay | yes | yes | current causes, solves and argument projection are Mind-dependent |
| `TValue` | contextual canonical semantic object | `(TVariable id, Term id)` | TValueFactory visible in a Mind | TValueFactory and Mind overlay | yes | yes | visibility, deletion and query membership depend on Mind |
| `Function` | canonical semantic definition of potential computation | name, arity, binding and normalized argument graph | FunctionFactory; semantically embedded in Rule structure | FunctionFactory and Mind overlay | yes | yes | current arguments/result are Mind projections |
| `FValue` | materialized computation object | Function plus ordered input stamp and registered result representation | FValueFactory visible in Mind | FValueFactory and transaction continuation lifecycle | yes | yes | applicability and visibility depend on active substitutions/Mind |
| `Solve` | structural runtime value object | predicate, arity and ordered argument identities; polarity participates in hash/representation and requires explicit semantic treatment | query, Domain or Cause-local runtime space | owning aggregate/runtime store | embedded only | no independent factory | yes when arguments resolve through Mind |
| `Cause` | provenance relation object | target Rule plus donor Solve | Rule/Domain provenance space | owning Rule/Domain provenance lifecycle | embedded in Rule persistence | structural deduplication, not independent factory | donor resolution depends on Mind |

### 8.1 Mind

`Mind` is an architectural object but not a semantic unit of knowledge. It establishes an active logical and transactional space. A child Mind is a provisional overlay, not a durable copy of a database.

```text
Mind identity != database identity
child Mind != persistent version
Mind ownership of factories != ownership of external storage
```

### 8.2 Rule

`Rule` is a canonical semantic object. Its operational ID names the registered representative, while sameness is established through normalized semantic structure. Visibility, deletion, restoration, promotion, causes and query-local usage are projections relative to a Mind.

### 8.3 Term

Ordinary Term identity is based on normalized typed content. C-variable and U-child terms include contextual coordinates. In particular:

```text
(parent C-variable, target Rule id) -> canonical U-child
```

### 8.4 TVariable

`TVariable` is the persistent definition of a substitution position owned by a Rule. Its current value exists separately as a TValue projection in an active Mind.

```text
variable definition != current value
Rule ownership != query membership
owner Mind != active execution Mind
undefined value != deleted variable
```

### 8.5 Domain

`Domain` is more than an arbitrary `Solve`: it is a registered predicate occurrence associated with a Rule and carrying persistent unit identity. Its stable definition includes predicate/polarity/arguments, Rule reference and substitution/abstraction properties.

The Rule does not lifecycle-own the canonical Domain merely because it contains it in its tree. The Rule semantically contains a reference to the Domain; DomainFactory owns registration and canonicalization.

Causes, solved TValue sets, calculated argument variants and exclusion state are not defining fields of Domain identity. They are projections stored in the observing Mind and keyed by the Domain plus the current converted argument list.

```text
Domain definition != current argument instantiation
Domain containment in Rule != lifecycle ownership
Domain causes/solves != Domain canonical identity
```

### 8.6 TValue

`TValue` materializes the pair `(TVariable, Term)`. It is not the TVariable itself and not merely the donor Term.

The same canonical TValue may participate in several runtime mappings:

- current-value projection of its TVariable;
- query-result projection;
- visible/deleted/restored projection of a Mind.

`setQuery()` publishes an existing TValue into query-local results; it does not create another substitution or commit state.

```text
TValue identity != query membership
TValue identity != current-selection status
(TVariable, Term) != either component alone
```

### 8.7 Function

`Function` is a definition of potential computation. Its identity includes name, arity, binding and normalized recursive argument structure. The transient slot at index `range` and current parameter values are execution projections, not part of the stable definition.

```text
Function definition != FValue result
argument graph != current argument values
result slot != canonical materialization
```

### 8.8 FValue

`FValue` is a materialized result of one Function under an ordered stamp of participating T-variable values. It preserves the distinction between the potential computation and an actual result.

Applicability is evaluated by comparing the Function and current substitutions with the stored stamp. The result Term is stored as part of the materialized record, while the applicability check deliberately focuses on Function plus stamp.

```text
Function + current stamp -> applicable FValue
FValue != transient result slot
result Term != input stamp
```

### 8.9 Solve

`Solve` is a structural runtime object describing one predicate-shaped variant through predicate ID, arity, polarity and ordered arguments. It is not independently registered as an `IUnit`; it is embedded in Domain, Cause and runtime collections.

Its equality contract protects structural deduplication of predicate-shaped variants. Because it has no independent factory or durable ID, its identity is value-like and local to the aggregate or runtime space that owns it.

### 8.10 Cause

`Cause` is a provenance relation object connecting a target Rule with a donor Solve. It does not own either the Rule or the donor's underlying semantic objects.

Its durable form is embedded in Rule persistence as `(ruleId, donor Solve)`. Resolution of the donor back to a Rule is a Mind-dependent lookup through RuleFactory.

```text
Cause owns neither endpoint
Cause identity = target Rule relation + donor structure
Cause persistence != independent canonical unit registration
```

---

## 9. Planned chapters

1. Scope, terminology and object criteria.
2. Object spaces and boundaries.
3. Identity model.
4. Relations, ownership and containment.
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

The next revision validates the factory ecosystem and storage object families, then formalizes object-space boundaries and transition vocabulary for:

- construction and canonicalization;
- hydration and publication;
- child projection, commit and release;
- deletion, restoration and resurrection;
- persistence representation and physical reclamation.

Each classification must remain grounded in current production code and qualified contracts. Philosophical interpretation remains outside the normative Object Model and will use the same canonical vocabulary in the monograph.
