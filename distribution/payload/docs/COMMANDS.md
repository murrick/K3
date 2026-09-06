# KANGER 3.7.0 — Command Processor Manual

This manual describes the customer-visible KANGER Console command processor shipped with KANGER 3.7.0. It is the reference for command grammar, command-side lifecycle semantics, diagnostics, and the canonical `status` projection.

For installation, update, account provisioning, and host configuration, see `../README.md`. For Browser UI navigation and the TECH panel, see `UI_CONSOLE.md`.

The running command registry and parser are authoritative for accepted syntax. The built-in `help` command is generated from that same registry.

---

## 1. Command processor model

The Console accepts two kinds of input:

1. **KANGER language expressions** — lines whose first non-space character is one of `!`, `?`, `+`, `-`, or `=`. These are sent to the logical processor as Core language, not parsed as Console commands.
2. **Console commands** — lifecycle, navigation, inspection, persistence, source, and diagnostic operations described in this manual.

The command parser performs lexical and grammar canonicalization. Runtime existence checks, storage/source legality, transaction preconditions, and semantic qualification are enforced by the corresponding runtime boundaries after parsing.

### 1.1 Prefix resolution

Command families and their keywords share a minimum-unique-prefix namespace.

- An exact spelling wins over a prefix interpretation.
- An unambiguous prefix is accepted.
- An ambiguous prefix is rejected rather than guessed.
- Extra arguments are rejected.
- Missing required arguments are rejected.
- Numeric ids and indexes must be non-negative integers.

Examples:

```text
transaction start
trans sta
start
```

all identify the canonical transaction-start operation when the abbreviations are unambiguous.

Do not depend on an abbreviation that could become ambiguous when the command surface grows. Customer automation should prefer the complete canonical spelling.

### 1.2 Canonical aliases

The following short top-level aliases are intentionally supported:

```text
start       -> transaction start
commit      -> transaction commit
rollback    -> transaction rollback
squash      -> transaction squash

use <name>      -> storage use <name>
close           -> storage close
drop <name>     -> storage drop <name>
reindex <name>  -> storage reindex <name>
```

### 1.3 Confirmation boundary

The Console asks for explicit confirmation before these destructive or overwrite operations:

- `erase`;
- `storage drop <name>` / `drop <name>`;
- `delete <source>`;
- `put <source>` when the named source already exists.

Transaction rollback and storage reindex are not routed through this confirmation dialogue; their lifecycle preconditions are enforced directly by the runtime.

---

## 2. Quick command map

| Area | Canonical syntax | Purpose |
| --- | --- | --- |
| Rules | `rule`, `rule <id>`, `rule all`, `rule produced`, `rule level [<n>]`, `rule tree <id>`, `rule comment ...` | Inspect rule state, provenance, and comments |
| Functions | `functions`, `function <id>`, `function source <id>` | Inspect registered functions and source |
| Base | `base`, `base predicates`, `base predicate <id|name>`, `base tree <statement-id>` | Inspect primary/base statements |
| Values | `values`, `values order <field> [asc|desc] [, ...]` | Inspect and order the last Values rowset |
| Solutions | `solutions`, `solution <id>`, `solution tree <id>` | Inspect complete solutions and proof trees |
| Hypotheses | `when`, `when accept <index>` | Inspect or accept a hypothesis from the current rowset |
| Transactions | `transaction`, `transaction start|commit|rollback|squash` | Inspect and manage explicit user transaction levels |
| Sources | `get [<source>]`, `put <source>`, `delete [<source>]` | List/load/save/delete server-side source files |
| Storage | `storage`, `storage use|close|drop|reindex ...` | Inspect and manage persistent storage |
| Status | `status [core [objects|transaction|levels]|storage|session|runtime]` | Cheap canonical product telemetry |
| Workspace | `erase` | Clear the current workspace through qualified runtime semantics |
| Help/session | `help`, `quit` | Render canonical help or end the session |

---

## 3. Rules

### `rule`

Shows the current primary rule context.

```text
rule
```

`rule` is the singular status form. The plural collection form is `rules`, which resolves to `rule all`.

### `rule <id>`

Shows one rule by runtime rule id.

```text
rule 13
```

The id is an infrastructure/runtime id, not a line number in a source file.

### `rule all`

Shows the complete current rule collection.

```text
rule all
rules
```

### `rule produced`

Shows the produced/derived rule collection for the current logical state.

```text
rule produced
```

### `rule level [<n>]`

