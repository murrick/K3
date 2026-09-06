# KANGER 3.7.0 — Browser UI and Server Console Manual

This manual describes the customer-visible KANGER 3.7.0 Browser workspace: the semantic panels, Dialogue/Source workspace, result panels, command composition behavior, operation containment, and the TECH panel.

For installation, Server/account administration, and configuration, see `../README.md`. For exact Console command grammar and command semantics, see `COMMANDS.md`.

---

## 1. What the Browser UI is

The Browser UI is an authenticated presentation and workspace client for one KANGER Server user session. It is not a second implementation of the command language and it is not an administrative shell over the host filesystem.

The Browser has three deliberately separated responsibilities:

1. **Presentation** — render trusted text/DOM and arrange the workspace.
2. **Composition** — help the user compose canonical Console commands or KANGER expressions into the input line.
3. **Transport containment** — send authenticated operations through the parent-owned transport/operation boundary.

Canonical command parsing and semantic execution remain Server-owned.

This distinction matters when diagnosing the system: what the Browser displays is a projection of Server/runtime state, not an independent source of KANGER semantics.

---

## 2. Login and session

The Browser authenticates with the KANGER user credentials created by the configured account-provisioning topology.

- In `TRUSTED` registration policy, an operator creates ACTIVE users through `kanger-admin`; public self-registration is unavailable.
- In `EMAIL_VERIFIED` policy, public registration is available and an account becomes ACTIVE only after successful e-mail confirmation.

The Browser obtains the public registration capability from the Server version response instead of guessing registration policy from UI behavior.

An authenticated Browser session owns a bearer token at the parent Console boundary. Presentation helpers and the TECH adapter do not independently own or expose that bearer.

`quit` ends the KANGER user session. Closing or refreshing a Browser tab is not the same operation as shutting down the KANGER Server.

---

## 3. Workspace overview

The normal authenticated workspace is organized around these visible areas:

```text
+----------------------+-------------------------------+
| Base / Statements    | Dialogue or Source            |
| Functions            |                               |
+----------------------+-------------------------------+
| Values | Solutions | Hypotheses | Log                |
+------------------------------------------------------+
| Console input                                         |
+------------------------------------------------------+
|                                             TECH >    |
+------------------------------------------------------+
```

Exact proportions adapt to the viewport and user resizing. When TECH is open it occupies one quarter of the viewport width (`25vw`) and the main workspace remains available alongside it.

---

## 4. Base / Statements

The **Base / Statements** area presents the current base/primary semantic projection grouped by predicate.

Typical interactions are composition aids, not immediate semantic execution:

- clicking a predicate name composes `predicateName(` into the Console input;
- clicking a statement composes its statement text;
- the `tree` action beside a statement composes `base tree <statement-id>`;
- the predicate information marker shows current predicate/statement details without executing a hidden mutation.

The Browser does not use a click on a semantic row as authority to change the KANGER program. It composes canonical text into the operator input, where the user can inspect/edit and explicitly execute it.

For the exact `base` command semantics, see `COMMANDS.md`.

---

## 5. Functions

The **Functions** area shows registered functions.

- clicking a function name composes `functionName(` into the input line;
- the `source` action composes `function source <id>`.

Function inspection is a Console projection; user-defined function semantics themselves belong to the KANGER language/runtime rather than to Browser presentation.

---

## 6. Dialogue

**Dialogue** is the primary command/query interaction view.

The input line accepts:

- KANGER language expressions beginning with `!`, `?`, `+`, `-`, or `=`;
- Console commands such as `status`, `transaction`, `storage`, `get`, `put`, and `help`.

Pressing Enter without Ctrl submits the current line. The Browser records operator dialogue/history presentation and sends the request through the canonical operation/transport boundary.

The current Server command syntax is available with:

```text
help
```

Full semantics are in `COMMANDS.md`.

### 6.1 Dialogue history

Previously displayed command requests can be clicked to restore their text to the input line. This is a composition convenience; clicking an old request does not automatically re-run it.

The Browser history presentation must not be treated as the canonical data model or parsed as machine telemetry. Structured runtime data such as TECH STATUS uses its own structured path.

---

