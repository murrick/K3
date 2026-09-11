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

With no options, the bundled launchers preserve the normal Console entry path and start an **interactive login**:

```text
login:
password:
```

The launchers do not select a user or authentication mode themselves; they only set the Developer Distribution classpath and forward caller arguments to `org.kanger.Kanger`.

For the explicit local single-user mode, request it deliberately:

```sh
bin/kanger-console -S
```

To select an existing user non-interactively, pass the user and password explicitly, for example:

```sh
bin/kanger-console -U <login> -P <password>
```

The launchers support the same `org.kanger.Kanger` options:

- `--adduser | -A <login>` — create a user; password is required;
- `--user | -U <login>` — select login;
- `--password | -P <password>` — select password;
- `--singleuser | -S` — start local single-user mode; the `singleuser` credential is created/reused automatically;
- `--help | -H` — show launcher help.

`KANGER_OPTIONS` may provide the same options through the environment. Command-line arguments and environment options are combined by the Console entry point.

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

- `?` — run the Core program-check operation owned by `Mind`;
- `z` — repeat the last Core query.

## 4. Canonical command reference

The spellings below are the canonical registry syntax on KANGER 3.7.0. The parser also accepts minimum unique prefixes where unambiguous, but scripts and documentation should prefer full canonical spellings.

The one-line descriptions below follow the same `CommandRegistry` metadata used by runtime `help`.

### RULE

- `rule` — show the current primary rule context;
- `rule <id>` — show one rule by runtime ID;
- `rule all` — show rules and produced statements;
- `rule produced` — show produced/generated rules;
- `rule level [<n>]` — show rules grouped by published user transaction level, or only level `n` when supplied;
- `rule tree <id>` — show the compiled structural tree of one rule;
- `rule comment <id>` — show a rule comment;
- `rule comment <id> <text...>` — set a rule comment; explicit empty text clears it.

`rule` and `rules` are synonymous family spellings.

### FUNCTION

- `functions` — show defined functions;
- `function <id>` — show one function by runtime ID;
- `function source <id>` — show source of one function.

### BASE

- `base` — show current unambiguous base statements;
- `base predicates` — show predicates known in the semantic context;
- `base predicate <id|name>` — show base statements for one predicate by runtime ID or name;
- `base tree <statement-id>` — show provenance of one base statement.

`predicate`/`predicates` family spellings are accepted where registered.

### VALUES

- `values` — show the current Values rowset using configured default ordering;
- `values order <field> [asc|desc] [, <field> [asc|desc]]...` — show the current Values rowset with invocation-local multi-key ordering.

Ordering changes presentation only; it does not change result membership.

### SOLUTION

- `solutions` — show the complete current Solutions set;
- `solution <id>` — show one Solution by its actual `IRule` runtime ID;
- `solution tree <id>` — show provenance of one Solution.

### WHEN / hypotheses

- `when` — show the current hypothesis rowset;
- `when accept <index>` — accept one hypothesis by zero-based row index.

### TRANSACTION

- `transaction` — show current transaction state;
- `transaction start` — start a child transaction;
- `transaction commit` — commit the current transaction, or perform the qualified root checkpoint where applicable;
- `transaction rollback` — roll back the current child transaction;
- `transaction squash` — collapse all explicit transaction history above U0 into one U1 without changing U0.

Registered top-level aliases:

- `start` — alias for `transaction start`;
- `commit` — alias for `transaction commit`;
- `rollback` — alias for `transaction rollback`;
- `squash` — alias for `transaction squash`.

Child transaction settlement follows the same qualified ownership/conflict model as the Core transaction lifecycle.

### SOURCE

- `get [<source>]` — with a name, load and compile one source; when omitted, Console lists available source names;
- `put <source>` — persist the current source under one logical name;
- `delete [<source>]` — with a name, delete one source; when omitted, Console lists available source names.

The no-argument list behavior is Console-local convenience around the canonical source operations.

### STORAGE

- `storage` — show available storages and the current/open storage;
- `storage use <name>` — open or create one storage;
- `storage close` — close the current storage;
- `storage drop <name>` — drop one explicitly named storage;
- `storage reindex <name>` — reindex one explicitly named storage.

Registered aliases:

- `use` — show available/current storage;
- `use <name>` — alias for `storage use <name>`;
- `close` — alias for `storage close`;
- `drop <name>` — alias for `storage drop <name>`;
- `reindex <name>` — alias for `storage reindex <name>`.

Drop asks for confirmation. Reindex reports progress. Transaction lifecycle and physical storage lifecycle are distinct; lifecycle preconditions are enforced rather than silently committing/rolling back user transactions.

### STATUS

- `status` — show canonical read-only product status;
- `status core` — show the Core status projection;
- `status core objects` — show the Core object-count/status projection;
- `status core transaction` — show Core transaction status;
- `status core levels` — show Core transaction-level status;
- `status storage` — show storage status;
- `status session` — show Console session status;
- `status runtime` — show runtime/capability status.

Canonical grammar:

```text
status [core [objects|transaction|levels]|storage|session|runtime]
```

### SYSTEM / SESSION

- `erase` — clear the current workspace using qualified runtime semantics;
- `help` — show canonical command help generated from `CommandRegistry`;
- `quit` — terminate the current session.

`quit` may request confirmation according to active state.

## 5. Explanation and diagnostic conveniences

`xplain` is Console-local rather than an ordinary `CommandRegistry` family.

- `xplain` — show the accumulated analyzer/explanation log;
- `xplain <file>` — write the accumulated explanation to a file according to Console handling;
- `xplain mode on` — enable runtime explanation display mode;
- `xplain mode off` — disable runtime explanation display mode.

The Console may suggest `xplain` after a source load is rejected.

For normal inspection also use `values`, `solutions`, `when`, `rule tree`, `base tree`, and `status ...` as appropriate.

## 6. Short working session

Start the bundled Console, authenticate, then enter:

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

For embedded Java usage read [`SDK.md`](SDK.md). For exact Java types and methods use the generated [`api/index.html`](api/index.html) reference.
