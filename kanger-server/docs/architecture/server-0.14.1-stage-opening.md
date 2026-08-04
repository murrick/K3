# KANGER Server 0.14.1 — account lifecycle contracts

Date: 2026-08-04

Status: OPEN

Base shelf:

```text
develop/server/0.13
db439e9c20835e9d918b19be216a598320458acd
```

Working branch:

```text
server/0.14-account-lifecycle
```

## Purpose

Server 0.14 separates public account registration from local operator provisioning and makes account creation an explicit lifecycle boundary.

The stage introduces contracts before changing registration behavior. Existing Server 0.13 accounts remain valid ACTIVE accounts and are not migrated, removed or converted into pending records.

## Governing invariant

```text
A KANGER User exists only as a complete ACTIVE account.
```

Before activation there must be no credential account, user id, user home, DB, UDF runtime, history or authenticated session.

## Registration policies

`server.email.mode` is resolved once at the configuration boundary:

```text
disabled          -> RegistrationPolicy.TRUSTED
starttls | smtps  -> RegistrationPolicy.EMAIL_VERIFIED
```

Production code must use `RegistrationPolicy`; transport-mode strings must not be scattered through account workflows.

### TRUSTED

Public self-registration is disabled. Accounts are provisioned by the local server operator through `kanger-admin` and become ACTIVE immediately.

### EMAIL_VERIFIED

Public registration creates only a persistent `PendingRegistration`. Confirmation activates the account. Confirmation does not create a session; ordinary login creates the session afterwards.

## Public and operator planes

```text
Public user plane
  login
  profile
  query / DB / UDF
  self-registration only in EMAIL_VERIFIED

Local operator plane
  kanger-admin
  create-user
  delete-user
  later: list-users, reset-password
```

A KANGER application user is not a KANGER server operator. No web role or RBAC model is introduced in Server 0.14.

## Required error codes

The public contract must use structured codes rather than interpreting human-readable descriptions:

```text
REGISTRATION_DISABLED
AUTHENTICATION_FAILED
EMAIL_CONFIRMATION_REQUIRED
CONFIRMATION_TOKEN_INVALID
CONFIRMATION_TOKEN_EXPIRED
EMAIL_ALREADY_USED
LOGIN_ALREADY_USED
RESEND_RATE_LIMITED
MAIL_DELIVERY_UNAVAILABLE
```

## Existing implementation boundary

At the Server 0.13 baseline:

- `UserFactory.createUser()` creates a credential and resolves/creates the physical user home;
- confirmation tokens are bound to already allocated users;
- `CredentialStore` supports authenticate/create/update but not delete;
- `UserFactory.dropUser()` closes runtime sessions but does not delete an account;
- login may issue a session for an unconfirmed account;
- `server.email.mode` configures mail transport but does not define registration policy.

These are characterization facts, not the target model.

## Migration contract

All credentials present at upgrade time are treated as existing ACTIVE accounts.

The upgrade must not:

- delete existing credentials;
- remove or relocate existing user homes;
- require retrospective email confirmation;
- rewrite DB/UDF data;
- infer pending registrations from existing accounts.

Historical Server 0.13 confirmation tokens may be invalidated at the explicit migration boundary.

## Stage sequence

### 0.14.1 — contracts and boundaries

- introduce `RegistrationPolicy`;
- introduce structured account error codes;
- characterize current account creation and confirmation coupling;
- define migration and state-machine contracts;
- do not switch production registration flow yet.

### 0.14.2 — unified account lifecycle

- introduce `AccountLifecycleService` as the sole owner of physical account creation/deletion;
- make ACTIVE account creation failure-atomic;
- disable public registration in TRUSTED at the server boundary.

### 0.14.3 — pending registration

- introduce persistent transient `PendingRegistrationStore`;
- create no account before confirmation;
- implement resend, pending-email replacement, cancellation, expiry and restart recovery.

### 0.14.4 — local operator plane

- introduce `kanger-admin`;
- support interactive, flag-based and mixed parameter input;
- implement `create-user` and safe `delete-user` through the same lifecycle service.

### 0.14.5 — public auth UI boundary

- expose registration policy/capabilities;
- remove Register from TRUSTED UI;
- align EMAIL_VERIFIED registration, confirmation and login UX;
- keep the broader owner-console stabilization for Server 0.15.

## Stage discipline

Each stage follows:

```text
characterization
-> red regression where behavior changes
-> minimal production change
-> focused tests
-> full permanent workflows
-> closure document
-> develop/server/0.14.x shelf
-> synchronize 00_KANGER / 05_KANGER / 06_KANGER
```

The main pull request remains draft until the complete Server 0.14 artifact is qualified.

## Non-goals

Server 0.14 does not introduce:

- web administrator roles;
- RBAC or capabilities delegated to application users;
- automatic login from a confirmation link;
- a limited DB-less/UDF-less account state;
- full browser owner-console redesign;
- SMART DB administration.
