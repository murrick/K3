# KANGER Server 0.14 — VPS acceptance post-merge receipt

Date: 2026-08-05
Repository: `murrick/K3`
PR: `#63 — KANGER Server 0.14: VPS deployment and acceptance`

## Repository closure

```text
immutable source commit: e5f9a1bfa47437636705f0935cb659cffb4d179e
operational branch:      ops/server/0.14-vps-acceptance
merged operational head: fe162b9276f0fcd92a23556c6bc5c874b462a789
merge commit:            456942653850c8ddd71160efd4648c0cc0289653
PR #63 state:            CLOSED / MERGED
```

PR #63 was first moved from Draft to Ready for review after all production gates had passed, and was then merged with a normal merge commit. The merge changed repository state only; it did not restart, reconfigure, or otherwise mutate the production host.

## Accepted production identity

```text
server version:     server-0.14
source shelf:       e5f9a1bfa47437636705f0935cb659cffb4d179e
JAR SHA-256:        e089497d0a8f041a872a3a5a09581f8d94f5962a277794747b7f54e209882a19
configuration SHA:  3f6b95b4cd567d946e0d6d4c634b0db6f2f3c6aa5d7c594a62f4a715c53209dc
registration policy: EMAIL_VERIFIED
mail transport:      SMTPS
health/readiness:     UP / READY
application plane:    loopback 127.0.0.1:1964
operator plane:       loopback 127.0.0.1:1965
public readiness:     HTTP 403
```

## Acceptance result

The following production gates passed:

- exact-shelf qualification and predeployment backup;
- guarded Server 0.13 to Server 0.14 cutover;
- browser UI cutover and exact public/origin artifact identity;
- public and operator network-boundary acceptance;
- real SMTPS `EMAIL_VERIFIED` account lifecycle;
- `TRUSTED` policy lifecycle with operator-created account;
- STARTTLS lifecycle and restoration to the exact SMTPS baseline;
- account deletion, workspace quarantine, session revocation and rejected fresh authentication;
- controlled restart and exact same-version update;
- protected Server 0.13 rollback shelf;
- manual rollback `0.14 -> 0.13` and restoration `0.13 -> 0.14`;
- durable-state metadata non-interference through rollback and restoration;
- nginx, HTTPS, TLS hostname, redirect, API, UI and CORS audit;
- removal of the exposed legacy default nginx site and its public PHP artifacts.

The default-site rollback material is retained at:

```text
/opt/kanger-server/rollback/nginx-default-site-20260805T083900Z
```

## Residual non-blocking observations

1. Account confirmation remains a prefetchable state-changing GET. A future change should use an inert landing GET, explicit POST and idempotent already-confirmed handling.
2. Unknown API paths currently return a generic JSON response with HTTP 200. No default-root or PHP-handler leakage was observed through `api.kanger.org`.
3. The host firewall posture remains permissive and predates this artifact. KANGER ports 1964 and 1965 remain loopback-only, and port 1965 is absent from nginx routing.
4. The accepted JAR does not embed its source commit property. Exact identity remains established by the accepted JAR SHA-256 and immutable source commit.

None of these observations blocks the accepted Server 0.14 production artifact.

## Canonical evidence

- `server-0.14-vps-acceptance-closure.md`
- `server-0.14-update-rollback-perimeter-acceptance.md`
- the sibling production, UI, SMTP and account-deletion evidence files in this directory
- merged PR #63 and merge commit `456942653850c8ddd71160efd4648c0cc0289653`

Older checkpoints that describe PR #63 as open, Draft, Ready but unmerged, or that list TRUSTED, STARTTLS, update, rollback or perimeter gates as pending are historical and are superseded by this receipt.

## Final status

```text
SERVER 0.14 PRODUCTION ACCEPTANCE: PASS
SERVER 0.14 REPOSITORY ACCEPTANCE: PASS
PR #63:                         CLOSED / MERGED
STAGE:                          COMPLETE
```

Any subsequent server, UI, security or host-hardening work must be opened as a separate artifact.