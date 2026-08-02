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
