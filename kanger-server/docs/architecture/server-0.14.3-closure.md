# KANGER Server 0.14.3 — persistent pending registration closure

Date: 2026-08-04

Status: CLOSED, pending exact closure-HEAD qualification

## Integration boundary

```text
base shelf: develop/server/0.14.2
base commit: b0a88eac128fa7eafdf7733be1112a844e091233
working branch: server/0.14-account-lifecycle
functional head: 1f5f494891cd7eee00ea567775bad8560df57cd7
draft PR: #62
```

## Scope completed

Server 0.14.3 replaces the public EMAIL_VERIFIED registration and confirmation topology with persistent transient pending registration.

Completed:

- introduced immutable `PendingRegistration` records;
- introduced versioned `PendingRegistrationStore` with atomic replacement;
- introduced `PendingRegistrationService` as the coordinator between transient registration intent and complete ACTIVE account publication;
- introduced `PendingRegistrationReactor` before all historical registration and confirmation behavior;
- made public EMAIL_VERIFIED registration create no credential, user id, account home, DB/UDF runtime or authenticated session;
- made confirmation activate exclusively through `AccountLifecycleService`;
- made confirmation return no authenticated session;
- introduced credential-authenticated pending login with scoped pending-action tokens;
- implemented confirmation resend, pending e-mail replacement and cancellation without an ordinary user session;
- routed confirmation mail directly from pending login/e-mail data without requiring `IUser`;
- preserved pending records when mail queueing or account activation fails;
- implemented restart-safe confirmation and pending-action tokens;
- implemented both account-publication crash-window recovery paths;
- prevented stale pending state from shadowing a successfully authenticated ACTIVE account;
- kept every pre-cutover credential ACTIVE in TRUSTED and EMAIL_VERIFIED policies;
- blocked historical session-based resend from producing unusable legacy confirmation links;
- made verified e-mail immutable;
- made account login immutable so profile identity cannot diverge from credential identity;
- enforced login and e-mail uniqueness again inside the physical workspace owner;
- retained the Server 0.14.2 TRUSTED topology and existing-account migration behavior.

## Governing invariant

```text
PendingRegistration is registration intent, not an account.
```

Before successful confirmation there is no:

- credential entry;
- user id;
- numeric `KANGER/<id>` account home;
- DB or UDF runtime;
- authenticated session;
- history or Mind runtime.

A pending record contains only:

- opaque pending id;
- normalized login and e-mail;
- encoded `CredentialMaterial`;
- profile fields and privacy consent;
- creation and expiry timestamps;
- confirmation-token fingerprint and expiry;
- pending-action-token fingerprint and expiry;
- resend metadata.

Plaintext passwords and raw persisted security tokens are excluded.

## Persistent transient store

Default location:

```text
KANGER/pending-registrations.conf
```

The store provides:

- versioned `v1` records;
- atomic temporary-file replacement;
- owner-only `rw-------` permissions where POSIX permissions are available;
- protected service-state ACL fallback on non-POSIX filesystems;
- startup and lazy pending-TTL eviction;
- independent pending, confirmation and action TTLs;
- bounded record count;
- restart recovery;
- normalized case-insensitive e-mail uniqueness;
- one-time confirmation rotation;
- scoped action-token rotation after pending e-mail replacement.

Configured defaults:

```properties
server.registration.pending.ttl.hours=168
server.registration.confirmation.ttl.hours=24
server.registration.action.ttl.minutes=15
server.registration.resend.cooldown.seconds=60
server.registration.pending.max.records=10000
```

Confirmation-token expiry does not remove an otherwise live pending registration. A later authenticated resend rotates the confirmation token.

## Public EMAIL_VERIFIED topology

### Registration

```text
validate recipient and privacy consent
-> verify active login/e-mail uniqueness
-> derive CredentialMaterial
-> persist PendingRegistration
-> queue confirmation mail from pending data
-> return PENDING_CONFIRMATION without session
```

The historical `UserFactory.createUser()` path is not reached.

Registration success:

```json
{
  "result": "OK",
  "state": "PENDING_CONFIRMATION",
  "email_hint": "r***@example.org"
}
```

