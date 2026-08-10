# KANGER 3.7.0.7 — targeted VPS development-soak qualification

Date: 2026-08-10

## Scope

This is the third live VPS qualification pass following the 3.7.0.5 and 3.7.0.6 development soaks.

It is intentionally limited to the eight corrections carried by `ui/3.7.0.7-soak-corrections`, the previously blocked confirmed-destructive projection case, and one minimal source/Core/server smoke.

It is **not** release acceptance and does not authorize merge, tag/publication, or production cutover.

## Preconditions

Before manual testing:

- immutable 3.7.0.7 bundle checksum verification has passed;
- guarded deployment reports `DEVELOPMENT_SOAK_DEPLOYMENT_OK`;
- installed JAR SHA equals package candidate SHA;
- `/health` reports `UP`;
- `/ready` reports `READY`;
- public Browser bytes match the package;
- rollback record and fresh pre-deploy snapshot/off-host receipt exist;
- Browser has been hard-refreshed.

Record exact deployment record path and installed candidate JAR SHA.

---

# A. Command grammar and Help compose

## A1 — plural RULE collection

Browser:

```text
rules
```

Expected:

- command is accepted;
- result is equivalent to canonical `rule all`;
- no parser error.

Optional parity check in Java Console if it uses the same canonical parser surface.

## A2 — parameterized Help compose

Run:

```text
help
```

Click representative parameterized syntax rows one at a time.

Required visible syntax remains complete, while input composition is:

```text
rule <id>                 -> rule 
rule tree <id>            -> rule tree 
base predicate <id|name>  -> base predicate 
storage drop <name>       -> storage drop 
when accept <index>        -> when accept 
```

Expected:

- metavariables are not copied literally;
- caret is after the trailing space at the first user argument;
- clicking does not execute;
- finished no-argument commands still compose without an artificial trailing argument placeholder.

---

# B. Solution action presentation

Create a small context producing at least one Solution, for example:

```text
!eating(cat, mouse);
?$x eating(x, mouse);
```

Expected:

- Values and Solutions appear as before;
- Solution tree action is visibly only:

```text
○
```

- tooltip/accessibility meaning may remain `tree`;
- clicking the icon composes the existing canonical `solution tree <id>` action and execution still works.

---

# C. Exact source EOF contract

In Browser Editor create exactly two valid lines and deliberately leave the final line without CR/LF:

```text
!eof_a(one);
!eof_z(last);
```

The buffer must end immediately after the last `;`.

Before Compile, use the Editor local Save action to obtain a byte-reference copy if convenient.

Compile.

Expected:

- Compile succeeds;
- final statement participates in inference;
- reopening Editor/current source does **not** gain a final EOL;
- source bytes remain identical to the original document.

Prove the last statement:

```text
?eof_z(last);
```

Expected:

```text
Result: TRUE
```

Save/reopen or compare local byte count again after Compile. The prior 3.7.0.6 symptom `N bytes -> N+1 bytes` must not recur.

---

# D. Browser destructive confirmation

Use only disposable fixtures.

## D1 — cancel path

Create a disposable fact, then issue:

```text
erase
```

Expected:

- Browser-owned confirmation UI is visibly usable;
- Cancel leaves the fact/context intact;
- dialogue records cancellation without executing erase.

## D2 — named source delete accept path

Create a disposable source under a unique name with first-time `put`.

Issue:

```text
delete <disposable-source>
```

Expected:

- confirmation UI identifies the operation/target;
- Confirm executes deletion;
- subsequent bare `delete` no longer lists that source.

## D3 — storage drop accept path

Create/use then close a disposable storage if needed by lifecycle rules.

Issue:

```text
storage drop <disposable-storage>
```

Expected:

- confirmation UI appears;
- Confirm executes drop;
- subsequent `storage` no longer lists it.

---

# E. PUT overwrite guard

Choose a fresh disposable source logical name.

First save:

```text
put <name>
```

Expected: immediate save because target does not yet exist.

Repeat the same logical name **without relying on an explicit `.k` extension**:

```text
put <name>
```

Expected:

- Browser receives explicit overwrite confirmation;
- Cancel does not overwrite;
- a later Confirm performs the overwrite.

This specifically regresses the old logical-name versus physical `<name>.k` mismatch.

---

# F. Transaction projection

Start a transaction:

```text
transaction start
```

Expected immediately, without page reload:

- canonical response reports level 1 relative to a root-level fixture;
- upper Browser transaction indicator changes to the same level.

Rollback:

```text
transaction rollback
```

Expected immediately, without page reload:

- canonical response reports the parent level;
- upper transaction indicator changes to the same level;
- rollback remains immediate and shows no confirmation UI.

Repeat one start/rollback cycle if necessary to rule out a one-time initialization effect.

---

# G. Left Base/Statements splitter

At normal and narrow Browser widths, drag the left Base/Statements splitter progressively left and right.

Expected:

- splitter follows the pointer continuously within practical bounds;
- no threshold snap/jump to the right;
- left column remains usable;
- resize survives ordinary layout refresh/viewport resize according to existing persistence behavior.

---

# H. Formerly blocked destructive projection cleanup

Prepare a disposable Browser context containing one or more recognizable facts and ensure they are visible in the left semantic projection.

Issue:

```text
erase
```

Accept the Browser confirmation.

Expected without hard refresh:

- erase executes only after confirmation;
- authoritative `base` is empty;
- corresponding left semantic rows disappear immediately;
- no grey/deleted/stale active rows remain;
- transaction projection remains coherent.

This closes the 3.7.0.6 `H2` case that was blocked by the unusable Browser confirmation path.

---

# I. Minimal regression smoke

After the targeted correction checks, establish a tiny clean source/context and verify:

```text
Compile
?known_fact(...);
```

Expected:

- source compiles normally;
- query returns the expected result;
- Values/Solutions projection remains usable when applicable;
- no correction above regresses ordinary Browser source/Core workflow.

If Java Console parity is checked, remember that Browser and `singleuser` Console source repositories are separate namespaces; compare Core semantics, not Browser source-file visibility.

---

# J. Final operational evidence

After all manual items:

1. `/health` is still `UP`;
2. `/ready` is still `READY`;
3. `kanger-server.service` remains active;
4. installed JAR SHA still equals the packaged candidate;
5. public UI symlink still points to the 3.7.0.7 versioned target;
6. exact deployment record path is recorded;
7. every manual divergence is recorded verbatim before any further lifecycle decision.

## Acceptance rule

The 3.7.0.7 targeted development soak is PASS only when A–J complete without unexplained divergence.

A PASS means the candidate is qualified as a development-soak artifact only. Release acceptance remains a separate explicit decision.