## 7. Source view

The central workspace can switch from **Dialogue** to **Source**.

Source view provides the text editor used for KANGER source editing and source-oriented workflows. It is a presentation/editor surface; loading, compiling, saving, overwriting, and deleting server-side sources are still controlled by canonical commands and Server boundaries.

Relevant commands are:

```text
get
get <source>
put <source>
delete
delete <source>
```

The Browser may provide source selection/composition controls, but source names and filesystem confinement are validated by the Server. A Browser client does not receive arbitrary host filesystem access.

The editor can use local Browser file interaction where the current UI explicitly provides it; such a local file is distinct from a server-side KANGER source until an explicit supported source operation is performed.

---

## 8. Values

The **Values** panel displays the Values rowset of the most recent logical operation.

Cells in result rows can be clicked to compose their textual value into the Console input. Header and row-identity cells are not treated as value-composition cells.

Ordering is a presentation of the current rowset and can be requested with:

```text
values order <field> [asc|desc] [, ...]
```

Values are substitutions/projected result data; they are not the complete proof/provenance graph.

---

## 9. Solutions

The **Solutions** panel displays the complete current Solutions set.

- clicking a solution composes its expression into the input;
- the `tree` action composes `solution tree <id>`.

A solution id is a runtime rule id. See `COMMANDS.md` for exact `solutions`, `solution <id>`, and `solution tree <id>` semantics.

---

## 10. Hypotheses

The **Hypotheses** panel displays the current hypothesis rowset produced by an indeterminate query when hypothesis processing is available.

A hypothesis is not automatically accepted knowledge.

The `⊕` action beside a hypothesis composes:

```text
when accept <index>
```

where the index is the zero-based row index of the current hypothesis set.

Clicking the hypothesis expression itself composes a corresponding expression into the input; this remains composition, not an implicit acceptance mutation.

---

## 11. Log

The **Log** panel presents operation/runtime diagnostic output exposed to the Browser.

Use it together with:

- the Dialogue response;
- canonical `status`;
- TECH;
- Server journal/health checks when performing host-level diagnosis.

The Log is diagnostic presentation, not a substitute for Server lifecycle state. Do not infer durable success solely because text appeared in the Log; use the relevant operation result/status boundary.

---

## 12. Storage and transaction indicators

The Browser header/workspace displays current storage and transaction context.

Clicking the current storage indicator composes either:

```text
storage
```

or, when a logical storage name is active:

```text
storage use <current-name>
```

Clicking the transaction indicator composes:

```text
transaction
```

These are deliberate composition actions. They do not silently change storage or transaction state.

---

## 13. Browser operation model

The Browser operation protocol separates state-changing operator work from observational reads.

The TECH panel exposes the relevant Browser-local observations as:

- **generation** — committed Browser operation generation;
- **operation** — current active mutating operation or `idle`;
- **snapshot** — committed/current Browser workspace snapshot identity.

A state-changing operation is serialized through the operation boundary. The Browser does not start a second competing mutation while an active mutation owns the operation slot.

Read-only telemetry must not manufacture a semantic operation merely to observe state. In particular, opening TECH uses a structured read path and must not become an operator operation or increment the Browser generation by itself.

---

## 14. Confirmation dialogues

The Server determines when canonical command ingress requires destructive/overwrite confirmation. The Browser renders that confirmation and continues only after explicit operator approval.

Current confirmation-protected Console operations are:

- `erase`;
- `storage drop <name>`;
- `delete <source>`;
- overwrite of an existing source by `put <source>`.

The presence or appearance of a Browser confirmation is not itself the semantic authority; Server command ingress owns the classification.

---

# 15. TECH panel

TECH is the compact technical/administrative context panel on the right side of the Browser workspace.

Open or close it with the `TECH` toggle. It can also be activated by keyboard with Enter or Space when the toggle has focus.

When open, TECH occupies one quarter of the Browser viewport. It contains two distinct telemetry domains separated visually by a boundary:

```text
Browser-local
  Dialogue
  Workspace
  Authority

Canonical Server STATUS
  Canonical STATUS
  Core
  Storage
  Session
  Runtime
```