Mail queue failure retains pending state and returns:

```text
MAIL_DELIVERY_UNAVAILABLE
```

### Pending login

A login/password pair absent from active credentials but present in pending state is verified against the stored `CredentialMaterial` and returns:

```text
EMAIL_CONFIRMATION_REQUIRED
pending_action_token
email_hint
can_resend=true
can_change_email=true
can_cancel=true
```

The pending-action token grants no session, query, DB/UDF, profile or operator access.

### Scoped actions

The pending-action token authorizes only:

- resend confirmation;
- replace the unconfirmed e-mail;
- cancel pending registration.

Changing pending e-mail:

- validates and normalizes the new address;
- checks active and pending uniqueness;
- invalidates the previous confirmation token;
- rotates the action token;
- queues a new confirmation message.

The historical `resend + ordinary session token` surface is blocked inside `PendingRegistrationReactor` and never reaches the legacy mail flow.

### Confirmation

The browser root link:

```text
/?confirm=<token>
```

is consumed before application-context routing. It therefore cannot fall through to the historical userId-bound confirmation path.

Successful confirmation:

```text
resolve pending token without deletion
-> activate complete account through AccountLifecycleService
-> remove exact pending record only after publication succeeds
-> return EMAIL_CONFIRMED without session
```

Ordinary login remains the only session-creation step.

## Disaster-recovery topology

Every confirmed account stores:

```properties
reg.activation.reference=<pending-id>
```

This reference is opaque and does not grant authorization. It identifies only the registration intent that created the account.

Confirmation reconciliation is serialized by one JVM registration-authority lock and distinguishes three states.

### No account artifacts

```text
no credential
no matching canonical home
-> normal complete ACTIVE publication
-> pending cleanup
```

### Credential and home already published

```text
credential exists
matching canonical home exists
exact activation reference matches
-> no duplicate account
-> pending cleanup only
```

This recovers a process stop after account publication but before pending removal.

### Home published, credential missing

```text
no credential
matching canonical home exists
exact activation reference matches
-> publish CredentialMaterial for the exact existing userId
-> pending cleanup
```

This recovers a process stop after canonical-home publication but before the final credential snapshot replacement.

`CredentialStore.publishPrepared(userId, login, material)` is deliberately narrow:

- the requested login must be absent;
- the exact userId must be absent from versioned and legacy credentials;
- the workspace login and activation reference must match;
- no new user id is allocated.

A home with the same login but another activation reference is never adopted. Confirmation returns `LOGIN_ALREADY_USED`, leaves the pending record retryable and publishes no credential.

A successfully authenticated credential always wins over a stale pending record with the same login; stale pending state cannot shadow an ACTIVE login.

## Migration contract

Every credential that existed before the pending-registration cutover remains an ACTIVE account in both policies:

```text
TRUSTED
EMAIL_VERIFIED
```

Historical `reg.agreed=false` or `reg.email.confirmed=false` does not demote such an account to pending state. After successful credential authentication, the account policy boundary attaches DB/UDF runtime before the legacy login processor constructs the session.

Pending login cannot use this migration path because the request receives the authenticated-credential marker only after successful active credential authentication.

## Identity invariants

### Verified e-mail

A verified e-mail address is immutable.

Profile updates may repeat the same normalized address, but replacement or clearing is rejected before the historical profile mutator with:

```text
VERIFIED_EMAIL_IMMUTABLE
```

An unverified optional e-mail of a TRUSTED or historical account remains editable until a future explicit verification workflow is introduced.

### Account login

The account login is the credential identity and is immutable through profile update.

Changing `reg.login` without rewriting the credential authority would create a split identity. Such updates are rejected before the historical profile mutator with:

```text
ACCOUNT_LOGIN_IMMUTABLE
```

Password reset and account rename are not profile operations and remain outside this stage.

### Workspace-owned uniqueness

`FileAccountWorkspace.prepare()` independently checks existing numeric account homes for:

- duplicate normalized login;
- duplicate case-insensitive e-mail.

This check runs inside the credential prepared-publication authority boundary, so future operator callers cannot bypass identity uniqueness merely by calling `AccountLifecycleService` directly.