Groups rules by published user transaction level, or restricts the projection to one level.

```text
rule level
rule level 1
```

The level is a user transaction level `U0`, `U1`, ...; it is not a storage generation number.

### `rule tree <id>`

Shows provenance/proof structure for a rule.

```text
rule tree 13
```

### `rule comment <id>`

Reads the comment associated with one rule.

### `rule comment <id> <text...>`

Sets the rule comment text. This is a state-changing command.

---

## 4. Functions

### `functions`

Lists the current function collection.

### `function <id>`

Shows one function by id.

### `function source <id>`

Shows the source associated with one function.

`function show ...` is intentionally not canonical syntax.

---

## 5. Base statements

The **base** projection is the unambiguous set of base/primary statements in the current logical state. It is distinct from the complete set of derived materializations.

### `base`

Shows the current base statements.

### `base predicates`

Shows the predicate collection used by the base projection.

### `base predicate <id|name>`

Shows base statements for one predicate, addressed either by numeric id or predicate name.

### `base tree <statement-id>`

Shows provenance for one base statement.

---

## 6. Values

`Values` is the tabular projection of substitutions produced by the most recently processed logical operation. It is not itself the proof graph and may be empty even when other diagnostic material exists.

### `values`

Shows the complete current Values rowset using its default ordering.

### `values order <field> [asc|desc] [, <field> [asc|desc]]...`

Shows the current rowset using invocation-local multi-key ordering.

Examples:

```text
values order name
values order city asc, age desc
```

Rules:

- at least one field is required;
- keys are separated by commas;
- each key may contain only a field and optional `asc`/`desc` direction;
- default direction is ascending;
- ordering changes presentation of this invocation; it does not change logical semantics.

---

## 7. Solutions

`Solutions` contains the complete current solution set of the last logical operation. Solution ids are actual runtime rule ids.

### `solutions`

Shows the complete current Solutions collection.

### `solution <id>`

Shows one solution by id.

### `solution tree <id>`

Shows the proof/provenance tree of one solution.

`solution` without an id and `solution show ...` are intentionally rejected.

---

## 8. Hypotheses — `when`

The `when` family operates on the hypothesis rowset of the most recent query.

### `when`

Shows the current hypotheses.

### `when accept <index>`

Accepts one hypothesis using its **zero-based row index** in the current hypothesis rowset and applies the corresponding state-changing operation.

```text
when accept 0
```

A hypothesis is advisory until it is explicitly accepted. Merely displaying `when` does not modify the program.

---

## 9. User transactions

KANGER exposes explicit user transaction levels `U0`, `U1`, ... . A `Mind` represents one concrete logical version; transaction lifecycle and physical storage lifecycle are separate state machines.

### `transaction`

Shows the current transaction level/status.

### `transaction start`

Creates a child user transaction above the current level.

```text
transaction start
start
```

A successful start changes the current level from `Un` to `U(n+1)`.

### `transaction commit`

```text
transaction commit
commit
```

At a child level (`U1+`), commit atomically merges the current child delta into its parent after qualification. On failure, the parent must not receive a partial merge.

At root `U0`, the canonical command acts as a qualified root checkpoint. With persistent storage open, that is the durability boundary for the qualified root state.

### `transaction rollback`

```text
transaction rollback
rollback
```

Discards the current explicit child transaction and returns to its parent. Rollback does not use the Browser destructive-confirmation dialogue.

### `transaction squash`

```text
transaction squash
squash
```

Collapses explicit transaction history above `U0` into one `U1` while leaving `U0` unchanged. This is a transaction-topology operation, not a physical-storage compaction command.

### Transaction compatibility and quiescence

The diagnostic compatibility state describes whether the current published transaction topology has been qualified by the relevant lifecycle path. `UNQUALIFIED` is a state description, not by itself an error or corruption signal.

`quiescent=true` is stronger than `level=0`: it also requires the absence of pending child reservations that would make the published root busy.

---

## 10. Server-side sources

Sources are files in the authenticated user's source workspace. Source names are validated and canonicalized by the Server; the Browser does not receive arbitrary filesystem access.

### `get`

Lists available server-side sources. This form is read-only.

### `get <source>`

Loads the named source and compiles it into the current workspace using the canonical source/compile lifecycle.

```text
get sample.kanger
```

This is state-changing because compilation can replace logical source state.

### `put <source>`

Persists the current canonical source projection to the named server-side source.

```text
put sample.kanger
```

