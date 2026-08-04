# KANGER Server 0.14.2 — unified account lifecycle

Date: 2026-08-04

Status: OPEN

Base shelf:

```text
develop/server/0.14.1
982e19577abfb8653acc7da4ecd54647f4a0826b
```

Working branch:

```text
server/0.14-account-lifecycle
```

Draft PR: #62

## Purpose

Introduce one server-side lifecycle owner for complete ACTIVE accounts and enforce the TRUSTED registration policy before the historical registration processor can create credentials, user directories, sessions or mail side effects.

## Confirmed Server 0.13 call graph

### Login

```text
QueryProcessor.processLogin
-> UserFactory.getUser(login, password)
-> optional DB/UDF initialization only when email is confirmed
-> UserFactory.addUser(user)
-> session token returned even when email is not confirmed
```

### New registration

```text
QueryProcessor.processLogin
-> UserFactory.token(login, password)
-> UserFactory.createUser(login, password)
   -> CredentialStore.create
   -> allocate userId
   -> UserFactory.getUserById
   -> create user home / SRC / DB
-> write unconfirmed user properties
-> UserFactory.addUser
-> return session token
-> queue confirmation mail
```

The existing order violates the Server 0.14 target invariant for EMAIL_VERIFIED registration. Full replacement of that flow belongs to 0.14.3. Stage 0.14.2 first establishes the complete ACTIVE account service and prevents the TRUSTED mode from entering the legacy self-registration path.

## Policy boundary

A dedicated account-policy reactor is inserted inside the session-serialization boundary and before the mail boundary:

```text
HttpServer
-> SessionSerializingReactor
-> AccountPolicyReactor
-> MailBoundaryReactor
-> QueryProcessor
```

For a new public registration request:

```text
RegistrationPolicy.TRUSTED
-> result=error
-> code=REGISTRATION_DISABLED
-> legacy processor not called
-> no credential/home/session/mail side effect
```

Authenticated profile updates with a non-empty session token are not classified as new self-registration and continue to the delegate until the profile API is redesigned in a later stage.

## AccountLifecycleService boundary

`AccountLifecycleService` becomes the sole target owner of physical account lifecycle operations.

Complete ACTIVE creation owns this sequence:

```text
validate request
-> reserve/create credential and userId
-> create staged user home
-> materialize profile and required directories
-> initialize DB/UDF resources
-> publish staged home as the canonical user home
-> expose account to authentication/session workflows
```

No partially created account may remain observable after failure.

Deletion owns this sequence:

```text
resolve account
-> prevent new authentication
-> revoke sessions and confirmation material
-> close runtime
-> remove credential/index authority
-> move home to quarantine
-> commit deletion result
```

The first implementation may introduce narrow ports around the existing credential, filesystem and runtime authorities. It must not duplicate lifecycle logic in QueryProcessor or the future CLI.

## Failure-atomic creation contract

Every externally visible mutation must have a compensating action until publication succeeds.

Required red/focused scenarios:

- failure before credential creation leaves no account;
- failure after credential creation removes the credential;
- failure after staged home creation removes the staging tree;
- failure while writing profile/resources removes credential and staging tree;
- publication failure restores absence of the target home;
- successful creation publishes exactly one ACTIVE account;
- retry after any failed attempt succeeds without manual cleanup.

## Credential-store requirements

The lifecycle service requires explicit account-authority operations that Server 0.13 lacks:

- lookup metadata without authenticating as the user;
- delete credential by exact userId;
- enumerate or resolve login/userId for future operator commands;
- preserve atomic file replacement and legacy-record compatibility.

These methods are lifecycle infrastructure, not a web administration API.

## Scope of 0.14.2

In scope:

- `AccountPolicyReactor` and TRUSTED public-registration rejection;
- structured `REGISTRATION_DISABLED` response;
- `AccountLifecycleService` and its injected lifecycle ports;
- explicit credential deletion needed for compensation;
- failure-atomic complete ACTIVE account creation;
- focused tests and permanent workflow qualification.

Out of scope:

- `PendingRegistrationStore` and public EMAIL_VERIFIED activation flow;
- resend/change-email/cancel pending actions;
- `kanger-admin` executable and local admin listener;
- browser UI changes;
- changing verified-email profile rules;
- physical purge of quarantined accounts.

## Completion criteria

0.14.2 closes only when:

- TRUSTED new registration is rejected before the legacy processor;
- the response contains stable code `REGISTRATION_DISABLED`;
- the unified lifecycle service can create a complete ACTIVE account failure-atomically;
- compensation and retry are proven by focused tests;
- existing Server 0.13 accounts continue to authenticate unchanged;
- all permanent workflows pass on the exact closure HEAD;
- `develop/server/0.14.2` is created as an immutable shelf.