Legacy account homes are included in these checks through `reg.login` and `reg.email`, even before their credentials migrate to the versioned format.

## Structured account codes

The public account contract now includes:

```text
REGISTRATION_DISABLED
AUTHENTICATION_FAILED
EMAIL_CONFIRMATION_REQUIRED
CONFIRMATION_TOKEN_INVALID
CONFIRMATION_TOKEN_EXPIRED
EMAIL_ALREADY_USED
VERIFIED_EMAIL_IMMUTABLE
ACCOUNT_LOGIN_IMMUTABLE
LOGIN_ALREADY_USED
RESEND_RATE_LIMITED
MAIL_DELIVERY_UNAVAILABLE
```

Clients must branch on codes rather than parse descriptions.

## Focused evidence

The Server 0.14.3 test corpus proves:

- pending persistence survives store reconstruction;
- plaintext password and raw tokens are absent from the file;
- expired pending records are evicted at restart and lazily;
- confirmation expiry preserves the live pending record;
- pending password verification issues only a scoped action token;
- resend cooldown and token rotation;
- pending e-mail replacement rotates confirmation and action tokens;
- cancellation removes only pending state;
- registration creates no credential or numeric home;
- mail queue failure preserves pending state;
- activation failure preserves pending state and account absence;
- successful confirmation creates one verified ACTIVE account and no session;
- root confirmation never delegates to the historical confirmation path;
- credential+home/pending-cleanup recovery;
- home-only/credential-missing recovery for the exact userId;
- unrelated activation references are rejected without consuming pending;
- active credential login wins over stale pending state;
- active and legacy login/e-mail uniqueness;
- workspace-owner duplicate login and e-mail rejection;
- pre-cutover credential activation in both policies;
- legacy resend cannot escape the pending boundary;
- verified e-mail immutability;
- account login identity immutability;
- exact credential recovery rejects existing login or userId.

## Functional-head qualification

All permanent workflows completed successfully on functional head
`1f5f494891cd7eee00ea567775bad8560df57cd7`:

```text
KANGER qualification isolation     run 30903860385  success
KANGER Server                      run 30903858763  success
KANGER CI                          run 30903859071  success
KANGER III semantic planning       run 30903858809  success
KANGER III storage optimization    run 30903858921  success
KANGER III DUMB reliability        run 30903858861  success
```

The KANGER Server workflow passed on Java 8 and Java 21. The Java 21 job ran 116 server tests with zero failures, errors or skips and passed authenticated TRUSTED migration, token rotation and shutdown smoke.

## Deliberately retained compatibility surface

The following historical methods remain in source for compatibility but are no longer reachable from the public EMAIL_VERIFIED register/confirm/resend topology:

- `UserFactory.createUser()` registration coupling;
- `UserFactory.getUserByToken()` historical confirmation;
- `ConfirmationTokenStore` userId-bound tokens;
- `QueryProcessor.sendConfirmation()`;
- session-based historical resend.

Their physical removal may be performed during later consolidation after the complete Server 0.14 artifact is qualified.

## Deliberately unchanged

Server 0.14.3 does not introduce:

- `kanger-admin`;
- local administrator listener or protocol;
- safe account deletion/quarantine workflow;
- web administrator roles or RBAC;
- browser UI changes;
- verified e-mail replacement workflow;
- account rename;
- password-reset UI;
- final server artifact identity update.

The built artifact intentionally remains `server-0.13` until the complete Server 0.14 release boundary.

## Next stage

Server 0.14.4 introduces the local operator plane through `kanger-admin` and the same lifecycle owner.

Initial required operations:

```text
create-user
delete-user
```

Creation must support interactive, flag-based and mixed input and must call `AccountLifecycleService` rather than reproduce credential/workspace logic.

Deletion must be safe and ordered:

```text
resolve exact account
-> prevent new authentication
-> revoke sessions and confirmation material
-> close runtime
-> remove credential authority
-> move home to quarantine
-> commit result
```

Direct physical purge remains a separate explicit operation.

The closure commit is documentation-only relative to the qualified functional head. The immutable shelf `develop/server/0.14.3` may be created only after all permanent workflows pass on the exact closure HEAD.
