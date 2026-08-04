# Server 0.14 pre-deployment backup evidence

Date: 2026-08-04
Target: `murray@94.103.94.41:4211`

## Result

```text
status: PASS
archive: kanger-server-pre-0.14-20260804T163538Z.tar.gz
archive size: approximately 4.1 MiB
off-host location: operator Mac backup directory
sha256: b405975db9f3ece35811da078f6384bdd96052e4b4c340c066e8c5db0831b04b
local verification: MATCH
service_was_active: true
service_after_backup: active
health_after_backup: UP
server_version_after_backup: server-0.13
```

## Covered material

The backup gate captured the installed configuration, durable server state, current and previous JARs, systemd unit and KANGER nginx site configuration. The archive was copied off the VPS and its SHA-256 was verified locally.

The archive itself is intentionally not committed because it contains operational configuration and durable production state.

## Boundary

No Server 0.14 code has been installed yet. The next permitted action is an exact local build and smoke qualification from immutable shelf:

```text
develop/server/0.14
e5f9a1bfa47437636705f0935cb659cffb4d179e
```
