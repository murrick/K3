# Disposable DMZ Console walkthrough

This is a development fixture for the currently supported exact-source restriction
workflow. It is not final acceptance of the full branching DMZ candidate.
Run from the repository root with Python 3 and a JDK providing `java` and `javac`:

```sh
python3 scripts/run-dmz-manual.py
```

The launcher compiles fresh sources without Maven or network downloads and opens
Console directly over a new Q. Each invocation creates a new `run-*` directory
under `target/dmz-manual`, with separate databases, operator settings and history.
It does not open existing user storage. `--output <directory>` changes the parent;
the launcher still creates a unique child. Stable legacy DUMB is excluded.

The terminal prints exact Context UUIDs/revisions, the discovered trigger rule and
complete commands for that run. They are also in `WALKTHROUGH.txt` in the displayed
run directory. Copy commands from the current run, since a new run has new UUIDs.
`walkthrough.commands` contains the complete scripted path, including deliberate
errors. `--prepare-only` prepares those files without entering Console.

## Knowledge and expected result

Q contains:

```text
!seed(1); !~cache(1);
!seed(2); !~cache(2);
!seed(3);
```

Rules contains:

```text
!@x seed(x) -> cache(x);
!@x cache(x) -> tail(x);
```

The two derived external cache supports conflict with protected Q facts. Each
conflict offers one eligible derived restriction; the Q primary is not selectable.
The independent third substitution can still derive `tail(3)` in the private DMZ
workflow after both conflicting applications are excluded. The Rules production
itself remains available.

## First complete path

1. Inspect `ctx version Q` and `ctx version Rules`. They match the printed pins.
2. Paste the complete `Start:` command. Expect `NEXT_CONFLICT; selected=0`.
3. Run `ctx dmz explain`. Inspect positive and negative evidence; confirm the
   negative Q primary and positive external chain have distinct exact origins.
4. Run `ctx dmz choose 1`. Expect `NEXT_CONFLICT; selected=1`.
5. Explain the new conflict and run `ctx dmz choose 1` again.
6. Expect `COMPLETE; selected=2` and `Opinion 1: TRUE`. Run `ctx tree 1` to read
   the three-node `tail(3)` answer and the two selected restriction chains.

The order of the two native conflicts may differ between fresh runs. The saved
number refers to the actual current offered alternative; do not assume it means
the first seed in the fixture.

## Refusal and corrected replay

Paste the same `Start:` command again to begin a fresh initial request. Then:

```text
ctx dmz choose 0
ctx dmz explain
ctx dmz choose 1 projection=1
ctx dmz explain
```

The invalid ordinal retains the frontier for correction. The insufficient
projection refuses during `CONFLICT_EVIDENCE`, prints an `Exact replay request`,
and clears the active saved result. The final explanation therefore reports no
saved query. These are deliberate errors; the Console session continues.

Copy the entire exact refusal command and change only `projection=1` to
`projection=3`. The generated guide also supplies the equivalent recovery command
for this first attempted choice. It already includes that choice; do not append
it a second time. Expect `NEXT_CONFLICT; selected=1`, then finish with:

```text
ctx dmz choose 1 projection=3
ctx dmz explain
ctx tree 1
```

The exact historical Q/source pins, first selected identity and opposing evidence
are reconstructed before the second selection is applied.

## Q-only control and cleanup

Paste the printed `Q-only control:` command, which queries `?tail(3);` without
external sources or restrictions. Expect UNKNOWN and no answer proof. This new
ordinary result replaces the active restricted result. Thus the earlier TRUE
answer was not written into Q.

Finish with `quit`. The scripted check also exercises EOF closure. The launcher
reopens Q and checks its fingerprint, revision and topology, and checks the exact
Rules pin. The query-only walkthrough ends with `DMZ fixture Q preservation: PASS`.
Authoring changes made while experimenting intentionally change that preservation
check; use a fresh run for the documented query-only path.

## Reproduce the scripted check

```sh
python3 scripts/run-dmz-manual.py --smoke
```

This builds a new fixture and executes the same real Console command stream.
`console-out.txt` and `console-err.txt` preserve the visible output and deliberate
refusals. The runner checks both conflict sides, completed proof/history, exact
budget recovery, UNKNOWN control, close/reopen and Q/source preservation. Success
prints `DMZ_MANUAL_FIXTURE_PASS`. The same driver is included in the combined DMZ
gate; the displayed assertion count is not a count of independent scenarios.

The fixture uses an explicit caller-selected continuation and supported unary
integer rules. A complete symmetric derived branch family and general query/result
forms remain subsequent development work in the DMZ readiness plan.


## Disposable family proof report (stage 155)

For manual inspection of the SDK family traversal, run:

```sh
python3 scripts/run-dmz-manual.py --family
```

This creates a separate disposable fixture and saves `family-report.txt` in the
printed `run-*` directory. It runs the real public SDK family utility, with exact
Context/revision/rule discovery and a fresh source reconstruction for every
prefix. It does not open Console or existing user databases. The original command
without `--family` still opens the explicit-choice Console walkthrough above.

The family fixture has Q donors `a(1)`, `a(2)`, `a(3)`, `b(4)`, source A's
`a(x) -> p(x)`, and source B's `b(1)`, `b(2)`, `b(x) -> ~p(x)`. Both caches are
warmed before contrary source primaries, so two actual conflicts have stored
history. The report queries `?p(1);`. With default limits expect:

- `Qualified traversal: exhausted`, four completed branches and no pending or
  uncovered branches. These are four exact proof histories; some answer values
  repeat. Both TRUE and FALSE appear with native answer proofs.
- Each branch lists its selected source/substitution restrictions and complete
  premise trees. Observed conflict sections show both endpoint proofs, including
  Q facts and any contrapositive application. Query answer proofs are separate
  from the restriction proof trees.
- `Physical Q: unchanged (verified)`. Every native continuation rolled back;
  the report remains readable after the fixture's owner closes.

To inspect partial results and corrected retries:

```sh
python3 scripts/run-dmz-manual.py --family --family-invocations 3
python3 scripts/run-dmz-manual.py --family --family-choices 1
python3 scripts/run-dmz-manual.py --family --family-invocations 64 --family-choices 128
```

The invocation cap of three retains one completed branch plus pending sibling
groups marked `Invocation limit`. A choice-depth cap of one leaves no complete
two-conflict branches and marks pending groups `Choice limit`. Each group identifies
the exact historical parent, first unexplored ordinal and all remaining choices
in that sibling suffix. Read the selected prefix and current conflict before
interpreting these options. A fresh retry creates new fixture UUIDs, so compare
status and source statements rather than copying old identities between runs.

Allowed caps are 1..10000 fresh SDK invocations and 1..128 selected restrictions.
Family-only limit flags require `--family`; it cannot be combined with `--smoke`
or `--prepare-only`. No automatic theory/model equivalence deduplication or native
missing-history reconstruction is claimed. The report is a first manual family
surface; choosing/reporting families inside Console and the remaining full-DMZ
language/result scenarios are still readiness-plan work.
