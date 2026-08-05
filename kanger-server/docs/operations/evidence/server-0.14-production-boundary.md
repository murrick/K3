# Server 0.14 production boundary acceptance

Recorded during VPS acceptance on 2026-08-04.

## Permanent verifier

The operator reported successful execution of the installed verifier against the production service.

Observed identity and readiness:

```json
{"result":"OK","api_version":"1","server_version":"server-0.14","version":"3.3","core_version":"3.3","status":"UP"}
```

```json
{"active_requests":1,"uptime_millis":633027,"failed_requests":0,"queue_remaining":128,"max_workers":32,"queued_requests":0,"overload_rejections":0,"api_version":"1","server_version":"server-0.14","version":"3.3","core_version":"3.3","result":"OK","total_requests":50,"status":"READY","queue_capacity":128}
```

The verifier also reported valid nginx syntax and successful application/operator loopback confinement.

## Listener boundary

```text
[::ffff:127.0.0.1]:1964 — application API
[::ffff:127.0.0.1]:1965 — owner operator API
```

No wildcard or public Java listener was observed.

## Operator CLI boundary

`sudo kanger-admin --help` exposed only the supported host operations:

```text
create-user
delete-user
```

The CLI states that passwords are accepted only through a hidden prompt or `--password-stdin`. No owner token or credential value was printed.

## Public boundary

Public health returned `server-0.14 / UP`.

Public version/auth capability snapshot:

```json
{"result":"OK","auth":{"public_registration":true,"registration_policy":"EMAIL_VERIFIED","pending_registration_actions":true,"email_confirmation_required":true,"confirmation_creates_session":false},"api_version":"1","server_version":"server-0.14","version":"3.3","core_version":"3.3"}
```

Public `/ready` returned:

```text
HTTP 403
```

## Result

```text
PERMANENT VERIFIER: PASS
APPLICATION LISTENER LOOPBACK: PASS
OPERATOR LISTENER LOOPBACK: PASS
OPERATOR CLI BOUNDARY: PASS
PUBLIC HEALTH: PASS
PUBLIC READY REJECTION: PASS
EMAIL_VERIFIED AUTH CAPABILITY: PASS
```

This closes the production network and operator-boundary gate. Real account-lifecycle and mail-transport acceptance remain open under PR #63.
