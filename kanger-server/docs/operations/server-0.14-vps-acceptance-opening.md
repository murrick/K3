# KANGER Server 0.14 VPS acceptance — opening contract

## Artifact identity

```text
base shelf:     develop/server/0.14
base SHA:       e5f9a1bfa47437636705f0935cb659cffb4d179e
working branch: ops/server/0.14-vps-acceptance
production VPS: murray@94.103.94.41:4211
```

Server 0.14 is already CLOSED, QUALIFIED, SHELVED and MERGED. This artifact does not reopen its account-lifecycle semantics. It records deployment, real-host verification and acceptance evidence.

## Production topology under test

```text
Cloudflare
    ↓
nginx :443
    ↓
127.0.0.1:1964 — public application API

SSH host operator
    ↓
sudo kanger-admin
    ↓
127.0.0.1:1965 — owner-only operator API
```

Both Java listeners must remain loopback-only. Port 1965 and the owner bearer token must never be exposed through nginx, Cloudflare, browser configuration, Docker publication or host firewall rules.

## Execution order

1. Read-only host inventory.
2. Record current service, Java, nginx, listener, firewall, Docker and disk state.
3. Create a transactionally quiet backup of `/etc/kanger-server` and `/var/lib/kanger-server`; copy the archive off-host.
4. Build the exact immutable `develop/server/0.14` shelf and verify generated release metadata.
5. Run local build and smoke qualification before transfer.
6. Transfer JAR and deployment assets into `/tmp/kanger-deploy`.
7. Install/update with `install.sh`; allow its automatic previous-JAR rollback on failed health/readiness.
8. Run `verify-installed.sh` and independent manual checks.
9. Run TRUSTED acceptance first:
   - Register absent;
   - `sudo kanger-admin create-user`;
   - public login/session/logout;
   - `sudo kanger-admin delete-user`;
   - credential/session revocation, quarantine and deletion journal.
10. Run EMAIL_VERIFIED acceptance separately for STARTTLS and SMTPS after SMTP credentials are configured.
11. Verify restart, update, manual rollback, nginx/HTTPS, CORS, firewall and public rejection of `/ready` and port 1965.
12. Record defects by category: deployment/configuration, Server 0.14 defect, future improvement.

## Safety invariants

- No destructive command before inventory and backup evidence exist.
- No account data is copied into Git or PR text.
- No SMTP password, TLS private key or admin token is printed or persisted in shell history.
- Existing configuration is preserved on update.
- Existing durable state is never replaced by an empty directory.
- Production exposure is enabled only after loopback health/readiness and listener confinement pass.
- Any code defect discovered during acceptance is fixed in a separate patch artifact; this operational branch records evidence and deployment-only corrections.

## Initial acceptance policy

TRUSTED mode is tested first:

```properties
server.email.mode=disabled
```

This minimizes the public surface while proving the new SSH/operator boundary. STARTTLS and SMTPS are subsequent, independently recorded acceptance passes.

## Completion condition

This artifact closes only when:

- the installed service reports `server_version=server-0.14`;
- application and operator listeners are confined to `127.0.0.1:1964` and `127.0.0.1:1965`;
- nginx exposes only the application API;
- backup and rollback procedures are demonstrated;
- TRUSTED and both EMAIL_VERIFIED transports have explicit pass/fail records;
- all discovered issues are classified and linked to their owning artifact.
