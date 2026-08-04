# KANGER Server 0.14 — final integration closure

Date: 2026-08-04

Status: CLOSED, pending exact closure-HEAD qualification

## Artifact boundary

```text
base shelf: develop/server/0.13
base SHA: db439e9c20835e9d918b19be216a598320458acd
working branch: server/0.14-account-lifecycle
latest stage shelf: develop/server/0.14.5
latest stage SHA: 7f7b0c198c57b30f37e741c031954a714f776829
functional integration HEAD: f0032988d97cb2206bdd773cb0c67336b3ffcaab
draft PR: #62
```

## Monotonic stage composition

The complete Server 0.14 artifact is one linear sequence:

```text
develop/server/0.13
→ develop/server/0.14.1
→ develop/server/0.14.2
→ develop/server/0.14.3
→ develop/server/0.14.4
→ develop/server/0.14.5
→ final Server 0.14 integration
```

Every qualified stage shelf is an exact ancestor of the next one with zero
`behind` commits. The working branch and `develop/server/0.14.5` were identical
when final integration opened.

## Governing account invariant

A KANGER `User` exists only as a complete ACTIVE account.

Before activation there is no:

- credential account;
- allocated user id;
- canonical numeric home;
- DB or UDF runtime;
- history;
- authenticated application session.

This invariant is shared by public e-mail-confirmed activation and trusted local
operator provisioning.

## Completed Server 0.14 topology

### TRUSTED

```text
server.email.mode=disabled
→ RegistrationPolicy.TRUSTED
→ no public self-registration
→ Register absent from browser gateway
→ complete ACTIVE account created through kanger-admin
→ ordinary login creates application session
```

There is no password-only public-registration fallback.

### EMAIL_VERIFIED

```text
server.email.mode=starttls | smtps
→ RegistrationPolicy.EMAIL_VERIFIED
→ registration creates persistent transient PendingRegistration only
→ confirmation publishes complete ACTIVE account
→ confirmation creates no application session
→ ordinary login creates session
```

### Local owner plane

```text
host operator
→ sudo kanger-admin
→ 127.0.0.1:1965 + owner-only bearer token
→ AccountLifecycleService
```

The owner listener is loopback-only, absent from nginx and inaccessible through
the public application protocol or browser UI.

### Public application plane

```text
browser / API client
→ nginx
→ 127.0.0.1:1964
→ policy-aware public API
```

The public `version` capability snapshot determines browser registration UX
without exposing SMTP configuration or owner capabilities.

## Lifecycle result

Server 0.14 provides:

- one `AccountLifecycleService` authority for complete account publication and
  deletion;
- persistent transient pending registration with bounded TTL, limits, resend
  cooldown and token rotation;
- no plaintext pending passwords and no raw persisted confirmation/action
  tokens;
- atomic activation ordering and recovery for partially published accounts;
- verified e-mail and login immutability;
- compatibility activation for pre-cutover Server 0.13 credentials;
- persistent monotonic user-id allocation without reuse;
- local operator account creation through the running server;
- forward-only deletion journal:
  - `PREPARED`;
  - `CREDENTIAL_REMOVED`;
  - `HOME_QUARANTINED`;
  - `COMPLETE`;
- session/runtime revocation and canonical-home quarantine;
- prevention of pending registration or confirmation resurrecting an account
  under deletion.

## Public browser boundary

The browser gateway:

- reads auth capabilities from the ordinary `version` response;
- hides Register in TRUSTED mode;
- treats EMAIL_VERIFIED registration as pending intent;
- never treats registration or confirmation as session creation;
- handles `EMAIL_CONFIRMATION_REQUIRED` through a scoped pending action token;
- supports pending resend, unconfirmed e-mail change and cancellation;
- renders server descriptions as text;
- keeps auth failures visible;
- validates an existing session before loading the owner console.

The historical owner console is preserved byte-for-byte as `html/console.html`.
Its authenticated renderer and session transport are not claimed as redesigned.

## Final release identity

Every non-empty public JSON response carries:

