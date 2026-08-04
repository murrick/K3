# KANGER Server 0.14.5 — public authentication UI boundary closure

Date: 2026-08-04

Status: CLOSED, pending exact closure-HEAD qualification

## Integration boundary

```text
base shelf: develop/server/0.14.4
base commit: df870583662a8a1a6811b89bbe9ed12ee6f5caef
working branch: server/0.14-account-lifecycle
functional head: 5bf5bd24bc4e563b1a18d236d2f93cc3c3a80205
draft PR: #62
```

## Scope completed

Server 0.14.5 aligns the public browser authentication surface with the
account-lifecycle authority established in Server 0.14.1–0.14.4.

Completed:

- introduced a stable public authentication capability snapshot on the
  ordinary `version` response;
- derived capability publication and policy enforcement from the same resolved
  `RegistrationPolicy` instance;
- removed public Register from TRUSTED deployments;
- represented EMAIL_VERIFIED registration as pending intent rather than account
  or session creation;
- represented e-mail confirmation as account activation followed by a separate
  ordinary login;
- handled `EMAIL_CONFIRMATION_REQUIRED` without creating or storing an
  application session;
- implemented scoped pending-registration resend, e-mail change and cancel
  actions;
- rotated the in-memory/session-scoped pending action token when returned by the
  server;
- kept authentication errors visible instead of reloading the page;
- rendered server descriptions and address hints as text rather than HTML;
- loaded the authenticated owner console only after an existing session passes
  the public `ping` boundary;
- preserved the historical owner-console payload byte-for-byte as
  `html/console.html`;
- introduced `html/config.js` as the explicit browser API endpoint boundary;
- added focused server capability tests, static UI contract tests and a live
  TRUSTED capability smoke.

## Public capability contract

The ordinary `version` response now contains:

```json
{
  "auth": {
    "registration_policy": "TRUSTED | EMAIL_VERIFIED",
    "public_registration": true,
    "email_confirmation_required": true,
    "confirmation_creates_session": false,
    "pending_registration_actions": true
  }
}
```

The object exposes product topology, not SMTP transport configuration.

### TRUSTED

```text
public_registration=false
email_confirmation_required=false
confirmation_creates_session=false
pending_registration_actions=false
```

The browser does not present Register. Account creation remains a host-operator
operation through `kanger-admin`.

### EMAIL_VERIFIED

```text
public_registration=true
email_confirmation_required=true
confirmation_creates_session=false
pending_registration_actions=true
```

Registration creates `PendingRegistration`; confirmation creates the complete
ACTIVE account; ordinary login creates the application session.

## Browser topology

```text
html/index.html
→ fetch public auth capabilities
→ login / conditional registration / pending actions
→ validate application session through command ping
→ load preserved html/console.html
```

The former combined `html/index.html` was copied without content changes to
`html/console.html`. Its blob identity on the functional head is:

```text
8584fe7c653e07441c8bf53b2a023b489f04d494
```

The new gateway injects the configured API endpoint into that preserved payload
at load time and supplies a document base for the existing relative assets.
The semantic workspace, command parser, panes and owner-console behavior were
not redesigned in this stage.

## Pending-registration UX

A pending login returns no application token. The gateway accepts only the
server's scoped `pending_action_token` and uses it for:

- resend confirmation;
- change the unconfirmed e-mail address;
- cancel the pending registration.

The token is held in page memory and `sessionStorage`, never placed in the URL
or persistent application cookie. A replacement token returned by resend or
e-mail-change rotation replaces the previous token.

Initial mail-delivery failure still leaves retryable pending intent. The gateway
instructs the user to sign in with the same pending credentials to obtain the
scoped action token.

## Confirmation UX

The browser no longer consumes `?confirm=<raw-token>` and no longer expects
confirmation to return a session token. Confirmation belongs to the server root
GET boundary. After redirect, the gateway presents ordinary login; it also
recognizes deployment redirect markers `confirmation=success` and
`confirmed=true` and displays:

```text
E-mail confirmed. Sign in to create a session.
```

No automatic login occurs.

## Security boundary

The new unauthenticated gateway:

- does not use `innerHTML` for server-originated content;
- clears password fields after each request;
- does not log passwords or scoped pending tokens;
- does not persist pending tokens beyond `sessionStorage`;
- does not load the owner console before session validation;
- contains no admin route, admin bearer token or host-operator capability;
- fails closed by hiding public registration when capabilities are absent,
  malformed or unknown.

The existing JavaScript-readable application-token cookie and legacy
`innerHTML` surfaces inside the preserved authenticated owner console are not
claimed as corrected here. They remain a separate security-hardening artifact.
Server 0.14.5 removes authentication-topology contradictions; it does not
redesign the complete session transport or console renderer.

## Functional-head qualification

All permanent workflows completed successfully on functional head
`5bf5bd24bc4e563b1a18d236d2f93cc3c3a80205`:

```text
KANGER qualification isolation     run 30915958754  success
KANGER Server                      run 30915959019  success
KANGER CI                          run 30915958762  success
KANGER III semantic planning       run 30915959561  success
KANGER III storage optimization    run 30915958560  success
KANGER III DUMB reliability        run 30915958819  success
```

KANGER Server passed on Java 8 and Java 21.

```text
Tests run: 155
Failures: 0
Errors: 0
Skipped: 0
```

The focused additions are:

- four `PublicAuthCapabilitiesReactorTest` cases;
- one `PublicAuthUiContractTest` case.

Java 21 additionally proved the complete live topology:

```text
server bootstrap
→ GET/POST version capability snapshot
→ exact TRUSTED capability values
→ public registration rejection
→ no rejected credential publication
→ pre-cutover credential migration login
→ authenticated ping
→ logout and closed-token rejection
→ second login with token rotation
→ kanger-admin create-user
→ ordinary public login
→ kanger-admin delete-user
→ credential/session revocation
→ exact quarantine and COMPLETE journal
→ public listener rejects admin dispatch
→ clean shutdown
```

The standalone shaded JAR, deployment assets and Java 21 artifact upload also
completed successfully.

## Diff and preservation audit

Relative to `develop/server/0.14.4`, the functional stage changes only:

- browser gateway/configuration and preserved console placement;
- public capability reactor and its composition boundary;
- focused tests and live auth smoke;
- stage documentation.

Account publication/deletion, credentials, pending-registration persistence,
mail transport, admin listener, semantic core and storage engines were not
changed.

## Deliberately excluded

Server 0.14.5 does not introduce:

- a web administration console or application-user RBAC;
- password reset or account recovery;
- HttpOnly server-managed session cookies;
- authenticated console XSS remediation;
- owner-console snapshot consolidation;
- destructive database-operation confirmations;
- semantic workspace or command-parser redesign;
- frontend bundling/CDN architecture;
- final Server 0.14 version-identity promotion or merge.

These are independent product, security or operational artifacts.

## Closure boundary

The functional Server 0.14 feature set is now complete through 0.14.5. The next
step is final Server 0.14 integration qualification and version-identity
promotion, followed by the immutable three-digit shelf:

```text
develop/server/0.14
```

PR #62 remains Draft and unmerged until that final integration boundary is
qualified and explicitly approved.

The closure changes after the qualified functional head are documentation-only.
The immutable shelf `develop/server/0.14.5` may be created only after all
permanent workflows pass on the exact closure HEAD.
