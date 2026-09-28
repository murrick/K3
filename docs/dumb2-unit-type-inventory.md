# DUMB2 UnitType persistence inventory

Checkpoint basis: feature branch `feat/3.8.0-dumb2-descriptor-metamodel` after
`beb56a6c3a274fade21dc1659d98023d1fcc2a4a`.

This inventory separates the historical `UnitType` enum from the actual
top-level physical persistence boundary. DUMB2 must preserve semantics, not
blindly reproduce every enum member.

## Top-level semantic units

The historical `Sapato.newInstance(UnitType)` storage reader can materialize
exactly these semantic unit kinds:

- `TERM` -> `Term`
- `RULE` -> `Rule`
- `COMMENT` -> `Comment`
- `DOMAIN` -> `CachedDomain` / `Domain`
- `FVALUE` -> `FValue`
- `TVALUE` -> `TValue`
- `FUNCTION` -> `Function`
- `PREDICATE` -> `Predicate`
- `TVARIABLE` -> `TVariable`
- `SYSOP` -> `Operation` or the UDF `Operation` subclass

DUMB2 has an explicit structural adapter for every one of these kinds.
No top-level semantic `UnitType` remains without an adapter.

## Physical non-unit payloads

`LONG` and `LONGS` are not semantic `IUnit` classes. Historical
`Sapato.pack()` uses these enum values as physical discriminators for a
single `Long` and a `Collection<Long>`.

DUMB2 integration must represent these as neutral structural payloads rather
than adding fake KANGER semantic adapters.

## Embedded structural values

These implement KANGER interfaces and expose a `UnitType`, but they are not
historical top-level Sapato records:

- `ARGUMENT` -> embedded `Argument-v1`
- `ARGLIST` -> represented by enclosing list fields such as
  `LIST<Argument-v1>`; no independent top-level record is required
- `CAUSE` -> embedded `Cause-v1`

Their DUMB2 representation is therefore deliberately structural/embedded.

## Runtime-only value

`HYPOTHESE` is inference/session state held by `HypothesisStore`.
It is not a historical durable Sapato unit and is outside DUMB2 M1
persistent semantic state.

## Sentinel

`UNKNOWN` has no persistent representation.

## Integration conclusion

The semantic-adapter phase is complete for the actual durable unit surface.
The next integration task is to remove the remaining legacy `Sapato.pack()`
wire format from DUMB2 `ContextBase` and route physical records through:

`Context manifest/type registry -> PersistentRecordCodec -> structural payload
-> KANGER adapter (only when semantic hydration is requested)`.

The ID/hash/next/typeCode envelope must be available without semantic object
hydration. `LONG` / `LONGS` must use generic structural type definitions,
not KANGER adapters.
