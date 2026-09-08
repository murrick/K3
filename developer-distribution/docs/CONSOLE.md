# KANGER 3.7.0 Console

The Developer Distribution includes the standalone Java Console for local work with KANGER Core. It does not require KANGER Server, REST, UI, or a browser client.

## 1. Starting Console

POSIX/macOS/Linux:

```sh
bin/kanger-console
```

Windows:

```bat
bin\kanger-console.cmd
```

The bundled launchers start `org.kanger.Kanger -S`, the local single-user mode. The single-user credential is created/reused automatically by the Console entry point.

The underlying launcher also supports these `org.kanger.Kanger` options when started directly:

```text
--adduser  | -A <login>     create a user; password is required
--user     | -U <login>     select login
--password | -P <password>  select password
--singleuser | -S           local single-user mode
--help     | -H             show launcher help
```

`KANGER_OPTIONS` may provide the same options through the environment. Command-line arguments and environment options are combined by the launcher.

## 2. Session model

After authentication/bootstrap the Console creates the active `Mind` for the user and starts a canonical Console session.

Ordinary Console commands are parsed by the shared canonical `CommandParser`. Core-language input bypasses command dispatch and is executed by the active `Mind`. Console additionally owns a few local conveniences documented below: source-list forms, `xplain`, and `z`.

At any time enter:

```text
help
```

The displayed ordinary-command help is generated from the same `CommandRegistry` used by the parser, so it is the runtime grammar authority for the installed version.

## 3. Core-language input

Enter KANGER source/query operations directly at the Console prompt. For example:

```text
!color(apple, Red);
!color(cucumber, Green);
?color(apple, Red);
```

Definitions and queries use the same Core semantics documented in `SDK.md`: query results are logical `true`, `false`, or undetermined; parse/runtime errors are failures rather than partial logical answers.

A bare:

```text
?
```

runs the Core program-check operation owned by `Mind`.

`z` repeats the last Core query:

```text
z
```

## 4. Canonical command reference

The spellings below are the canonical registry syntax on KANGER 3.7.0. The parser also accepts minimum unique prefixes where unambiguous, but scripts and documentation should prefer full canonical spellings.

### RULE

```text
rule
rule <id>
rule all
rule produced
rule level [<n>]
rule tree <id>
rule comment <id>
rule comment <id> <text...>
```

`rule` and `rules` are synonymous family spellings. These commands inspect current rules, generated rules, transaction levels, structural trees, and rule comments.

### FUNCTION

```text
functions
function <id>
function source <id>
```

Shows defined functions, one function, or its source.

### BASE

```text
base
base predicates
base predicate <id|name>
base tree <statement-id>
```

`predicate`/`predicates` family spellings are accepted where registered. `base tree` shows provenance of one base statement.

### VALUES

```text
values
values order <field> [asc|desc] [, <field> [asc|desc]]...
```

Displays the current Values rowset. Invocation-local ordering does not mutate result membership.

### SOLUTION

```text
solutions
solution <id>
solution tree <id>
```

Displays the current solution set, one solution, or its provenance.

### WHEN / hypotheses

```text
when
when accept <index>
```

`when` displays the current hypothesis rowset. `when accept` accepts one row by zero-based index.

### TRANSACTION

```text
transaction
transaction start
transaction commit
transaction rollback
transaction squash
```

Top-level aliases are also registered:

```text
start
commit
rollback
squash
```

`transaction` shows current state. Child transaction settlement follows the same qualified ownership/conflict model as the Core transaction lifecycle.

### SOURCE

```text
get [<source>]
put <source>
delete [<source>]
```

`get <source>` loads/compiles a named source. `put <source>` persists current source under a logical name. `delete <source>` removes a named source.

In Console, bare:

```text
get
put
delete
```

are local read-only conveniences that list available source names.

### STORAGE

```text
storage
storage use <name>
storage close
storage drop <name>
storage reindex <name>
```

Registered aliases:

```text
use
use <name>
close
drop <name>
reindex <name>
```

`storage`/bare `use` shows available and current storage. `storage use <name>` opens or creates storage through the canonical user/storage lifecycle. Drop asks for confirmation. Reindex reports progress.

Transaction lifecycle and physical storage lifecycle are distinct; lifecycle preconditions are enforced rather than silently committing/rolling back user transactions.

### STATUS

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

The canonical grammar is summarized as:

```text
status [core [objects|transaction|levels]|storage|session|runtime]
```

These are read-only product/status projections.

### SYSTEM / SESSION

```text
erase
help
quit
```

`erase` clears the current workspace through qualified runtime semantics. `quit` terminates the session and may request confirmation according to active state.

## 5. Explanation and diagnostic conveniences

`xplain` is Console-local rather than an ordinary `CommandRegistry` family.

```text
xplain
xplain <file>
xplain mode on
xplain mode off
```

- `xplain` shows accumulated analyzer/explanation log;
- `xplain <file>` writes the accumulated explanation to a file according to Console handling;
- `xplain mode on|off` toggles runtime explanation display mode.

The Console may suggest `xplain` after a source load is rejected.

For normal inspection also use `values`, `solutions`, `when`, `rule tree`, `base tree`, and `status ...` as appropriate.

## 6. Short working session

Start the bundled Console, then enter:

```text
!color(apple, Red);
!color(cucumber, Green);
?color(apple, Red);
status core
rule
help
quit
```

This exercises local Core state without storage and without any Server/UI component.

For persistence, inspect the current state first and then use the canonical storage family, for example:

```text
storage
storage use tutorial
storage
storage close
```

Storage names are logical names resolved in the user storage context. Use `storage drop <name>` only when you intend to remove that storage; Console asks for confirmation.

## 7. Source workflow

Console source operations use the user's source directory. A typical discovery/load flow is:

```text
get
get <source>
```

After loading, use normal query/inspection commands. If compilation is rejected, inspect the reported parse/runtime diagnostic and use `xplain` where explanation data is available.

`put <source>` and `delete <source>` change persisted source files and should be used deliberately.

## 8. Errors and recovery

The canonical session catches and renders command parse errors, KANGER parse errors, command errors, database errors, storage lifecycle errors, and runtime errors. A failed command does not by itself mean the Console process or session must be restarted.

Storage lifecycle errors may include a code and required action. Follow the reported lifecycle requirement rather than bypassing it with internal APIs.

Unexpected exceptions are printed with a timestamp and stack trace because Console is a developer tool.

## 9. What Console documentation does not cover

This guide intentionally excludes:

- KANGER Server deployment or HTTP endpoints;
- browser/UI operation;
- production service authentication setup;
- server installation scripts.

For embedded Java usage read [`SDK.md`](SDK.md). For exact Java types and methods use [`api/index.html`](api/index.html).
