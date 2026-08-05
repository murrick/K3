# KANGER Server 0.14 — update, rollback and perimeter acceptance

Date: 2026-08-05
Repository: `murrick/K3`
Operational branch: `ops/server/0.14-vps-acceptance`
Draft PR: `#63 — KANGER Server 0.14: VPS deployment and acceptance`
Immutable source shelf: `develop/server/0.14 @ e5f9a1bfa47437636705f0935cb659cffb4d179e`

This document records the final production acceptance gates executed after account-lifecycle and mail-transport acceptance. Sensitive configuration values, credentials, tokens and account-state content are intentionally omitted.

## Accepted Server 0.14 baseline

```text
installed JAR:   /opt/kanger-server/kanger-server.jar
JAR SHA-256:     e089497d0a8f041a872a3a5a09581f8d94f5962a277794747b7f54e209882a19
configuration:   /etc/kanger-server/kanger.conf
config SHA-256:  3f6b95b4cd567d946e0d6d4c634b0db6f2f3c6aa5d7c594a62f4a715c53209dc
runtime policy:  EMAIL_VERIFIED
application:     127.0.0.1:1964
operator plane:  127.0.0.1:1965
health:          UP
readiness:       READY
```

The configuration hash is the exact restored SMTPS production baseline.

## Same-version update acceptance

A read-only preflight proved:

```text
current Server 0.14 JAR: exact accepted SHA-256
staged Server 0.14 JAR:  exact accepted SHA-256
current/staged identity: exact match
automatic previous JAR: Server 0.13 before update
installer rotation:      current JAR -> kanger-server.jar.previous
```

Because the standard installer rotates the current artifact into `.previous`, the accepted Server 0.13 rollback artifact was first preserved independently:

```text
/opt/kanger-server/rollback/server-0.13-4eb4bd33
```

The protected shelf contains the Server 0.13 JAR, pre-0.14 configuration, systemd unit, manifest and checksums. It is owned by `root:root`, mode `0700`, and its checksum verification passed.

The standard installer was then executed with the exact staged Server 0.14 JAR. Postconditions:

```text
service restarted:                true
current JAR identity:             server-0.14
previous JAR identity:            server-0.14
current/previous JAR SHA-256:     exact accepted Server 0.14 hash
configuration:                    unchanged
systemd unit:                     unchanged
operator wrapper:                 unchanged
protected Server 0.13 shelf:      intact
runtime policy:                   EMAIL_VERIFIED
health/readiness:                 UP / READY
application/operator listeners:   loopback only
```

Result:

```text
SAME_VERSION_UPDATE_GATE=PASS
```

## Manual rollback rehearsal

Before rollback, an exact Server 0.14 restoration snapshot was created:

```text
/opt/kanger-server/rollback/server-0.14-pre-manual-rollback-20260805T071605Z
```

The production service was then switched manually to the protected Server 0.13 JAR and pre-0.14 configuration. The durable state directory was not replaced or rolled back.

Observed Server 0.13 state:

```text
server identity:             server-0.13
health/readiness:            UP / READY
application listener:        loopback port 1964
operator listener 1965:      absent, as expected for Server 0.13
origin health:               server-0.13 / UP
public health:               server-0.13 / UP
public readiness:            HTTP 403
users.sequence:              unchanged at 9
canonical-home count:        unchanged at 3
quarantine count:            unchanged at 5
```

This proved that Server 0.13 could reopen the current durable state after Server 0.14 operation without changing the observed durable-state metadata.

Result:

```text
MANUAL_ROLLBACK_013_GATE=PASS
```

## Restoration to Server 0.14

Before restoration, an exact snapshot of the active Server 0.13 rollback boundary was created:

```text
/opt/kanger-server/rollback/server-0.13-pre-restore-0.14-20260805T073739Z
```

The service was restored from the exact Server 0.14 snapshot. Postconditions:

```text
current JAR identity:          server-0.14
previous JAR identity:         server-0.14
current JAR SHA-256:           exact accepted hash
configuration SHA-256:         exact SMTPS baseline
runtime policy:                EMAIL_VERIFIED
health/readiness:              UP / READY
application listener:          loopback port 1964
operator listener:             loopback port 1965
origin/public health:          server-0.14 / UP
public readiness:              HTTP 403
users.sequence:                unchanged at 9
canonical-home count:          unchanged at 3
quarantine count:              unchanged at 5
```

