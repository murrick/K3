# KANGER Server 0.14.4 — local operator plane

Date: 2026-08-04

Status: OPEN

## Integration boundary

```text
base shelf: develop/server/0.14.3
base commit: 18223327fbff520b8b65c635971cf4562372077b
working branch: server/0.14-account-lifecycle
draft PR: #62
```

## Purpose

Introduce a local server-operator plane and expose account provisioning and safe account deletion through the same lifecycle authority used by EMAIL_VERIFIED activation.

The operator is not a KANGER application user. No web role, RBAC grant or ordinary application session can authorize operator commands.

## Process ownership

`users.conf`, pending registration state, sessions, runtime objects and account homes have one authoritative owner while the server is running: the KANGER Server JVM.

A standalone CLI process must not mutate these files directly because JVM locks and runtime/session state do not cross process boundaries.

The target topology is therefore:

```text
kanger-admin CLI
-> local authenticated admin endpoint
-> server JVM
-> AccountLifecycleService
-> credential/session/runtime/workspace authorities
```

## Local admin transport

The admin endpoint is separate from the public API listener.

Initial contract:

```properties
server.admin.enabled=true
server.admin.bind.address=127.0.0.1
server.admin.port=1965
server.admin.request.max.body.bytes=65536
server.admin.token.file=KANGER/admin.token
```

Security requirements:

- bind address must be loopback;
- the admin listener must never be included in the public nginx template;
- every request requires `Authorization: Bearer <admin-token>`;
- the server creates a cryptographically random token when absent;
- the token file is owner-only where POSIX permissions are available;
- token values are never logged;
- request bodies are bounded;
- only POST is accepted for mutations;
- public application sessions and pending-action tokens are invalid on this listener;
- failed authentication returns a generic response without token diagnostics.

The endpoint is intended for local invocation or an SSH-local port forward. TLS is not required on the loopback transport; remote exposure is forbidden by configuration validation.

## CLI

Executable:

```text
kanger-admin
```

Initial commands:

```text
kanger-admin create-user
kanger-admin delete-user
```

Input modes:

```text
interactive
flag-based
mixed
```

Examples:

```text
kanger-admin create-user --login rick --email rick@example.org
kanger-admin delete-user --login rick
kanger-admin delete-user --user-id 7
```

Missing non-secret fields may be prompted. Password input must use a console-hidden prompt when interactive and must not be echoed or logged. A command fails instead of silently reading a password from an insecure redirected terminal unless an explicit non-interactive password source is selected.

CLI exit classes:

```text
0 success
2 invalid command/input
3 authentication/connection failure
4 account conflict/not found
5 lifecycle incomplete/recovery required
```

## Create-user contract

Local operator creation uses:

```text
AccountActivationSource.LOCAL_OPERATOR
```

Topology:

```text
validate request
-> AccountLifecycleService.createActiveAccount
-> complete ACTIVE account
-> optional e-mail remains unverified
-> no session
```

The CLI and admin endpoint must not reproduce credential, userId, workspace or DB/UDF initialization logic.

Required evidence:

- flag, interactive and mixed parsing;
- no plaintext password in logs, response or profile;
- duplicate login/e-mail is rejected by the lifecycle owner;
- successful creation authenticates through the ordinary public login path;
- optional operator e-mail is not marked verified;
- no session is created by the admin command.

## Delete-user identity

Deletion accepts exactly one selector:

```text
--user-id <id>
--login <login>
```

Resolution must cross-check the credential authority and canonical account profile. Ambiguous or inconsistent identity is a hard failure; the operator command must not guess.

Deletion is by exact `userId`. Login is only a resolver.

## Persistent deletion journal

Deletion is a recoverable state machine, not a recursive directory delete.

Default journal:

```text
KANGER/account-deletions.conf
```

Each record contains:

- opaque deletion id;
- exact userId;
- login and optional e-mail snapshot;
- canonical home;
- quarantine destination;
- created/updated timestamps;
- lifecycle state;
- last diagnostic text without secrets.

