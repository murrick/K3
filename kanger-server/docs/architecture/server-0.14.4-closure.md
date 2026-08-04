# KANGER Server 0.14.4 — local operator plane closure

Date: 2026-08-04

Status: CLOSED, pending exact closure-HEAD qualification

## Integration boundary

```text
base shelf: develop/server/0.14.3
base commit: 18223327fbff520b8b65c635971cf4562372077b
working branch: server/0.14-account-lifecycle
functional head: 329a9508fb85b7ede9d9c83b6be796ca06fb90b8
draft PR: #62
```

## Scope completed

Server 0.14.4 introduces a local authenticated host-operator plane and a recoverable safe account-deletion lifecycle.

Completed:

- introduced `AdminServer` as a separate loopback-only listener;
- introduced an atomically created owner-only bearer-token authority;
- kept the public application API unaware of admin operations;
- introduced `kanger-admin` as a client of the running server rather than a second file owner;
- implemented interactive, flag-based and mixed CLI input;
- accepted passwords only through a hidden prompt or explicit `--password-stdin`;
- implemented `create-user` through `AccountLifecycleService`;
- implemented safe `delete-user` by login or user id;
- required both CLI confirmation and protocol `confirm=DELETE` before deletion dispatch;
- introduced persistent deletion journal and exact quarantine destinations;
- implemented forward recovery for every deletion crash window;
- revoked sessions, runtime, legacy confirmation and stale pending intent;
- made user-id allocation persistent and monotonic;
- blocked pending registration and confirmation while a deletion tombstone is active;
- installed `/usr/local/bin/kanger-admin` through the production installer;
- documented operator configuration, usage, exit codes and recovery boundaries.

## Governing authority boundary

```text
kanger-admin
→ loopback-only authenticated admin listener
→ AccountLifecycleService
→ credential / runtime / pending / workspace authorities
```

The CLI does not read or mutate credential, pending, journal or account-home files. `AccountLifecycleService` remains the sole account-lifecycle owner in the running server JVM.

## Local transport contract

Default topology:

```text
public application API: 127.0.0.1:1964
local operator plane:    127.0.0.1:1965
```

The operator listener:

- rejects any non-loopback bind address before opening a socket;
- authenticates a bearer token before operation dispatch;
- accepts only POST mutations;
- bounds request bodies;
- exposes only `create-user` and `delete-user`;
- is not present in the nginx template;
- is stopped by the common idempotent server shutdown path.

## Credential and secret contract

The admin token is generated with `SecureTokens.random256()`, persisted atomically and restricted to owner read/write permissions where POSIX permissions are available.

The CLI:

- forbids plaintext `--password` arguments;
- uses a hidden prompt in interactive mode;
- requires explicit `--password-stdin` for automation;
- does not print passwords or bearer tokens;
- returns machine-distinct exit classes for input, connection, lifecycle and incomplete-deletion failures.

## Operator account creation

`create-user` publishes a complete ACTIVE account immediately through the same lifecycle service used by confirmed public registration.

An optional operator-supplied e-mail address remains unverified:

```text
reg.email.confirmed=false
reg.agreed=false
```

The command creates no authenticated session. Ordinary application login creates the session afterwards.

## Safe deletion state machine

```text
PREPARED
→ CREDENTIAL_REMOVED
→ HOME_QUARANTINED
→ COMPLETE
```

Properties:

- the journal record is persisted before destructive mutation;
- a failure before credential removal leaves authentication recoverable;
- after credential removal recovery proceeds only forward;
- canonical home relocation is idempotent by exact journal destination;
- repeated recovery cannot create a second quarantine tree;
- login and e-mail remain reserved while deletion is incomplete;
- deleted and failed-allocation user ids are never reused;
- automatic recovery never republishes a removed credential.

## Functional-head qualification

All permanent workflows completed successfully on functional head
`329a9508fb85b7ede9d9c83b6be796ca06fb90b8`:

```text
KANGER qualification isolation     run 30912643725  success
KANGER Server                      run 30912644289  success
KANGER CI                          run 30912647775  success
KANGER III semantic planning       run 30912643689  success
KANGER III storage optimization    run 30912644101  success
KANGER III DUMB reliability        run 30912647308  success
```

The KANGER Server workflow passed on Java 8 and Java 21.

The server corpus contained:

```text
Tests run: 150
Failures: 0
Errors: 0
Skipped: 0
```

Java 21 additionally proved the complete deployed topology in one running process:

```text
server bootstrap
→ admin token creation
→ kanger-admin create-user
→ ordinary public login and session issuance
→ kanger-admin delete-user
→ credential and session rejection
→ exact quarantine tree
→ decoded deletion journal state COMPLETE
→ public listener rejects admin dispatch
→ clean server shutdown
```

The standalone shaded JAR, deployment assets and Java 21 artifact upload also completed successfully.

## Security and recovery evidence

Focused regressions prove that:

- missing or incorrect bearer credentials never invoke lifecycle code;
- non-loopback listener configuration fails closed;
- unknown admin paths do not dispatch account operations;
- deletion without the explicit marker never invokes lifecycle code;
- passwords and bearer tokens do not appear in CLI or protocol output;
- failed revocation before credential removal preserves the credential;
- quarantine failure after credential removal resumes forward;
- process-stop recovery after the home move reuses the exact destination;
- stale pending intent cannot resurrect an account under deletion;
- multiple facades over credential, pending and confirmation stores share JVM authority locks.

## Deliberately excluded

Server 0.14.4 does not introduce:

- a web administration console;
- application-user roles or RBAC;
- remote operator access;
- `list-users` or password-reset commands;
- quarantine purge or account restoration commands;
- public auth UI changes;
- final Server 0.14 version-identity promotion.

These are separate lifecycle or product artifacts and must not weaken the local operator boundary established here.

## Next stage

Server 0.14.5 aligns the public authentication UI with the server contract:

- expose registration policy and capabilities;
- remove Register from TRUSTED mode;
- align EMAIL_VERIFIED registration, confirmation and login UX;
- remove stale browser confirmation/session assumptions;
- keep broader owner-console redesign outside Server 0.14.

The closure changes after the qualified functional head are documentation-only. The immutable shelf `develop/server/0.14.4` may be created only after all permanent workflows pass on the exact closure HEAD.