Do not conflate these domains. The first three sections describe Browser client state/authority. The latter sections are a formatted projection of canonical Server `status.schema=1` telemetry.

---

## 16. TECH — Dialogue

The Browser-local **Dialogue** section contains:

### Parser

```text
kanger-command / server
```

This states that canonical command parsing is Server-owned by the KANGER command module. TECH does not parse Console text locally.

### Session

```text
parent-owned
```

The authenticated transport/bearer belongs to the parent Console boundary. The presentation/TECH code does not own a second bearer.

### Generation

The current Browser operation generation. It changes when committed operator work advances the Browser workspace generation.

Opening TECH itself must not advance this number.

### Operation

Either:

```text
idle
```

or the currently active Browser mutating operation id/name.

If an ordinary mutation is in progress, this is the Browser-side operation slot, not the Server JVM thread count.

### Snapshot

The Browser operation protocol's last committed snapshot id and, where applicable, a current in-flight snapshot transition.

A snapshot is Browser workspace state, not a DUMB storage generation.

---

## 17. TECH — Workspace

The Browser-local **Workspace** section contains:

### Storage

The Browser workspace's currently known logical storage name, or `unused` when no storage is active.

### Physical

A compact Browser-side physical storage observation, currently including whether a physical generation is present and the known WAL segment observation.

This is intentionally a lightweight workspace projection. The authoritative detailed storage metrics are in the canonical **Storage** section below it.

---

## 18. TECH — Authority

The Browser-local **Authority** section documents two security/presentation boundaries:

### Rendering

```text
trusted text/DOM
```

The Browser presentation layer renders through the qualified trusted-text/typed-DOM boundary. Semantic payloads are not treated as arbitrary executable HTML authority.

### Network

```text
parent containment
```

Child presentation helpers operate through the parent network/transport boundary rather than creating independent authenticated network authority.

These lines are architecture diagnostics: they describe which Browser layer owns rendering/network behavior.

---

## 19. TECH — Canonical STATUS

Opening TECH triggers one authenticated **structured read** of canonical Server STATUS through the parent transport boundary.

The request is not entered into operator Dialogue history, is not Console-text parsing in the Browser, and is not polling.

Lifecycle:

- TECH closed: no STATUS read is issued merely because the panel is closed;
- open TECH: one fresh STATUS read;
- close TECH: no read;
- reopen TECH: one fresh read;
- no periodic polling.

The panel renders `status.schema=1`.

### Badge states

Typical badge values:

- `LOADING` — a fresh structured read is in progress;
- `CURRENT` — the snapshot was accepted and rendered;
- `UNAVAILABLE` — the read failed or did not contain a valid canonical schema-1 snapshot.

When the snapshot is current, the low-level `Source` diagnostic row is hidden. On failure it becomes visible with the failure description.

The displayed **Generation** is the Browser operation generation associated with the rendered observation; it is not the Server storage generation.

---

## 20. TECH — Core

The Core section reformats canonical `status core` / `status core levels` data.

### Transaction

Displayed as:

```text
U<n>
```

For example `U0` or `U1`.

### Compatibility

The canonical transaction compatibility observation. `UNQUALIFIED` is not automatically an error; see `COMMANDS.md` for the exact semantics.

### State

TECH maps canonical `transaction.quiescent` to:

- `QUIESCENT` when true;
- `ACTIVE` when false;
- `unavailable` when the canonical metric is absent.

### Mind

```text
current <mind-id> · root <root-mind-id>
```

### Pending

```text
current <count> · root <count>
```

These are pending child reservations for the current/root Minds.

### Objects

STATUS schema v1 normally displays `unavailable`. This is intentional; the canonical STATUS contract refuses to perform an expensive/hydrating object enumeration just to populate telemetry.

---

## 21. TECH — Storage

The Storage badge displays the canonical storage state, normally `OPEN` or `CLOSED`.

### Name

Current storage name or `none`.

### Backend

Storage backend description, for example `DUMB data model`, when available.

### Volume

Formatted as:

```text
<bases> bases · <records> records · <physical size>
```

Example:

```text
10 bases · 598 records · 146 KiB
```

