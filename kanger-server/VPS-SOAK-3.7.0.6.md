# KANGER 3.7.0.6 — VPS development soak manual regression protocol

Date: 2026-08-10

## Scope

This protocol qualifies only the 3.7.0.6 development-soak candidate deployed from the exact product head recorded in the package `SOURCE.txt`.

It is **not** release acceptance and does not authorize merge, tag/publication, or production release cutover.

The protocol exists because the first 3.7.0.5 live soak exposed cross-boundary defects that had escaped component qualification.

## Preconditions

Before manual testing:

- immutable bundle checksum verification has passed;
- guarded deploy has reported `DEVELOPMENT_SOAK_DEPLOYMENT_OK`;
- installed candidate JAR SHA equals the package SHA;
- `/health` reports `UP`;
- `/ready` reports `READY`;
- public Browser assets match the package bytes;
- rollback record and pre-deploy snapshot/off-host receipt exist;
- Browser is hard-refreshed to avoid stale JS/CSS.

Record the deployment record path and candidate JAR SHA before starting.

---

# A. Parser and canonical command surface

## A1 — doubled statement prefix must be rejected

Enter in Browser:

```text
!!eating(Cat, Mouse);
```

Expected:

- syntax/parser error is shown;
- no assertion/rule is accepted;
- no semantic state is changed.

Repeat in Java Console.

Expected: same parser rejection.

Additional verification: query a predicate that would become reachable if the malformed input had been reinterpreted. No `?eating(Cat, Mouse)` rule/trace may have been introduced by the malformed statement.

## A2 — canonical Browser command families

Exercise each canonical command in Browser:

```text
rules
functions
base
values
solutions
when
transaction
```

Expected for every command:

- command is accepted through the canonical processor;
- textual result/status appears in Dialogue where applicable;
- Browser semantic panels remain additional visualization, not a replacement command path;
- behavior is semantically consistent with Java Console.

## A3 — HELP compose affordance

Run:

```text
help
```

Expected:

- HELP is rendered from canonical command metadata;
- command syntax rows are clickable;
- clicking a syntax row copies/composes the command into the input;
- clicking does **not** execute the command automatically.

---

# B. Query result presentation

## B1 — Values and Solutions survive query completion

Run a query known to produce both Solutions and Values, for example a variable query over loaded test data.

Expected:

- query completes normally;
- Results/Values/Solutions panes that contain committed projection data remain visible;
- they do not immediately collapse leaving only Log;
- Console result remains semantically identical.

## B2 — Solutions tree action presentation

With Solutions visible, inspect the bottom `tree` action.

Expected:

```text
○ tree
```

or the equivalent established circle-marker presentation used by peer actions.

Activate it on a valid solution if appropriate.

Expected: existing tree behavior is unchanged.

---

# C. Browser editor EOF contract

## C1 — compile source without final EOL

In Editor create a source with a valid final statement and deliberately leave the last line without CR/LF.

Example shape:

```text
!alpha(one);
!omega(last);
```

where the buffer ends immediately after the final `;`.

Compile.

Expected:

- compilation succeeds when the complete source is otherwise valid;
- the final `!omega(last);` line participates in compilation;
- editor buffer is not silently modified merely to add CR/LF.

Query the final fact to prove it was compiled.

---

# D. Rejected GET projection rollback

## D1 — rejected source must not leave ghost statements

Prepare/load a `.k` source that contains at least one unique recognizable fact and also causes `get` to be rejected by compile/collision validation.

Run `get` for that source.

Expected:

- rejection/error is shown;
- authoritative Mind remains unchanged;
- unique fact from the rejected source cannot be queried successfully;
- Statements/Base panel contains no residual rows from the rejected source.

This specifically regresses the 3.7.0.5 symptom where the Mind was clean but stale rows remained in the left panel.

---

# E. Destructive-operation confirmation policy

The policy is part of the artifact contract, not optional presentation.

## E1 — erase requires explicit confirmation