Creating a new source does not require overwrite confirmation. Replacing an existing named source does.

### `delete`

Lists available server-side sources. This form is read-only.

### `delete <source>`

Deletes the named source after explicit confirmation.

Source operations are confined to the user's configured `sources.dir`; they are not a general filesystem command interface.

---

## 11. Persistent storage

A storage name identifies a persistent logical database/generation managed by the configured storage backend. Only one storage is current for a user runtime at a time.

### `storage`

Shows available storage and the current selection. Alias family spelling `use` with no name resolves to this status form.

### `storage use <name>`

```text
storage use natives
use natives
```

Opens the named storage; when allowed by the backend/lifecycle, a new named storage can be created. Opening storage publishes the canonical schema bases into the root logical context and qualifies the resulting state before it becomes active.

A storage candidate that fails physical or semantic qualification is rejected and must not remain the active generation or published factory binding.

### `storage close`

```text
storage close
close
```

Closes the current physical storage using the canonical rebase/settlement path. Physical close and user transaction topology are distinct concerns; the runtime preserves the qualified logical transaction topology required by the lifecycle contract rather than treating close as an implicit `erase`.

### `storage drop <name>`

```text
storage drop olddb
```

Permanently removes the explicitly named storage after confirmation. The active logical view is detached before physical removal when the target is current.

### `storage reindex <name>`

```text
storage reindex natives
```

Rebuilds the explicitly named storage using the storage lifecycle. It requires a legal/quiescent transaction topology. Reindex is not protected by the generic confirmation dialogue, so treat it as an explicit administrative data-maintenance command.

---

## 12. Canonical `status`

`status` is the canonical read-only product telemetry command. It is deliberately designed to be cheap and non-invasive.

Hard invariants of STATUS:

- read-only;
- no logical hydration merely to count or display objects;
- no semantic qualification triggered for observation;
- no full graph/database scan;
- no lazy materialization;
- no transaction mutation;
- no storage lifecycle mutation;
- no namespace enumeration merely for telemetry;
- when a safe metric is unavailable, report `unavailable` rather than invent `0`.

### 12.1 Grammar

```text
status
status core
status core objects
status core transaction
status core levels
status storage
status session
status runtime
```

Unique prefixes are accepted by the parser, but complete spelling is recommended in automation.

### 12.2 `status` — root projection

The root form is intentionally compact:

```text
core.transaction.level=<n>
core.transaction.compatibility=<state>
storage.current=<name|none>
session.user=<id>
runtime.version=<version>
runtime.java=<java-version>
```

### 12.3 `status core`

Fields:

| Field | Meaning |
| --- | --- |
| `transaction.level` | Current published user transaction level (`0` = `U0`) |
| `transaction.compatibility` | Current transaction-compatibility observation; `UNQUALIFIED` is not by itself a failure |
| `transaction.quiescent` | True only when the current/root topology has no outstanding child reservations and is otherwise quiescent |
| `transaction.current.pending.children` | Pending child reservations owned by the current Mind |
| `transaction.root.pending.children` | Pending child reservations owned by the root Mind |
| `objects` | `unavailable` in STATUS schema v1; object counting would violate the cheap/non-hydrating telemetry contract |

### 12.4 `status core transaction`

Focused transaction projection:

```text
level=<n>
compatibility=<state>
quiescent=<true|false>
current.pending.children=<n>
root.pending.children=<n>
```

### 12.5 `status core levels`

```text
current=<user-transaction-level>
mind=<current-mind-id>
root.mind=<root-mind-id>
```

`current` is a transaction depth. `mind` and `root.mind` are runtime Mind identities; they are different dimensions and should not be compared as if they were the same counter.

### 12.6 `status core objects`

STATUS schema v1 returns:

```text
count=unavailable
```

This is intentional. It means the canonical cheap telemetry layer does not expose a safe constant-time object count, not that the workspace contains zero objects.

### 12.7 `status storage`

Fields:

| Field | Meaning |
| --- | --- |
| `current` | Current storage name, or `none` |
| `state` | `open` or `closed` |
| `backend` | Active storage backend description, when available |
| `bases` | Number of persistent **schema bases** opened by KANGER; not a count of customer databases |
| `records` | Total persistent records across the schema bases exposed by the storage telemetry |
| `physical.bytes` | Cheap physical generation-size metric in bytes |
| `wal.pending.bases` | Number of schema bases with pending recovery/WAL work reported by the backend |
| `cache.used.bytes` | Current storage-cache memory in use |
| `cache.max.bytes` | Configured maximum storage-cache memory |
| `cache.entries` | Number of cached entries |
| `cache.hits` | Cache hit counter |
| `cache.misses` | Cache miss counter |
| `cache.evictions` | Cache eviction counter |

