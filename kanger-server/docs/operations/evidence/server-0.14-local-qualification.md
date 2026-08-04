# Server 0.14 local deployment qualification evidence

Recorded during VPS acceptance on 2026-08-04.

## Exact source

```text
branch: develop/server/0.14
HEAD:   e5f9a1bfa47437636705f0935cb659cffb4d179e
```

The operator reported a clean exact checkout and successful Maven qualification:

```text
BUILD SUCCESS
```

## Built artifact identity

```properties
branch=server-0.14
source.branch=develop/server/0.14
server.version=server-0.14
```

```text
JAR:    kanger-server/target/kanger-server.jar
SHA256: e089497d0a8f041a872a3a5a09581f8d94f5962a277794747b7f54e209882a19
```

## Local runtime qualification

The operator reported successful completion of:

```text
kanger-server/scripts/smoke-local.sh
kanger-server/scripts/smoke-auth-local.sh
```

Observed acceptance conditions:

- health, readiness and version identity report `server-0.14`;
- TRUSTED public registration policy is enforced;
- ordinary login/logout and session-token rotation pass;
- operator-plane account creation passes;
- account deletion reaches COMPLETE;
- credential and session revocation pass;
- canonical-home quarantine and deletion journal verification pass;
- the public listener cannot dispatch owner operations;
- the built JAR SHA-256 remains unchanged after local smoke execution.

## Evidence status

```text
LOCAL EXACT BUILD: PASS
LOCAL HTTP SMOKE: PASS
LOCAL AUTH/OPERATOR SMOKE: PASS
DEPLOYABLE JAR: QUALIFIED FOR STAGING TRANSFER
```

This is operator-observed operational evidence. It does not modify or replace the immutable qualified Server 0.14 shelf.
