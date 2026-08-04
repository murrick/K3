# KANGER Server 0.14.5 — public authentication UI boundary

Date: 2026-08-04

Status: OPEN

## Integration boundary

```text
base shelf: develop/server/0.14.4
base commit: df870583662a8a1a6811b89bbe9ed12ee6f5caef
working branch: server/0.14-account-lifecycle
draft PR: #62
```

## Purpose

Align the public browser authentication surface with the account lifecycle that
Server 0.14.1–0.14.4 already enforces.

The browser is a policy projection, not a second policy owner.

## Governing topology

```text
TRUSTED
→ public registration is absent
→ account provisioning belongs to kanger-admin
→ ordinary login creates a session

EMAIL_VERIFIED
→ public registration creates PendingRegistration only
→ confirmation creates a complete ACTIVE account without a session
→ ordinary login creates the session
→ pending actions use only the scoped pending-action token
```

## Capability contract

The ordinary public `version` response exposes a stable `auth` object:

```json
{
  "registration_policy": "TRUSTED | EMAIL_VERIFIED",
  "public_registration": true,
  "email_confirmation_required": true,
  "confirmation_creates_session": false,
  "pending_registration_actions": true
}
```

The response exposes product capabilities, not SMTP mode, credentials or
transport details. The same resolved `RegistrationPolicy` instance drives both
enforcement and capability publication.

## Browser boundary

The authentication gateway must:

- fetch capabilities before presenting registration actions;
- never display Register in TRUSTED mode;
- treat successful EMAIL_VERIFIED registration as `PENDING_CONFIRMATION`, not
  as login or session creation;
- treat confirmation as activation followed by a separate ordinary login;
- handle `EMAIL_CONFIRMATION_REQUIRED` without storing an application session;
- hold the scoped pending-action token only for resend, e-mail change and cancel;
- rotate the scoped token when the server returns a replacement;
- keep errors visible instead of immediately reloading the page;
- use text-only rendering for server descriptions and hints;
- load the existing owner console only after a valid application session exists.

## Console preservation strategy

The historical combined page is preserved byte-for-byte as `html/console.html`.
A new `html/index.html` becomes the authentication gateway and loads that
console payload only after login. The gateway injects the selected API endpoint
into the preserved console document at load time.

This isolates auth topology without redesigning the owner console or semantic
workspace UI.

## Security boundary

- no raw password or pending token is written to logs or visible markup;
- passwords remain only in form fields for the duration of the request;
- pending-action tokens remain in page memory and `sessionStorage`, never in a
  URL or persistent cookie;
- all server-provided text is rendered with `textContent`;
- the console is not loaded before session creation;
- the public UI contains no operator routes or admin bearer material.

## Deliberately excluded

Server 0.14.5 does not redesign:

- the authenticated owner-console layout;
- command parsing, result panes or snapshot consistency;
- destructive database-operation confirmations;
- application-user roles or web administration;
- server-side HttpOnly session cookies;
- password reset or account recovery;
- deployment-specific CDN or frontend build tooling.

Those remain separate product/security artifacts. This stage only removes the
browser contradictions with the already-qualified Server 0.14 account model.

## Qualification gates

Closure requires:

1. focused capability tests for TRUSTED and EMAIL_VERIFIED;
2. Java 8 and Java 21 server corpus green;
3. static UI contract gate proving policy-aware registration and pending-action
   handling without legacy confirmation/session assumptions;
4. live TRUSTED smoke proving the version capability snapshot and disabled
   public registration;
5. all permanent KANGER workflows green on the exact closure HEAD;
6. immutable shelf `develop/server/0.14.5` only after exact qualification.