Issue:

```text
erase
```

Expected:

- operation does not execute immediately;
- explicit confirmation is requested;
- cancel/no leaves context unchanged;
- a separate accepted confirmation executes erase.

## E2 — bare delete is read-only list form

Issue:

```text
delete
```

Expected:

- available `.k` sources are listed/selectable;
- no file is deleted merely by issuing bare `delete`;
- selection composes/chooses the target, followed by explicit confirmation before deletion.

## E3 — named delete requires confirmation

Issue against a disposable source:

```text
delete <source.k>
```

Expected:

- explicit confirmation names or unambiguously identifies the target;
- decline leaves source intact;
- accept deletes source.

## E4 — storage drop requires confirmation

Against a disposable test storage only:

```text
storage drop <name>
```

Expected:

- explicit confirmation before destructive execution;
- decline preserves storage;
- accept removes the disposable storage.

## E5 — put overwrite requires confirmation only on overwrite

Create/save a disposable source once using `put`.

Expected first write: no overwrite confirmation is necessary when target does not exist.

Repeat `put` to the same existing source.

Expected:

- explicit overwrite confirmation appears;
- decline preserves previous contents;
- accept performs overwrite.

---

# F. Intentional immediate operations

These are explicitly **not** part of the destructive confirmation set.

## F1 — transaction rollback is immediate

Create a disposable transaction with an uncommitted change, then issue canonical rollback.

Expected:

- rollback executes immediately;
- no confirmation dialog/question is shown;
- uncommitted change disappears from authoritative context and Browser projection.

## F2 — storage reindex is immediate

Against a safe disposable/test storage, issue canonical reindex.

Expected:

- reindex executes immediately;
- no confirmation dialog/question is shown;
- storage remains usable after reindex.

---

# G. Narrow-layout presentation

## G1 — right-side semantic actions stay structurally aligned

Resize semantic panels progressively narrower while rows containing right-side action icons/controls are visible.

Expected:

- action controls remain in their dedicated right-side layout region;
- they do not drift into row text or fall into visually inconsistent positions;
- panel may enforce a practical minimum content geometry rather than corrupting row layout.

Repeat on representative panels containing multiple action types.

## G2 — input prompt colon is vertically centered

Inspect Browser command input at normal and narrow widths.

Expected:

- `:` remains vertically centered relative to the input line;
- resizing does not move it toward the top edge.

---

# H. Projection consistency after state changes

## H1 — transaction rollback projection cleanup

Within a transaction create a temporary statement, verify it appears in the Browser semantic projection, then rollback.

Expected:

- statement is absent from authoritative context;
- corresponding semantic row disappears from the active panel without reload.

## H2 — ordinary delete/erase projection cleanup

Perform one confirmed disposable destructive operation and observe semantic panels.

Expected:

- panels reflect the resulting authoritative state immediately;
- no grey/deleted/stale active rows remain solely because legacy DOM state survived.

---

# I. Source lifecycle smoke

Exercise the non-destructive source path after all corrections:

```text
Editor Save/Open
get
Compile
bare Core query ?...
```

Expected:

- Save/Open preserves source;
- `get` accepted source loads normally;
- Compile succeeds;
- query result is consistent between Browser and Java Console;
- no correction above regresses the previously qualified source workflow.

---

# J. Final health and evidence

After manual tests:

1. verify `/health` is still `UP`;
2. verify `/ready` is still `READY`;
3. verify service remains active;
4. record the deployment record path;
5. record installed candidate JAR SHA;
6. record any failed manual item verbatim with reproduction steps;
7. do not merge or promote the candidate solely because the soak completed.

## Acceptance rule

Manual 3.7.0.6 development-soak qualification is PASS only when all mandatory sections A–J complete without unexplained divergence.

Any failure remains a development defect and either:

- receives a new stacked correction artifact, or
- is explicitly classified/documented before any further lifecycle decision.

Release acceptance remains a separate explicit decision.