Versioned atomic persistence and owner-only permissions are required.

States:

```text
PREPARED
CREDENTIAL_REMOVED
HOME_QUARANTINED
COMPLETE
```

A COMPLETE record is retained as the deletion audit/recovery identity until a later explicit purge operation removes both quarantine data and its journal entry.

## Safe deletion topology

Preparation performs only validation and journal persistence:

```text
resolve exact account
-> verify canonical home and quarantine destination
-> persist PREPARED
```

The credential authority then provides a prepared deletion boundary under its exclusive lock:

```text
credential authority lock
-> wait for/hold user runtime lock
-> detach all sessions
-> close Mind/storage runtime
-> revoke legacy confirmation tokens
-> remove matching stale PendingRegistration
-> atomically remove credential
```

Lock order:

```text
registration authority
-> credential authority
-> session/runtime lock
```

This is the same order used by pending activation and active login; reverse acquisition is forbidden.

After credential removal:

```text
persist CREDENTIAL_REMOVED
-> atomically move KANGER/<id> to KANGER/.quarantine/<id>-<deletion-id>
-> persist HOME_QUARANTINED
-> persist COMPLETE
```

## Failure semantics

### Before credential removal

The credential remains present and canonical home remains in place. The account is still usable after a new ordinary login, even if an old session was detached while attempting runtime closure.

The PREPARED journal record permits retry or explicit cancellation.

### Credential removed, home still canonical

Public authentication must remain impossible. The journal exposes `CREDENTIAL_REMOVED`; retry continues forward to quarantine. The implementation must never silently restore authentication as an automatic rollback.

### Home moved, journal not advanced

Recovery inspects the exact canonical/quarantine paths and advances the journal. It must not create a second quarantine destination or delete either tree blindly.

### COMPLETE

- authentication fails;
- all old sessions fail;
- canonical account home is absent;
- exact quarantine tree exists;
- audit identity remains readable;
- a later account may reuse the login/e-mail only after deletion is COMPLETE.

## Confirmation and pending revocation

Deletion revokes:

- all active session tokens for the user;
- historical userId-bound confirmation records;
- stale pending registration with the same active login;
- the runtime/history cache.

This prevents a stale confirmation from recreating or reconciling a deleted account while deletion is in progress.

The operation executes under the same registration-authority lock as pending confirmation.

## Red/focused evidence

- create-user delegates to `AccountLifecycleService` exactly once;
- delete identity mismatch fails before mutation;
- failure before credential removal leaves credential and canonical home;
- runtime-close failure prevents credential deletion;
- successful credential removal rejects new authentication immediately;
- every old session token is rejected;
- legacy confirmation token is revoked;
- stale pending registration cannot confirm or shadow deletion;
- failure after credential removal retains deterministic journal state;
- retry from PREPARED with missing credential advances forward;
- retry after home move does not duplicate or lose quarantine data;
- successful deletion leaves one COMPLETE journal record and one exact quarantine tree;
- delete-user is idempotent for the same deletion id;
- concurrent login/confirmation/delete respects the lock order;
- non-loopback admin bind is rejected;
- missing/incorrect admin bearer token never invokes lifecycle code;
- public API listener cannot dispatch admin commands;
- CLI does not print or persist passwords or admin tokens.

## Non-goals

Server 0.14.4 does not introduce:

- web administrator accounts or RBAC;
- remote administration exposure;
- physical purge of quarantine data;
- account restoration from quarantine;
- account rename;
- verified e-mail replacement;
- browser UI changes;
- final Server 0.14 version identity.

## Completion criteria

0.14.4 closes only when:

- local authenticated admin transport is operational and loopback-only;
- `kanger-admin create-user` and `delete-user` support flag, interactive and mixed input;
- both commands use the unified lifecycle owner;
- deletion is persistent, recoverable and forward-only after credential removal;
- sessions, confirmation material, stale pending state and runtime are revoked;
- quarantine and audit identity are preserved;
- all permanent workflows pass on the exact closure HEAD;
- `develop/server/0.14.4` is created as an immutable shelf.
