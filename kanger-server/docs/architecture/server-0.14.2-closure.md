# KANGER Server 0.14.2 — unified account lifecycle closure

Date: 2026-08-04

Status: CLOSED, pending exact closure-HEAD qualification

## Integration boundary

```text
base shelf: develop/server/0.14.1
base commit: 982e19577abfb8653acc7da4ecd54647f4a0826b
working branch: server/0.14-account-lifecycle
functional head: 26163a0d165face6b25f09f936f97fa9bde3c25d
draft PR: #62
```

## Scope completed

Server 0.14.2 introduces the target owner of complete ACTIVE account publication and closes public registration in TRUSTED mode before the historical processor can produce account, session or mail side effects.

Completed:

- introduced `AccountPolicyReactor` before the mail and legacy registration boundaries;
- enforced structured `REGISTRATION_DISABLED` for new public registration in TRUSTED mode;
- retained authenticated profile-update routing until its later redesign;
- treated every existing Server 0.13 credential as ACTIVE in TRUSTED mode regardless of historical e-mail-confirmation flags;
- introduced `AccountLifecycleService` as the target owner of complete ACTIVE account publication;
- introduced staging under `.creating` and canonical home publication by same-filesystem move;
- made credential publication the final visibility boundary;
- added credential deletion and non-authenticating login lookup required by later operator lifecycle;
- coordinated separate `CredentialStore` facades with one JVM-wide credential authority lock;
- introduced portable `CredentialMaterial` for protected pending persistence and later activation without plaintext recovery;
- separated account activation authority from e-mail verification through `AccountActivationSource`;
- preserved the historical `ssl` mail-mode alias while keeping `smtps` canonical;
- preserved the existing versioned `users.conf` record format and legacy migration behavior.

## Governing lifecycle

```text
complete request
-> derive or restore CredentialMaterial
-> allocate candidate userId under credential-authority lock
-> create private staging home
-> write canonical final paths into profile
-> validate DB/UDF runtime attachment
-> publish staging home as KANGER/<userId>
-> atomically publish credential last
-> return ACTIVE account without session
```

Until the credential snapshot is published, the account cannot authenticate. If workspace preparation, publication or final credential persistence fails, the staging or published home is rolled back and no credential remains visible.

## TRUSTED policy boundary

The production request chain is now:

```text
HttpServer
-> SessionSerializingReactor
-> AccountPolicyReactor
-> MailBoundaryReactor
-> QueryProcessor
```

For a new public registration request in TRUSTED:

```text
result=error
code=REGISTRATION_DISABLED
delegate not invoked
credential not created
home not created
session not issued
mail not queued
```

The integrated smoke also proves that an existing Server 0.13 credential with:

```properties
reg.agreed=false
reg.email.confirmed=false
```

still logs in as an ACTIVE TRUSTED account, receives DB/UDF runtime, can execute authenticated requests, logs out, rejects the closed token and receives a rotated token on the next login. The legacy profile flags are not rewritten merely because TRUSTED activation does not depend on them.

## Activation is not e-mail verification

`AccountActivationSource` records the authority that permits publication:

```text
LOCAL_OPERATOR      -> ACTIVE, optional e-mail remains unverified
EMAIL_CONFIRMATION  -> ACTIVE, supplied e-mail is verified
```

A request using `EMAIL_CONFIRMATION` must contain a non-empty e-mail address. Password representation is independent of activation authority: either plaintext or already-derived credential material may be supplied by the local operator, while a confirmed pending registration will explicitly use `EMAIL_CONFIRMATION`.

The generated user profile therefore records:

```text
LOCAL_OPERATOR:
  reg.agreed=false
  reg.email.confirmed=false

EMAIL_CONFIRMATION:
  reg.agreed=true
  reg.email.confirmed=true
```

This avoids asserting a verified e-mail merely because the account is complete and ACTIVE.

## Pending-compatible credential material

`CredentialMaterial` contains only salted PBKDF2-HMAC-SHA256 verification material and a format identifier. Its encoded form can be protected by the future `PendingRegistrationStore`, decoded after restart and published into `users.conf` without retaining or reconstructing the plaintext password.

The existing `users.conf` versioned record layout remains compatible:

```text
v2 <login> <userId> <iterations> <salt> <hash>
```

Legacy Java-hash records still authenticate and migrate transparently.

## Failure-atomicity evidence

Focused tests prove:

- successful operator creation publishes exactly one complete ACTIVE account;
- operator activation does not falsely mark an optional e-mail verified;
- encoded/decoded pending credential material activates a verified account without plaintext;
- verified-email activation without an e-mail address is rejected;
- runtime preparation failure leaves no credential, canonical home or staging directory;
- retry after preparation failure succeeds without manual cleanup and reuses the first available id;
- synthetic final credential-publication failure rolls back an already published home;
- an existing canonical/orphan home is never overwritten and does not gain a credential;
- duplicate login does not disturb the existing complete account;
- versioned and legacy credentials can be deleted by exact userId;
- two independent CredentialStore facades over one file allocate distinct ids and preserve both snapshots.

The account profile contains only canonical final paths and never persists `.creating` paths or plaintext password material.

## Functional-head qualification

All permanent workflows completed successfully on functional head
`26163a0d165face6b25f09f936f97fa9bde3c25d`:

```text
KANGER qualification isolation     run 30898207409  success
KANGER Server                      run 30898207451  success
KANGER CI                          run 30898207197  success
KANGER III semantic planning       run 30898207542  success
KANGER III storage optimization    run 30898207317  success
KANGER III DUMB reliability        run 30898207428  success
```

The KANGER Server workflow passed on Java 8 and Java 21. The server test corpus contained 80 tests with zero failures; the Java 21 job also passed authenticated loopback, TRUSTED registration rejection, unconfirmed legacy-account activation, token rotation and shutdown smoke.

## Deliberately transitional surface

Server 0.14.2 does not yet replace the EMAIL_VERIFIED public-registration path. Until 0.14.3:

- historical `UserFactory.createUser()` and confirmation-token coupling remain present;
- EMAIL_VERIFIED registration may still enter the legacy processor;
- `PendingRegistrationStore` does not yet exist;
- confirmation, resend, pending-email replacement and cancellation are not yet converted;
- no browser UI is changed;
- no `kanger-admin` executable or local admin listener exists;
- safe account deletion is not yet exposed: credential deletion is only a lifecycle primitive, while session revocation, runtime closure and home quarantine remain mandatory for 0.14.4;
- server artifact identity remains `server-0.13` until the final Server 0.14 release boundary.

## Next stage

Server 0.14.3 replaces the public EMAIL_VERIFIED registration and confirmation topology with persistent transient `PendingRegistration` records. Registration must create no credential, userId, home, DB, UDF runtime or session; confirmation must activate through the lifecycle service using persisted `CredentialMaterial`, then remove the pending record only after successful account publication.

The closure commit is documentation-only relative to the qualified functional head. The stage shelf `develop/server/0.14.2` may be created only after all permanent workflows pass on the exact closure HEAD.
