# KANGER Server 0.14.1 — contracts and boundaries closure

Date: 2026-08-04

Status: CLOSED, pending exact closure-HEAD qualification

## Integration boundary

```text
base branch: develop/server/0.13
base commit: db439e9c20835e9d918b19be216a598320458acd
working branch: server/0.14-account-lifecycle
functional head: cffaa248c9bd82be6323e3748811ddacf0ae8c6f
draft PR: #62
```

## Scope completed

Server 0.14.1 establishes the account lifecycle contract without switching the production registration workflow.

Completed:

- introduced `RegistrationPolicy.TRUSTED`;
- introduced `RegistrationPolicy.EMAIL_VERIFIED`;
- mapped `server.email.mode=disabled` to TRUSTED;
- mapped `server.email.mode=starttls|smtps` to EMAIL_VERIFIED;
- made absent, empty or unsupported modes fail closed;
- introduced stable machine-readable `AccountErrorCode` values;
- documented the configuration topology in `deploy/kanger.conf.example`;
- recorded the Server 0.13 lifecycle surface through characterization tests;
- fixed the migration rule that all existing credentials remain ACTIVE accounts;
- fixed the invariant that `PendingRegistration` is not a User or account state;
- fixed the separation between public user plane and local operator plane;
- excluded web roles/RBAC from the current artifact.

## Governing invariant

```text
A KANGER User exists only as a complete ACTIVE account.
```

Before activation there is no credential account, user id, user home, DB, UDF runtime, history or authenticated session.

## Characterized Server 0.13 boundary

The executable characterization records that:

- `UserFactory.createUser(login, password)` returns an `IUser` and therefore combines credential creation with user resolution/home creation;
- `UserFactory.token(login, password)` and `getUserByToken(token)` bind confirmation to an already allocated user;
- `CredentialStore` has authenticate/create/update but no delete contract;
- `UserFactory.dropUser(...)` is runtime/session closure rather than physical account deletion.

These tests identify the migration starting point. They are not the target lifecycle and may be replaced by target-state gates when the legacy entrypoints are deliberately removed.

## Migration contract

Every credential present when upgrading from Server 0.13 is treated as an existing ACTIVE account.

Server 0.14 must not retrospectively:

- require e-mail confirmation for an existing account;
- convert an existing account to PendingRegistration;
- delete or relocate an existing user home;
- rewrite DB/UDF data;
- remove existing credentials.

## Functional-head qualification

All permanent workflows completed successfully on functional head
`cffaa248c9bd82be6323e3748811ddacf0ae8c6f`:

```text
KANGER qualification isolation     run 30894283370  success
KANGER Server                      run 30894283061  success
KANGER CI                          run 30894285725  success
KANGER III semantic planning       run 30894284762  success
KANGER III storage optimization    run 30894283111  success
KANGER III DUMB reliability        run 30894283153  success
```

The KANGER Server workflow passed on Java 8 and Java 21. The Java 21 job also passed authenticated loopback and shutdown smoke.

## Deliberately unchanged

Server 0.14.1 does not yet change:

- `QueryProcessor` registration/login behavior;
- confirmation token persistence or ownership;
- physical account creation;
- public Register UI visibility;
- session issuance;
- credential deletion;
- user-home deletion;
- server artifact version identity.

These changes belong to later Server 0.14 stages and require red target-state regressions first.

## Next stage

Server 0.14.2 introduces `AccountLifecycleService` as the single owner of complete ACTIVE account creation and deletion. It must provide failure-atomic creation and enforce `REGISTRATION_DISABLED` for public registration in TRUSTED mode.

The closure commit is documentation-only relative to the qualified functional head. The stage shelf `develop/server/0.14.1` may be created only after all permanent workflows pass on the exact closure HEAD.
