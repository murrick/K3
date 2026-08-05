# KANGER Server 0.14 — VPS acceptance closure

Date: 2026-08-05
Repository: `murrick/K3`
PR: `#63 — KANGER Server 0.14: VPS deployment and acceptance`
Operational branch: `ops/server/0.14-vps-acceptance`
Immutable source shelf: `develop/server/0.14 @ e5f9a1bfa47437636705f0935cb659cffb4d179e`

## Closure status

```text
SERVER 0.14 VPS ACCEPTANCE: TECHNICALLY COMPLETE
PR #63:                      OPEN / READY FOR REVIEW
MERGE:                       NOT PERFORMED
```

All production acceptance gates defined for PR #63 have passed. Remaining observations are classified as non-blocking operational or application-security debt and are explicitly separated from the Server 0.14 deployment artifact.

## Production target

```text
host:       murray@94.103.94.41:4211
public UI:  https://kanger.org/
public API: https://api.kanger.org/
service:    kanger-server.service
application plane: 127.0.0.1:1964 behind nginx
operator plane:    127.0.0.1:1965 through sudo kanger-admin
```

## Final installed identity

```text
server version: server-0.14
source shelf:   develop/server/0.14
git commit:     e5f9a1bfa47437636705f0935cb659cffb4d179e
JAR SHA-256:    e089497d0a8f041a872a3a5a09581f8d94f5962a277794747b7f54e209882a19
config SHA-256: 3f6b95b4cd567d946e0d6d4c634b0db6f2f3c6aa5d7c594a62f4a715c53209dc
runtime policy: EMAIL_VERIFIED
health:         UP
readiness:      READY
```

The configuration hash is the exact restored SMTPS production baseline.

## Final browser UI identity

```text
current target:  /home/murray/sites/kanger-server-0.14-20260804T181706Z
previous target: /home/murray/sites/kanger
index SHA-256:   747104f9e9a7599679b5329eb098590b190a876ff949f01e232f67a1e1b8baae
config SHA-256:  d44e67c862f12065d62259055043922ff573271dd5fac2f373cc454172757715
console SHA-256: 24d74b6608d4d49a5464d62dd368354c3f30d5f90b90b6a8717531d8e7719dca
origin UI:       PASS
public UI:       PASS
```

The previous UI target remains available as an explicit rollback boundary.

## Closed acceptance gates

```text
read-only VPS inventory:                    PASS
local exact-shelf qualification:            PASS
off-host predeployment backup:              PASS
guarded Server 0.13 -> 0.14 cutover:         PASS
production public/operator boundary:        PASS
guarded browser UI cutover:                 PASS
origin and public UI verification:           PASS
SMTPS delivery and lifecycle:               PASS
TRUSTED registration-policy lifecycle:      PASS
STARTTLS delivery and lifecycle:             PASS
restoration to exact SMTPS baseline:         PASS
controlled service restart:                 PASS
controlled same-version update:             PASS
manual rollback to Server 0.13:              PASS
restoration from Server 0.13 to Server 0.14: PASS
durable-state metadata non-interference:    PASS
nginx routing and syntax:                    PASS
HTTPS certificates and redirects:           PASS
CORS allow/deny policy:                      PASS
application listener confinement:           PASS
operator listener confinement:              PASS
public readiness rejection:                 PASS
legacy default-site exposure removal:       PASS
public PHP exposure removal:                PASS
```

## Account and transport acceptance

The following lifecycle was observed under production SMTP/SMTPS and STARTTLS transport:

```text
registration
  -> persistent pending intent
  -> confirmation e-mail delivery
  -> explicit confirmation
  -> complete ACTIVE account publication
  -> separate ordinary login
  -> authenticated browser session
  -> operator deletion
  -> credential revocation
  -> runtime/session revocation
  -> canonical workspace quarantine
  -> fresh authentication rejection
```

TRUSTED policy acceptance separately proved that public registration was hidden and rejected while operator-created accounts could log in and be deleted through the supported operator boundary.

The Server 0.14 runtime was finally restored to:

```text
registration_policy=EMAIL_VERIFIED
public_registration=true
email_confirmation_required=true
confirmation_creates_session=false
pending_registration_actions=true
email transport=SMTPS
```

## Update and rollback acceptance

The standard installer rotates the current JAR into `kanger-server.jar.previous`. Before same-version update, the accepted Server 0.13 rollback boundary was therefore preserved independently:

```text
/opt/kanger-server/rollback/server-0.13-4eb4bd33
```

