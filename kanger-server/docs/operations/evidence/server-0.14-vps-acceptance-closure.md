# KANGER Server 0.14 — VPS acceptance closure checkpoint

Date: 2026-08-04
Repository: `murrick/K3`
Draft PR: `#63 — KANGER Server 0.14: VPS deployment and acceptance`
Operational branch: `ops/server/0.14-vps-acceptance`
Immutable source shelf: `develop/server/0.14 @ e5f9a1bfa47437636705f0935cb659cffb4d179e`

## Production target

```text
host:       murray@94.103.94.41:4211
public:     https://kanger.org/
service:    kanger-server.service
public API: 127.0.0.1:1964 behind nginx/Cloudflare
operator:   127.0.0.1:1965 through sudo kanger-admin
```

## Installed server identity

```text
server version: server-0.14
source commit:  e5f9a1bfa47437636705f0935cb659cffb4d179e
JAR SHA-256:   e089497d0a8f041a872a3a5a09581f8d94f5962a277794747b7f54e209882a19
health:        UP
readiness:     READY
```

The previous `server-0.13` artifact and pre-0.14 configuration/systemd rollback copies remain retained.

## Installed browser UI identity

```text
previous target: /home/murray/sites/kanger
current target:  /home/murray/sites/kanger-server-0.14-20260804T181706Z
index SHA-256:   747104f9e9a7599679b5329eb098590b190a876ff949f01e232f67a1e1b8baae
config SHA-256:  d44e67c862f12065d62259055043922ff573271dd5fac2f373cc454172757715
console SHA-256: 24d74b6608d4d49a5464d62dd368354c3f30d5f90b90b6a8717531d8e7719dca
origin UI:       PASS
public UI:       PASS
```

The previous UI target remains available as rollback evidence.

## Closed production gates

```text
read-only VPS inventory:                 PASS
local exact-shelf qualification:         PASS
off-host predeployment backup:           PASS
guarded server cutover:                  PASS
production public/operator boundary:     PASS
guarded browser UI cutover:              PASS
origin and public UI verification:       PASS
SMTPS delivery:                          PASS
EMAIL_VERIFIED pending registration:     PASS
confirmation and ACTIVE publication:     PASS
confirmation_creates_session=false:      PASS
ordinary login and console opening:      PASS
logout and repeated login:               PASS
operator account deletion:               PASS
canonical workspace quarantine:          PASS
active browser-session revocation:       PASS
fresh post-deletion login rejection:     PASS
repeat deletion idempotency:             PASS
monotonic user-id allocation:             PASS
```

## Account lifecycle acceptance interpretation

The production lifecycle was observed end-to-end:

```text
registration
  -> persistent pending intent
  -> e-mail confirmation
  -> complete ACTIVE account publication
  -> separate ordinary login
  -> authenticated session
  -> operator deletion
  -> credential revocation
  -> runtime/session revocation
  -> canonical workspace quarantine
  -> fresh authentication rejection
```

A temporary diagnostic confusion was traced to browser autocomplete: a later registration used an e-mail address as the login string, creating a distinct ACTIVE identity with a new monotonically allocated user id. No identity reuse, orphan workspace, or credential/workspace disagreement was present.

## Open production findings

### 1. Confirmation link is a state-changing GET

The confirmation e-mail points directly to a prefetchable `GET /?confirm=<token>` action. A mail scanner, preview, or link-prefetch mechanism can consume the one-time token before the human opens the link. Reopening the consumed link returns `CONFIRMATION_TOKEN_INVALID`, even though the account may already be ACTIVE.

Recommended future fix:

```text
GET confirmation landing page
  -> explicit user confirmation
  -> state-changing POST
  -> idempotent already-confirmed outcome
```

This finding does not invalidate the completed account lifecycle acceptance.

### 2. Default nginx document-root artifacts require later review

`/var/www/html/info.php` and `/var/www/html/test.php` were observed outside the KANGER UI root. Their enabled-site reachability must be audited before any deletion decision. No removal was performed under this acceptance artifact.

## Remaining PR #63 acceptance scope

The following gates remain open and must be continued in a new work session:

1. `TRUSTED` registration-policy acceptance:
   - public registration hidden/disabled;
   - operator-created account;
   - ordinary login;
   - operator deletion.
2. STARTTLS transport acceptance, followed by restoration of SMTPS.
3. Controlled service restart acceptance.
4. Controlled same-version update acceptance.
5. Manual rollback rehearsal and restoration to Server 0.14.
6. Final nginx/HTTPS/CORS/firewall and enabled-site audit.
7. Classification of residual findings and final PR closure decision.

Changing production registration policy, restarting the service, performing rollback, deleting files, marking PR #63 ready, or merging it remain explicit approval boundaries.

## Current repository boundary

```text
PR #63: open, Draft, mergeable
base:   develop/server/0.14 @ e5f9a1bfa47437636705f0935cb659cffb4d179e
head before this closure commit:
        951be60009d3f54ac010e1d5cbd7e590e06d0bad
```

This document is the authoritative continuation checkpoint for the next KANGER III chat. Detailed proof remains in the sibling evidence files under `kanger-server/docs/operations/evidence/`.
