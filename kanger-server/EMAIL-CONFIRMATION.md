# KANGER Server e-mail confirmation

Registration confirmation is a public, one-time operation. It does not require
an authenticated session token and does not create a new authenticated session.

New messages use:

```text
https://api.kanger.org/login?confirm=<opaque-one-time-token>
```

The historical root form remains accepted for already delivered messages:

```text
https://api.kanger.org/?confirm=<opaque-one-time-token>
```

A successful confirmation consumes the token, persists both the explicit
`reg.email.confirmed=true` property and the legacy `reg.agreed=true` property,
and returns HTTP 303 to `server.confirmation.redirect.url` (default
`https://kanger.org/`). The redirect response is non-cacheable and uses
`Referrer-Policy: no-referrer`, so the confirmation token is not forwarded to
the UI.

An invalid, expired or already consumed token remains a JSON error response and
is not redirected. This preserves diagnostics instead of presenting a false
successful login page.