```json
{
  "version": "3.3",
  "core_version": "3.3",
  "api_version": "1",
  "server_version": "server-0.14"
}
```

`version` remains the semantic-core compatibility alias. Release tooling verifies
`server_version` and does not fall back to `version`.

Generated build metadata contains:

```properties
branch=server-0.14
server.version=server-0.14
source.branch=<source Git branch>
```

## Installed topology contract

Production verification proves:

```text
systemd service enabled and active
/health reports server-0.14
/ready reports server-0.14
application listener confined to 127.0.0.1:1964
operator listener confined to 127.0.0.1:1965
neither listener bound to 0.0.0.0 or [::]
nginx configuration valid
```

Permanent CI also verifies the operator launcher and canonical admin settings in
the production configuration example.

## Integration diff audit

Relative to `develop/server/0.14.5`, functional integration changed exactly nine
release-boundary files:

```text
.github/workflows/kanger-server.yml
kanger-server/DEPLOYMENT.md
kanger-server/VERSION-CONTRACT.md
kanger-server/deploy/verify-installed.sh
kanger-server/docs/architecture/server-0.14-integration-opening.md
kanger-server/pom.xml
kanger-server/scripts/smoke-local.sh
kanger-server/test/org/kanger/HttpServerTest.java
kanger-server/test/org/kanger/VersionTest.java
```

No account lifecycle, credential, pending store, mail, admin protocol, browser,
semantic core or storage-engine production code changed after the qualified
0.14.5 shelf.

## Functional-head qualification

All permanent workflows passed on functional integration HEAD
`f0032988d97cb2206bdd773cb0c67336b3ffcaab`:

```text
KANGER qualification isolation     run 30919339192  success
KANGER Server                      run 30919342954  success
KANGER CI                          run 30919339983  success
KANGER III semantic planning       run 30919339369  success
KANGER III storage optimization    run 30919339164  success
KANGER III DUMB reliability        run 30919339181  success
```

KANGER Server passed on Java 8 and Java 21:

```text
Tests run: 155
Failures: 0
Errors: 0
Skipped: 0
```

The shaded distribution reports `server-0.14`, deployment assets pass their
permanent assertions, and the Java 21 artifact upload completed successfully.

Java 21 additionally proved:

```text
server bootstrap
→ application listener 127.0.0.1:1964
→ operator listener 127.0.0.1:1965
→ /health and /ready report server-0.14
→ exact TRUSTED public capability snapshot
→ public registration rejection
→ no rejected credential publication
→ pre-cutover Server 0.13 credential login
→ authenticated ping
→ logout and closed-token rejection
→ second login with token rotation
→ kanger-admin create-user
→ ordinary public login
→ kanger-admin delete-user
→ credential/session revocation
→ canonical-home quarantine
→ deletion journal COMPLETE
→ public listener rejects admin dispatch
→ clean shutdown
```

## Qualification correction record

The first integration candidate correctly emitted `server-0.14` but exposed one
remaining test-only `server-0.13` expectation in `HttpServerTest`. The failed run
contained 154 passing tests and one release-identity assertion failure. The
assertion was promoted without production-code changes, and the complete exact
matrix then passed on `f0032988d97cb2206bdd773cb0c67336b3ffcaab`.

## Deliberately excluded future artifacts

Server 0.14 does not claim completion of:

- HttpOnly server-managed application sessions;
- authenticated owner-console XSS remediation;
- replacement of legacy `innerHTML` surfaces;
- password reset and account recovery;
- browser administration or application-user RBAC;
- consistent owner-console snapshots across multiple requests;
- destructive database-operation guardrails;
- owner-console semantic/UI redesign;
- deployment of the qualified artifact to production.

These remain independent security, product and operational artifacts.

## Closure protocol

This file is the only change after the qualified functional integration HEAD.
The exact closure HEAD must pass all permanent workflows before the immutable
three-digit shelf is created:

```text
develop/server/0.14
```

After the shelf is created, PR #62 remains Draft and unmerged until explicit
approval is given to mark it ready or merge it.
