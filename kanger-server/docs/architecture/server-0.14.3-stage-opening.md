# KANGER Server 0.14.3 — persistent pending registration

Date: 2026-08-04

Status: OPEN

## Integration boundary

```text
base shelf: develop/server/0.14.2
base commit: b0a88eac128fa7eafdf7733be1112a844e091233
working branch: server/0.14-account-lifecycle
draft PR: #62
```

## Purpose

Replace the historical EMAIL_VERIFIED registration and confirmation topology with a persistent transient `PendingRegistration` lifecycle.

Public registration must no longer create a credential, user id, user home, DB/UDF runtime or session. Successful confirmation activates one complete account through `AccountLifecycleService`; ordinary login creates the session afterwards.

## Confirmed historical coupling

At the 0.14.2 base:

```text
register
-> UserFactory.token(login,password)
-> UserFactory.createUser(login,password)
-> CredentialStore.create
-> userId and user home
-> session
-> ConfirmationTokenStore.bind(token,userId)
-> MailBoundaryReactor resolves IUser
-> confirmation mail
```

Confirmation consumes a token bound to `userId` and resolves an already existing User. Resend requires an authenticated user session. This topology is superseded for EMAIL_VERIFIED registration.

## Target topology

```text
register
-> validate login/email/profile/consent
-> derive CredentialMaterial
-> persist PendingRegistration
-> queue confirmation mail from pending login/email
-> return PENDING_CONFIRMATION without session

confirm
-> resolve pending by one-time token
-> AccountLifecycleService.createActiveAccount(
       activationSource=EMAIL_CONFIRMATION,
       credentialMaterial=pending material)
-> remove pending only after successful account publication
-> return success / HTTP redirect without session

login before confirmation
-> verify pending login/password
-> return EMAIL_CONFIRMATION_REQUIRED
-> issue scoped pending-action token
```

## PendingRegistration is not an account

A pending record has no:

- credential entry;
- userId;
- KANGER/<id> home;
- DB or UDF runtime;
- authenticated session;
- history or Mind.

It contains only:

- opaque pending id;
- normalized login;
- normalized e-mail;
- encoded `CredentialMaterial`;
- profile fields and privacy consent;
- created/expires timestamps;
- confirmation-token hash and expiry;
- pending-action-token hash and expiry;
- resend count and last-resend timestamp.

Raw tokens and plaintext passwords are never persisted.

## Persistence and recovery

Default store:

```text
/var/lib/kanger-server/KANGER/pending-registrations.conf
```

Required properties:

- versioned format;
- atomic temporary-file replacement;
- owner-only permissions where POSIX permissions are supported;
- startup and lazy expiry eviction;
- bounded record count;
- restart-safe confirmation and pending-action tokens;
- no user directories or account storage for pending records.

Pending TTL and confirmation TTL are independent. The initial defaults are:

```text
pending registration TTL: 7 days
confirmation token TTL: 24 hours
pending action token TTL: 15 minutes
resend cooldown: 60 seconds
```

All values become explicit configuration keys.

## Disaster-recovery boundary

The pending record must not be consumed before ACTIVE publication succeeds.

Confirmation therefore follows:

```text
resolve valid pending token without deletion
-> activate complete account
-> remove the same pending record/token
```

If activation fails, the pending record remains retryable.

If the process stops after account publication but before pending removal, the active account carries the pending activation reference. A repeated confirmation reconciles that exact account and removes the stale pending record rather than creating a duplicate.

## Scoped pending actions

A pending-action token authorizes only:

- resend confirmation;
- replace unconfirmed e-mail and rotate confirmation token;
- cancel pending registration.

It never authorizes:

- query execution;
- DB/UDF access;
- profile API access;
- authenticated session creation;
- account deletion or operator functions.

Changing pending e-mail invalidates the previous confirmation token and sends a new message. After activation, verified e-mail remains outside this pending surface.

## Public responses

Registration success:

```json
{
  "result": "OK",
  "state": "PENDING_CONFIRMATION",
  "email_hint": "r***@example.org"
}
```

Pending login:

```json
{
  "result": "error",
  "code": "EMAIL_CONFIRMATION_REQUIRED",
  "pending_action_token": "...",
  "email_hint": "r***@example.org",
  "can_resend": true,
  "can_change_email": true,
  "can_cancel": true
}
```

Mail queue failure retains the pending record and returns `MAIL_DELIVERY_UNAVAILABLE`, allowing later credential-authenticated resend.

## Required red/focused evidence

- registration leaves CredentialStore unchanged;
- registration creates no numeric account home;
- registration returns no session token;
- pending store survives reconstruction/restart;
- raw password and raw tokens do not appear in the store;
- expired pending records are evicted;
- confirmation-token expiry does not delete a still-live pending registration;
- new confirmation rotation invalidates the previous token;
- pending login validates password and issues only a scoped action token;
- resend obeys cooldown and rotates confirmation token;
- e-mail replacement invalidates old confirmation and preserves no verified-email claim;
- cancellation removes only pending state;
- activation failure preserves pending state;
- successful activation creates complete verified ACTIVE account and no session;
- post-publication/pre-removal recovery reconciles by pending activation reference;
- repeated confirmation is idempotent only for that exact activation reference;
- active and pending login/e-mail uniqueness is enforced.

## Deliberately retained compatibility

The historical `ConfirmationTokenStore` and `UserFactory` confirmation methods may remain temporarily for source compatibility, but the public EMAIL_VERIFIED request path must no longer call them after this stage closes.

TRUSTED behavior, existing-account migration, session semantics and the 0.14.2 lifecycle shelf must remain unchanged.

## Completion criteria

0.14.3 closes only when:

- public EMAIL_VERIFIED registration creates only persistent pending state;
- confirmation activates exclusively through `AccountLifecycleService`;
- no confirmation response creates a session;
- resend/change-email/cancel work without an ordinary user session;
- restart and activation-failure recovery are proven;
- the legacy public register/confirmation path is unreachable;
- all permanent workflows pass on the exact closure HEAD;
- `develop/server/0.14.3` is created as an immutable shelf.