Result:

```text
SERVER_014_RESTORATION_GATE=PASS
```

## Final nginx, HTTPS, UI and CORS audit

The final read-only perimeter audit proved:

```text
nginx active/enabled:              PASS
nginx syntax:                      PASS
kanger.org server names:           PASS
api.kanger.org server name:        PASS
application upstream:              loopback port 1964
operator port 1965 in nginx:       absent
TLS hostname validation:           PASS
TLS protocol:                      TLS 1.3
HTTP -> HTTPS redirects:           PASS
UI origin/public hash identity:    PASS
API origin/public health identity: PASS
public /ready rejection:           HTTP 403
allowed CORS origins:              kanger.org, www.kanger.org
external/null CORS origins:        HTTP 403, no allow-origin header
CORS credentials:                  disabled
```

The accepted UI hashes remained unchanged:

```text
index.html:   747104f9e9a7599679b5329eb098590b190a876ff949f01e232f67a1e1b8baae
config.js:    d44e67c862f12065d62259055043922ff573271dd5fac2f373cc454172757715
console.html: 24d74b6608d4d49a5464d62dd368354c3f30d5f90b90b6a8717531d8e7719dca
```

The certificates for `kanger.org`, `www.kanger.org` and `api.kanger.org` were valid during the audit and reported an expiry date of 2026-10-15.

## Legacy default-site finding and closure

The audit found an enabled nginx default site and two legacy diagnostic files under `/var/www/html`:

```text
info.php SHA-256: 3dedb74885acd4858287238df82075cc868df90ac70f603f03e1f241295c3027
test.php SHA-256: a5534e9d387fd68b5b0e64891fc71c8cb83274b0d67354159d841f87d5848774
```

Classification proved:

- the raw-IP/default virtual host was publicly reachable;
- `info.php` was served verbatim;
- `test.php` produced a PHP-handler response through `php8.3-fpm`;
- `api.kanger.org/info.php` and `/test.php` did not expose those files: both matched a random unknown path and were generic KANGER application fallback responses.

The closure-blocking default-site exposure was removed under a guarded mutation:

```text
disabled symlink: /etc/nginx/sites-enabled/default
quarantine:       /opt/kanger-server/rollback/nginx-default-site-20260805T083900Z
nginx action:     reload only
KANGER restart:   none
```

The quarantine retains the default-site configuration, both PHP files, original enabled-link target, manifest and checksums. After the reload:

```text
default site enabled:             false
default_server directive:         absent
public-root PHP artifacts:        absent
raw-IP PHP exposure:              absent
KANGER PID:                       unchanged
Server 0.14 health/readiness:     UP / READY
UI identity:                      unchanged
CORS behavior:                    unchanged
1964/1965 listener confinement:   unchanged
```

Result:

```text
DEFAULT_SITE_HARDENING_GATE=PASS
```

## Residual non-blocking observations

### Host firewall posture

The host still has no UFW policy and the observed nftables/iptables filter policies are permissive. This posture predates Server 0.14. It is not a Server 0.14 acceptance blocker because the application and operator planes are confined to loopback and operator port 1965 is not exposed through nginx. It remains host-hardening debt.

Existing VPN, Tailscale, SSH and Docker endpoints are outside this artifact and were not modified.

### Unknown API paths

Unknown API paths currently return the generic KANGER JSON fallback with HTTP 200. This behavior caused paths resembling PHP files to return 200 through `api.kanger.org`, but no filesystem or PHP-handler leakage was present. HTTP unknown-path semantics remain a future application/security improvement.

### Confirmation action semantics

The previously recorded state-changing confirmation GET remains a future security/UX improvement. It did not invalidate the completed lifecycle acceptance.

## Final acceptance result

```text
same-version update:                    PASS
manual rollback to Server 0.13:         PASS
restoration to Server 0.14:             PASS
durable-state metadata non-interference: PASS
nginx routing and TLS:                  PASS
UI release identity:                    PASS
API public boundary:                    PASS
CORS policy:                            PASS
legacy default-site exposure:           CLOSED
public PHP exposure:                    CLOSED
application/operator confinement:       PASS
```

Server 0.14 VPS deployment and production acceptance are technically complete. The remaining observations are documented non-blocking debt and do not require reopening the Server 0.14 deployment artifact.
