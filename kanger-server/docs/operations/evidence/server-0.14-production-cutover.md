# Server 0.14 production cutover evidence

Recorded during VPS acceptance on 2026-08-04.

## Target

```text
host: murray@94.103.94.41:4211
service: kanger-server.service
```

## Installed artifact

```text
source shelf: develop/server/0.14
git SHA:      e5f9a1bfa47437636705f0935cb659cffb4d179e
JAR SHA-256:  e089497d0a8f041a872a3a5a09581f8d94f5962a277794747b7f54e209882a19
```

## Guarded installation result

The operator reported the exact guarded-wrapper terminal block:

```text
KANGER Server 0.14 guarded installation complete
DEPLOYMENT_GATE=PASS
INSTALLED_SHA256=e089497d0a8f041a872a3a5a09581f8d94f5962a277794747b7f54e209882a19
CONFIG_ROLLBACK_COPY=/etc/kanger-server/kanger.conf.pre-0.14-20260804T174000Z
SYSTEMD_ROLLBACK_COPY=/etc/systemd/system/kanger-server.service.pre-0.14-20260804T174000Z
PREVIOUS_JAR_IDENTITY=server-0.13
SERVICE_ACTIVE=active
```

The installed service then returned:

```json
{"result":"OK","api_version":"1","server_version":"server-0.14","version":"3.3","core_version":"3.3","status":"UP"}
```

and readiness:

```json
{"active_requests":1,"uptime_millis":844,"failed_requests":0,"queue_remaining":128,"max_workers":32,"queued_requests":0,"overload_rejections":0,"api_version":"1","server_version":"server-0.14","version":"3.3","core_version":"3.3","result":"OK","total_requests":6,"status":"READY","queue_capacity":128}
```

## Preserved rollback boundary

- previous production JAR remains identifiable as `server-0.13`;
- the pre-0.14 configuration copy is retained;
- the pre-0.14 systemd unit copy is retained;
- the service is active after cutover;
- staged and installed JAR SHA-256 are identical.

## Result

```text
PRODUCTION CUTOVER: PASS
SERVER IDENTITY: server-0.14
SERVICE HEALTH: UP
SERVICE READINESS: READY
AUTOMATIC ROLLBACK BOUNDARY: PRESERVED
```

This closes the code deployment gate. Account-policy, SMTP transport, browser, restart/update/rollback and broader operational acceptance remain open under PR #63.