The exact Server 0.14 JAR was then reinstalled through the standard installer. The service restarted onto the same accepted binary, while configuration, systemd unit, operator wrapper and durable-state metadata remained unchanged.

Manual rollback then proved:

```text
Server 0.14 -> Server 0.13: PASS
Server 0.13 health/readiness: UP / READY
Server 0.13 public health:    PASS
Server 0.13 public /ready:    HTTP 403
Server 0.13 -> Server 0.14:   PASS
final Server 0.14 identity:   exact accepted JAR/config hashes
```

Relevant protected restoration snapshots include:

```text
/opt/kanger-server/rollback/server-0.14-pre-same-version-20260805T065524Z
/opt/kanger-server/rollback/server-0.14-pre-manual-rollback-20260805T071605Z
/opt/kanger-server/rollback/server-0.13-pre-restore-0.14-20260805T073739Z
```

## Final network and perimeter acceptance

The final perimeter audit proved:

```text
nginx active/enabled and syntactically valid
kanger.org and www.kanger.org UI routing valid
api.kanger.org upstream resolves only to loopback port 1964
operator port 1965 absent from nginx routing
TLS hostname validation passed for all three public names
HTTP redirects to HTTPS
UI public/origin hashes match the accepted artifact
API public/origin health reports Server 0.14 / UP
public /ready returns HTTP 403
CORS allows only kanger.org and www.kanger.org
external and null origins are rejected
CORS credentials remain disabled
```

### Legacy default-site closure

The final audit found an enabled default nginx virtual host exposing legacy `/var/www/html/info.php` and `/var/www/html/test.php` artifacts through the host IP. Classification proved that this was a real default-site/PHP surface, not leakage through the KANGER API virtual host.

The exposure was closed under a guarded mutation:

```text
disabled:   /etc/nginx/sites-enabled/default
quarantine: /opt/kanger-server/rollback/nginx-default-site-20260805T083900Z
nginx:      reload only
KANGER:     no restart
```

The default configuration and both artifacts remain retained with a manifest and checksums. The public root no longer contains the PHP artifacts, raw-IP requests no longer expose or execute them, and all KANGER health, UI, CORS and listener checks remained unchanged.

## Residual non-blocking observations

### Confirmation link semantics

The confirmation link still uses a state-changing GET action. Mail scanners or link-prefetch mechanisms can consume the one-time token before a human follows the link. A future design should use an inert GET landing page followed by an explicit state-changing POST and an idempotent already-confirmed result.

This finding does not invalidate the completed lifecycle acceptance.

### Unknown API path semantics

Unknown API paths currently return a generic JSON fallback with HTTP 200. Paths such as `/info.php`, `/test.php` and a random unknown audit path returned identical responses, proving that no default-root or PHP-handler leakage was present through `api.kanger.org`.

HTTP unknown-path status semantics remain a future application/security improvement.

### Host firewall posture

UFW is absent and the observed nftables/iptables filter policies remain permissive. This posture predates Server 0.14 and is not a deployment blocker because both KANGER planes are confined to loopback and the operator plane is not proxied by nginx.

Firewall policy remains separate host-hardening debt. Existing SSH, VPN, Tailscale and Docker endpoints are outside this artifact and were not modified.

### Build provenance metadata

The accepted JAR exposes the version and source branch but its embedded source-commit property is empty. The artifact remains identified by its exact accepted SHA-256 and immutable source shelf. Future builds should embed the full source commit in build metadata.

## Evidence index

Detailed sanitized evidence remains in sibling files under `kanger-server/docs/operations/evidence/`, including:

- production cutover and boundary acceptance;
- browser UI cutover;
- SMTP/SMTPS account acceptance;
- account deletion acceptance;
- final update, rollback and perimeter acceptance:
  `server-0.14-update-rollback-perimeter-acceptance.md`.

No SMTP password, TLS private key, owner token, confirmation token, session token or account-state content is committed.

## Final PR closure decision

The operational acceptance scope of PR #63 is complete. PR #63 was explicitly marked Ready for review after technical closure.

```text
TECHNICAL ACCEPTANCE DECISION: PASS
PR REVIEW STATE:               READY FOR REVIEW
ELIGIBLE TO MERGE:              YES, after the normal final repository review
MERGE:                          NOT PERFORMED
```

Merging PR #63 remains a separate explicit repository action.

## Repository boundary

```text
base: develop/server/0.14 @ e5f9a1bfa47437636705f0935cb659cffb4d179e
head before readiness-state synchronization:
      29084f732fc44422fec13c67ecc171f81c8907dc
```

This document is the authoritative final closure record for KANGER Server 0.14 VPS acceptance.