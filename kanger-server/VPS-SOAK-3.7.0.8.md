# KANGER 3.7.0.8 — focused VPS qualification

Date: 2026-08-11

## Purpose

This protocol qualifies only the residual blockers and requested Browser polish
carried out of the 3.7.0.7 targeted VPS soak, followed by a minimal regression
smoke over the already-qualified destructive and transaction boundaries.

It does **not** perform release acceptance, merge, tag/publication, or production
cutover.

During the manual run execute exactly one operator action at a time and record
its result before continuing.

## A — Help discovery

1. Hard-refresh the Browser candidate and authenticate.
2. Execute `help`.
3. Verify the RULE section visibly contains the executable collection alias:

   ```text
   rules
   ```

4. Click `rules` in Help and verify it composes exactly `rules` without executing.
5. Execute the composed `rules` and verify it has the same collection semantics
   as `rule all`.

## B — Exact source document / no-final-EOL regression

The source and the query are deliberately separate. **Do not place the query in
the Editor source sequence.**

1. Open Editor and replace its contents with exactly:

   ```text
   !eof_a(one);
   !eof_z(last);
   ```

   There must be no CR/LF after the final semicolon.

2. Use local `Save` before Compile and record the downloaded file byte length.
   Expected UTF-8 length: `26` bytes.
3. Compile once. Expected: success.
4. In Browser Console execute:

   ```text
   ?eof_z(last);
   ```

   Expected: `TRUE` / one valid solution. This proves the last no-EOL statement
   still participates in inference.
5. Return to Editor using the normal Editor control.
6. Use local `Save` again without touching the source.
7. Verify the second file is still exactly `26` bytes and byte-identical to the
   first save. Compile/view navigation must not synthesize a terminal EOL into
   the author's document.

## C — Editor return position

1. Produce several visible dialogue/history entries so the latest activity is
   below the initial session/help output.
2. Open Editor.
3. Return to Console without executing another operation.
4. Verify dialogue/history is positioned at the latest activity, not at the
   startup/beginning of history.

## D — Command-input clear control

1. Enter a non-empty command fragment into the Browser command input without
   executing it.
2. Verify one clear control is visible at the right edge of the input: a single
   circular control with a centered `×`.
3. Click it.
4. Verify the input becomes empty, focus remains in the command input, and no
   command/history entry is executed or created.

## E — User menu cleanup

1. Open the authenticated user menu.
2. Verify `Personal data`, `Change login & password`, and `Quit` remain present.
3. Verify `Resend confirmation e-mail` is absent.
4. Repeat after a hard refresh and verify it remains absent. The removal is
   unconditional and must not depend on authentication/registration mode.

## F — Left splitter geometry

1. Drag the left semantic-panel splitter slowly toward the narrow side.
2. Verify the divider follows the pointer continuously with no threshold snap,
   rebound, or jump to the right.
3. Drag it back toward the wide side and verify the same continuous behavior.
4. Reload/hard-refresh once and verify the persisted `sx` position is restored
   through `--kanger-left` without a second visible geometry correction.

## G — Source transition invalidates a reusable local Editor view

1. With one source visible in Editor, return to Console.
2. Load a different disposable source using canonical `get <source>`.
3. Open Editor.
4. Verify the newly loaded Server source is shown; the previous local Editor
   document must not be revived after an authoritative workspace/source change.

## H — Critical prior-boundary regression smoke

Recheck only the safety-sensitive behavior that must not regress:

1. `erase` -> Browser-owned confirmation -> Cancel; workspace unchanged.
2. `transaction start` -> upper TX projection changes immediately.
3. `transaction rollback` -> immediate, **no confirmation**, TX projection
   returns immediately.
4. Existing-source `put <name>` -> Browser-owned overwrite confirmation.
5. `storage reindex <name>` -> immediate, **no confirmation**.

Use disposable names only. Do not drop or delete non-soak data.

## I — Minimal Core smoke

Editor source:

```text
!eating(cat, mouse);
```

Compile it. Then, separately in Browser Console, execute:

```text
?$x eating(x, mouse);
```

Expected: TRUE and `x=cat`.

This explicit split corrects the 3.7.0.7 protocol wording defect that had placed
an informational query inside an Editor source sequence.

## J — Final operations evidence

Capture independently after the Browser checks:

- installed candidate JAR SHA256;
- `/health` status;
- `/ready` status;
- systemd service state;
- active public UI symlink target;
- deployment record status and exact artifact/source identities.

Acceptance requires all sections A-J to be divergence-free. Any reproducible
source-byte mutation or splitter snap remains a release blocker.