The current KANGER schema opens ten persistent schema bases: dictionary/terms, domains, functions, function values, predicates, rules, comments, term values, term variables, and library data. Consequently a healthy open database commonly reports `bases=10`; that does **not** mean ten user databases exist.

Example:

```text
current=natives
state=open
backend=DUMB data model
bases=10
records=598
physical.bytes=150217
wal.pending.bases=0
...
```

When storage is closed, storage-specific metrics may legitimately be `unavailable`.

### 12.8 `status session`

```text
user=<numeric-user-id>
mind=<current-mind-id>
user.dir=<user-workspace>
database.dir=<database-directory>
sources.dir=<source-directory>
```

In the standard installed Server topology, the default user workspace is under:

```text
/var/lib/kanger/KANGER/<user-id>/
```

with default subdirectories `DB/` and `SRC/` unless overridden by the per-user configuration.

### 12.9 `status runtime`

Fields:

| Field | Meaning |
| --- | --- |
| `version` | KANGER product/core version |
| `source.branch` | Build source branch metadata, when present |
| `build.date` | Build timestamp metadata, when present |
| `java` | Java runtime version |
| `jvm` | JVM implementation/name |
| `uptime.ms` | Server JVM uptime in milliseconds |
| `heap.used.bytes` | Current used heap |
| `heap.committed.bytes` | Heap committed to the JVM |
| `heap.max.bytes` | Maximum heap reported by the JVM |
| `os` | Operating-system name |
| `arch` | Runtime architecture |

The Browser TECH panel formats some of these raw values for readability (KiB/MiB, uptime text, badges) but the canonical semantics are defined here.

---

## 13. `erase`

```text
erase
```

Clears the current workspace through the qualified runtime semantics after explicit confirmation. When persistent storage is active, the confirmation explicitly warns that current database contents are also affected.

`erase` is not equivalent to deleting a source file, dropping a named storage, or rolling back one child transaction.

---

## 14. `help`

```text
help
```

Renders canonical command help directly from the live command registry. This is the fastest way to verify syntax for the exact installed build.

The built-in help is syntax-oriented; this manual supplies the longer lifecycle and operational semantics.

---

## 15. `quit`

```text
quit
```

Terminates the current authenticated KANGER session through the canonical session lifecycle. It is not a Server shutdown command.

---

## 16. Errors and rejected operations

A rejected command should be interpreted at the boundary that rejected it:

- **parse/grammar error** — command spelling, ambiguity, missing/extra argument, invalid id/index shape;
- **lifecycle rejection** — command is syntactically valid but illegal in the current transaction/storage/source state;
- **semantic rejection** — compilation/query/storage qualification found inconsistent or corrupt semantic state;
- **storage error** — physical storage operation failed or the named generation does not exist;
- **confirmation cancellation** — destructive operation was not authorized.

Canonical failures are contained at the Server boundary; a failed operation must not be assumed to have partially succeeded. In particular, rejected storage publication and rejected transaction settlement are designed to preserve the previously published usable state.

When diagnosing a failure, use `status`, the Browser TECH panel, and the operation error/diagnostic output together. Do not infer corruption from one neutral state label such as `UNQUALIFIED` or from one `unavailable` metric.

---

## 17. Automation guidance

For scripts and support procedures:

- use full canonical spellings rather than shortest prefixes;
- do not parse Browser presentation text when a structured API/SDK surface exists;
- treat ids as runtime/infrastructure ids unless the command explicitly defines another identity;
- do not infer object counts from `lastId`-style values;
- treat `unavailable` as an explicit absence of a safe metric;
- expect destructive commands to require interactive confirmation unless the surrounding supported interface defines a separate explicit automation contract;
- verify `status` after lifecycle work when operational evidence is required.

---

## 18. Related documentation

- `../README.md` — installation, update, Server configuration, account provisioning, backup boundary, host troubleshooting.
- `UI_CONSOLE.md` — Browser UI, workspace panels, operation model, and TECH presentation.

This document defines the KANGER 3.7.0 distribution command surface. If a future release changes command grammar or command lifecycle semantics, the distribution reference for that release must change with it.