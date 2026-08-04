# KANGER Server 0.14 — SMTPS account lifecycle acceptance

Date: 2026-08-04
Target: production VPS `94.103.94.41`
Server: `server-0.14`
Browser UI: exact Server 0.14 snapshot
Mail mode: `smtps`

## Observed sequence

1. Public registration completed and produced a pending EMAIL_VERIFIED registration.
2. A confirmation e-mail was delivered through the production SMTPS path.
3. Opening the confirmation URL in the user's browser returned:

```json
{
  "result": "error",
  "code": "CONFIRMATION_TOKEN_INVALID",
  "description": "Confirmation token is invalid",
  "server_version": "server-0.14"
}
```

4. The user then performed an ordinary sign-in with the newly registered credentials.
5. Sign-in succeeded and the authenticated KANGER console opened.

No login, e-mail address, password, session token, confirmation token, or confirmation URL is recorded in this evidence.

## Acceptance result

```text
SMTPS delivery:                     PASS
pending registration:              PASS
account activation/publication:    PASS
ordinary credential login:         PASS
session creation:                  PASS
authenticated console load:        PASS
confirmation browser UX:           FINDING
```

The successful ordinary login proves that the pending registration had already been activated into a complete ACTIVE account before the browser received `CONFIRMATION_TOKEN_INVALID`. Confirmation did not create a browser session; the separate ordinary login did.

## Production finding

Server 0.14 confirmation is a state-changing `GET /?confirm=<token>` operation. After successful activation, the pending record is removed. A subsequent request using the same token therefore receives `CONFIRMATION_TOKEN_INVALID`; the current response does not distinguish an unknown token from an already consumed token.

The first confirmation request may have been issued by a mail security scanner, link preview, browser prefetch, or an earlier user-agent request. The available acceptance evidence proves prior token consumption and successful activation, but does not identify which client consumed it.

## Classification

This does not invalidate the Server 0.14 account lifecycle artifact:

```text
pending intent
→ e-mail confirmation
→ complete ACTIVE account
→ separate ordinary login
→ application session
```

It is an operational/UX and confirmation-protocol finding for a follow-up artifact. A safer confirmation design should avoid irreversible account activation directly on a prefetchable GET request and should provide an idempotent already-confirmed outcome where possible.
