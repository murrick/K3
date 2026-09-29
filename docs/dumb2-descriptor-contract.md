# DUMB 2.0 structural descriptor contract

Status: first executable metamodel slice for the self-describing DUMB 2.0 representation.

## Boundary

A descriptor describes physical structure only. It contains no Java class names,
`serialVersionUID`, reflection metadata, callbacks, KANGER interfaces or enum ordinals.
KANGER semantic materialization remains an adapter responsibility.

The first metamodel is intentionally finite:

- `BOOL`
- `INT32`
- `INT64`
- `FLOAT64`
- `UTF8`
- `BYTES`
- `SELF` for recursive values of the current named `STRUCT`
- `REF<schema>`
- `LIST<T>`
- named `STRUCT`
- named `ENUM`
- `VARIANT` selected by a previously decoded field
- `CONDITIONAL` selected by a bounded predicate over a previously decoded field

`VARIANT` and `CONDITIONAL` may only depend on fields that precede them in the
same `STRUCT`. This keeps generic decoding single-pass and prevents the descriptor
model from becoming an executable language.

`SELF` is the only recursive primitive in the initial vocabulary. It resolves to the nearest enclosing named `STRUCT`; this is sufficient for the current recursive `Term` payload without introducing a general symbol table or executable type resolver.

`REF<schema>` is typed because DUMB 2.0 operational IDs are schema-local. A bare
`INT64` is not sufficient to identify a referenced physical namespace.

## Scalar payload contract

The generic value codec built on this metamodel will use explicit representation:

- byte order: little-endian;
- `BOOL`: one byte, values `0` or `1`;
- `INT32`: signed 32-bit integer;
- `INT64`: signed 64-bit integer;
- `FLOAT64`: IEEE-754 binary64;
- `UTF8`: unsigned 32-bit byte length followed by UTF-8 bytes;
- `BYTES`: unsigned 32-bit byte length followed by bytes;
- `LIST<T>`: unsigned 32-bit element count followed by elements;
- `ENUM`: explicit stable symbolic code supplied by the descriptor/manifest, never a runtime enum ordinal.

The current Java implementation of the descriptor-definition codec uses its own
versioned `K3DS` envelope. Its magic is literal ASCII and all numeric metadata after the magic is little-endian, matching the generic value contract. Its kind/operator tags are explicit constants and are
not derived from Java enum order. The future Context manifest may embed these
canonical descriptor bytes or carry the same logical information in a larger
versioned envelope.

## Streaming rules

A `STRUCT` is decoded in field order. Field names are metadata and adapter keys;
they do not change record identity by themselves.

A `VARIANT` names a preceding discriminator field and maps symbolic tags to one
of a finite set of descriptors.

A `CONDITIONAL` names a preceding field and applies one of a deliberately small
set of predicates. The initial metamodel contains integer equality and integer
greater-than because they are sufficient for the current inventory. New
operators require an explicit format evolution decision; arbitrary expressions
are not allowed.

## Persistent identity rule

No runtime `enum.ordinal()` is a persistent code. This applies both to the
record-level type registry and to nested enums such as `ArgumentType`,
`DataType`, `LibMode` and `FunctionBinding`.

The same rule applies to persistent/structural identity algorithms: runtime enum
ordering must not silently change a stored or canonical hash. Stable semantic
or descriptor codes must be used where enum identity participates in hashing.

## Deliberately not in this slice

This slice does not change `Sapato`, old DUMB, `ContextBase`, `.context`, or the
runtime ServiceLoader surface. It now includes a neutral immutable `StructuralValue`
and a generic descriptor-driven little-endian value codec. Qualification covers a
TValue-shaped record, an Argument-shaped `ENUM + VARIANT + REF` record, and a
recursive Term-shaped `SELF + VARIANT + CONDITIONAL` record without a KANGER-specific
binary switch.

The first KANGER adapter boundary is now executable for `TValue`:

`live TValue -> TValueAdapter -> StructuralValue -> generic codec -> bytes`

and the reverse path resolves the descriptor's typed `dictionary` and `tvariables`
references through the supplied Mind. The generic descriptor/value packages contain
no `TValue`, `UnitType` or KANGER factory switch. The adapter does not call the legacy
`pack()/apply()` binary format and does not register the restored unit in
`TValueFactory`; canonicalization/publication remain Mind/factory lifecycle concerns.

The next slice is the Context-local type registry/manifest boundary: assign an
immutable typeCode to `(typeName, descriptor)` and prove that a record can be decoded
from `typeCode + manifest + bytes` without a KANGER-specific binary switch.


## Context-local type registry

The first registry layer is executable. A positive `typeCode` is allocated only
inside one Context registry. Re-registering the same `(typeName, descriptor)` is
idempotent. A different descriptor under the same `typeName` receives a new code,
so old and new layouts may coexist.

A code loaded from a manifest is immutable: the same code may only be installed
again with the exact same symbolic type and descriptor. Registry enumeration is
canonicalized by ascending code so manifest serialization does not depend on
registration order. Separate registries may assign the same numeric code to
different definitions; the number has no meaning outside its Context.


## Context manifest publication

The old fixed 28-byte ContextId sidecar has been replaced by a versioned Context
manifest. The `.context` file now persists the stable ContextId and the complete
Context-local type registry, including canonical DescriptorBinaryCodec bytes. The
whole manifest is protected by CRC32.

Registry publication uses forced temporary bytes followed by atomic same-filesystem
replacement. ContextId never changes when the registry grows. ContextStore publishes
a registered descriptor before returning the type definition to a caller, establishing
the required ordering boundary for future records that reference its typeCode.

Reopen reconstructs the registry from the manifest and rejects invalid codes,
descriptors, checksums or framing. Descriptor codec incompatibility is reported as
storage-format incompatibility rather than silently adopting a Java runtime layout.

A qualification slice also proves the generic read direction:

`typeCode + reopened manifest + payload bytes -> Descriptor -> StructuralValue`

The decode step contains no switch on KANGER UnitType or Java unit classes.
