# KANGER Server 0.14 — VPS acceptance closure

Date: 2026-08-05
Repository: `murrick/K3`
PR: `#63 — KANGER Server 0.14: VPS deployment and acceptance`
Operational branch: `ops/server/0.14-vps-acceptance`
Immutable source commit: `e5f9a1bfa47437636705f0935cb659cffb4d179e`

## Final closure status

```text
SERVER 0.14 VPS ACCEPTANCE: CLOSED / PASS
PR #63:                      CLOSED / MERGED
MERGED OPERATIONAL HEAD:     fe162b9276f0fcd92a23556c6bc5c874b462a789
MERGE COMMIT:                456942653850c8ddd71160efd4648c0cc0289653
```

All production and repository acceptance gates defined for Server 0.14 are complete. There is no remaining continuation boundary inside PR #63. Subsequent work must be opened as a separate artifact.

The post-merge repository update only synchronizes evidence. It did not restart, reconfigure, or otherwise mutate the production host.

## Production target and final identity

```text
host:                murray@94.103.94.41:4211
public UI:           https://kanger.org/
public API:          https://api.kanger.org/
service:             kanger-server.service
server version:      server-0.14
source commit:       e5f9a1bfa47437636705f0935cb659cffb4d179e
JAR SHA-256:         e089497d0a8f041a872a3a5a09581f8d94f5962a277794747b7f54e209882a19
configuration SHA:   3f6b95b4cd567d946e0d6d4c634b0db6f2f3c6aa5d7c594a62f4a715c53209dc
registration policy: EMAIL_VERIFIED
mail transport:      SMTPS
health:              UP
readiness:           READY
application plane:   127.0.0.1:1964 behind nginx
operator plane:      127.0.0.1:1965 through sudo kanger-admin
public readiness:    HTTP 403
```

The configuration hash is the exact restored SMTPS production baseline.

## Browser UI identity

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
guarded browser UI cutover:                  PASS
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
Ready-for-review transition:                 PASS
repository merge:                            PASS
```

## Account and transport acceptance

The production lifecycle was observed end to end:

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

`TRUSTED` policy acceptance separately proved that public registration was hidden and rejected while an operator-created account could log in and be deleted through the supported local operator boundary.

The final runtime capability boundary is:

```text
registration_policy=EMAIL_VERIFIED
public_registration=true
email_confirmation_required=true
confirmation_creates_session=false
pending_registration_actions=true
email_transport=SMTPS
```

## Update, rollback and restoration

The standard installer rotates the current JAR into `kanger-server.jar.previous`. Before the same-version update, the exact Server 0.13 rollback boundary was therefore preserved independently:

```text
/opt/kanger-server/rollback/server-0.13-4eb4bd33
```

The exact Server 0.14 JAR was reinstalled through the standard installer. Configuration, systemd unit, operator wrapper and durable-state metadata remained unchanged.

The manual rehearsal proved:

```text
Server 0.14 -> Server 0.13: PASS
Server 0.13 health/readiness: UP / READY
Server 0.13 public health:    PASS
Server 0.13 public /ready:    HTTP 403
Server 0.13 -> Server 0.14:   PASS
final Server 0.14 identity:   exact accepted JAR/configuration hashes
```

Protected restoration snapshots include:

```text
/opt/kanger-server/rollback/server-0.14-pre-same-version-20260805T065524Z
/opt/kanger-server/rollback/server-0.14-pre-manual-rollback-20260805T071605Z
/opt/kanger-server/rollback/server-0.13-pre-restore-0.14-20260805T073739Z
```

Durable-state metadata remained unchanged through rollback and restoration:

```text
users sequence:        9
canonical home count:  3
quarantine count:      5
```

## Network and perimeter acceptance

The final audit proved:

```text
nginx active/enabled and syntactically valid
kanger.org and www.kanger.org UI routing valid
api.kanger.org upstream resolves only to loopback port 1964
operator port 1965 absent from nginx routing
TLS hostname validation passed for all public names
HTTP redirects to HTTPS
UI public/origin hashes match the accepted artifact
API public/origin health reports Server 0.14 / UP
public /ready returns HTTP 403
CORS allows kanger.org and www.kanger.org
external and null origins are rejected
CORS credentials remain disabled
```

### Legacy default-site closure

The audit found an enabled default nginx virtual host exposing legacy `/var/www/html/info.php` and `/var/www/html/test.php` artifacts through the host IP. Classification proved that this was a real default-site/PHP surface and not leakage through the KANGER API virtual host.

The exposure was closed through a guarded mutation:

```text
disabled:   /etc/nginx/sites-enabled/default
quarantine: /opt/kanger-server/rollback/nginx-default-site-20260805T083900Z
nginx:      reload only
KANGER:     no restart
```

The default configuration and both artifacts remain retained with a manifest and checksums. The public root no longer contains them, raw-IP requests no longer expose or execute them, and all KANGER health, UI, CORS and listener checks remained unchanged.

## Residual non-blocking observations

### Confirmation link semantics

Confirmation still uses a state-changing GET action. Mail scanners or link-prefetch mechanisms may consume the one-time token before a human follows it. A future design should use an inert landing GET, an explicit state-changing POST and an idempotent already-confirmed result.

### Unknown API path semantics

Unknown API paths currently return a generic JSON fallback with HTTP 200. `/info.php`, `/test.php` and a random unknown audit path returned identical responses, proving that no default-root or PHP-handler leakage was present through `api.kanger.org`.

### Host firewall posture

UFW is absent and the observed nftables/iptables filter policies remain permissive. This posture predates Server 0.14. It is not an acceptance blocker because both KANGER planes are confined to loopback and the operator plane is not proxied by nginx. Firewall policy remains separate host-hardening debt.

### Build provenance metadata

The accepted JAR exposes the version and source branch but its embedded source-commit property is empty. Exact artifact identity remains fixed by the JAR SHA-256 and immutable source commit. Future builds should embed the full source commit in build metadata.

None of these observations invalidates the accepted and merged Server 0.14 artifact.

## Canonical evidence index

Detailed sanitized evidence remains under `kanger-server/docs/operations/evidence/`, including:

- `server-0.14-production-cutover.md`;
- `server-0.14-production-boundary.md`;
- `server-0.14-browser-ui-cutover.md`;
- `server-0.14-smtps-account-acceptance.md`;
- `server-0.14-account-deletion-acceptance.md`;
- `server-0.14-update-rollback-perimeter-acceptance.md`;
- `server-0.14-vps-acceptance-post-merge.md`.

No SMTP password, TLS private key, owner token, confirmation token, session token or account-state content is committed.

## Final repository decision

```text
TECHNICAL ACCEPTANCE DECISION: PASS
PRODUCTION ACCEPTANCE:          PASS
REPOSITORY ACCEPTANCE:          PASS
PR #63:                         CLOSED / MERGED
MERGE COMMIT:                   456942653850c8ddd71160efd4648c0cc0289653
STAGE:                          COMPLETE
```

Older checkpoints that describe PR #63 as open, Draft, Ready but unmerged, or that list TRUSTED, STARTTLS, update, rollback or perimeter work as pending are historical and are superseded by this document and the post-merge receipt.

Any subsequent Server, UI, application-security or host-hardening work must begin as a separate artifact.