`10 bases` means ten internal KANGER **schema bases** in the current persistent model, not ten customer databases. The raw semantic definitions of `bases`, `records`, and `physical.bytes` are owned by `COMMANDS.md` / canonical STATUS.

### WAL

Number of pending schema bases reported by recovery/WAL telemetry.

### Cache

```text
<used> / <maximum> · <entries> entries
```

### Cache I/O

```text
<hits> hits · <misses> misses · <evictions> evictions
```

When storage is closed, backend/volume/WAL/cache metrics may be `unavailable`; that is an honest absence of storage telemetry, not a zero-value database.

---

## 22. TECH — Session

The Session section contains:

- **User** — numeric authenticated user id;
- **Mind** — current runtime Mind id;
- **Home** — `user.dir`;
- **Database** — configured `database.dir`;
- **Sources** — configured `sources.dir`.

With the standard installation defaults these are normally under:

```text
/var/lib/kanger/KANGER/<user-id>/
/var/lib/kanger/KANGER/<user-id>/DB/
/var/lib/kanger/KANGER/<user-id>/SRC/
```

Per-user paths may be overridden in that user's `kanger.conf`; see the administration manual.

---

## 23. TECH — Runtime

The Runtime badge displays the KANGER product version.

### Build

Build source branch metadata, when embedded in the Server build.

### Built

Build timestamp, formatted for readability.

### Java

```text
<java-version> · <JVM-name>
```

### System

```text
<OS> · <architecture>
```

### Uptime

JVM uptime formatted as days/hours/minutes/seconds as needed.

### Heap

```text
<used> / <committed> / <maximum>
```

TECH uses binary size units (KiB, MiB, GiB) for readability; canonical STATUS exposes byte counters.

---

## 24. Reading TECH correctly

A few common interpretations:

### `Operation = idle`, `Canonical STATUS = CURRENT`

Normal idle observation. TECH obtained a current structured status snapshot without becoming an operator mutation.

### `Compatibility = UNQUALIFIED`

Not sufficient evidence of an error. Compatibility qualification is lifecycle state; correlate with the command outcome, quiescence, and pending-child counts.

### `Objects = unavailable`

Expected in STATUS schema v1. It explicitly means no safe cheap object-count metric is exposed.

### `Storage = CLOSED` and storage detail = `unavailable`

Normal when no persistent storage is attached.

### `10 bases`

Ten internal persistent schema bases, not ten user databases.

### Browser Generation vs Storage generation

They are unrelated identities:

- Browser **generation** = operation/workspace commit generation;
- storage **physical generation** = persistent storage publication/generation concept.

Never compare the numeric values as if they were one sequence.

---

## 25. User workflow examples

### Inspect the current system without changing it

```text
status
status core transaction
status storage
status session
status runtime
```

Or open TECH for the formatted snapshot.

### Inspect current storage from the UI

Click the storage indicator to compose `storage`, inspect/edit the command if desired, and execute it.

### Investigate one statement

Use the `tree` action in Base / Statements. The Browser composes `base tree <id>`; execute it to retrieve canonical provenance.

### Investigate one solution

Use the `tree` action in Solutions, which composes `solution tree <id>`.

### Accept a hypothesis

Use the `⊕` action in Hypotheses. It composes `when accept <index>`; execute only after deciding that the hypothesis should become accepted input.

---

## 26. What the Browser does not own

The Browser UI does **not** own:

- canonical Console grammar;
- KANGER logical semantics;
- transaction settlement;
- storage publication/qualification;
- source path security;
- account registration policy;
- credential persistence;
- host administration;
- Server lifecycle;
- canonical STATUS metric definitions.

Those authorities remain in the Server/runtime or installation/admin layer. This separation is intentional and is part of the KANGER 3.7.0 product contract.

---

## 27. Related documentation

- `../README.md` — installation, update, configuration, TRUSTED/EMAIL_VERIFIED registration, `kanger-admin`, operational troubleshooting.
- `COMMANDS.md` — complete Console grammar and command semantics, including canonical STATUS field definitions.

This manual documents the Browser UI shipped with KANGER 3.7.0. A future release that changes visible workspace structure, operation behavior, or TECH telemetry must update the distribution UI manual with that change.