# KANGER Server local operator plane

## Purpose

`kanger-admin` provisions and safely removes KANGER application accounts from the server host.

It is not a web administration console and it does not grant application users administrative roles. The command is a local authenticated client of the running KANGER Server process.

For the customer-facing KANGER 3.7.0 installation, update, TLS, TRUSTED-mode, and administration procedure, see the distribution guide:

```text
distribution/payload/README.md
```

That file is copied into the root of the customer tarball by `distribution/build-distribution.sh` and is the operational installation map for the 3.7.0 bundle.

## Architecture

```text
host operator
    |
    v
kanger-admin
    |
    | HTTP + bearer token, loopback only
    v
127.0.0.1:1965
    |
    v
AccountLifecycleService
    |
    +-- credential authority
    +-- session/runtime authority
    +-- pending/confirmation authority
    +-- canonical account home
    +-- deletion journal and quarantine
```

The CLI never edits credential files, pending registrations, account homes, or deletion journals directly. The running server remains the only account-lifecycle owner.

## KANGER 3.7.0 distribution path and execution

The KANGER 3.7.0 distribution installs the launcher in the active release:

```text
/opt/kanger/current/bin/kanger-admin
```

Run it through `sudo`:

```bash
sudo /opt/kanger/current/bin/kanger-admin --help
```

The distribution launcher must be run as root. It starts the admin client from the active Server JAR with:

```text
-Duser.home=/var/lib/kanger
-cp /opt/kanger/current/server/kanger-server.jar
org.kanger.admin.KangerAdmin
```

The launcher defaults may be overridden for controlled diagnostic/testing use with `KANGER_ADMIN_SERVER_HOME` and `KANGER_ADMIN_SERVER_JAR`; ordinary installed operation uses the defaults above.

## Configuration

Default server settings are:

```properties
server.admin.enabled=true
server.admin.bind.address=127.0.0.1
server.admin.port=1965
server.admin.request.max.body.bytes=65536
server.admin.token.file=KANGER/admin.token
```

The listener must remain loopback-only. It must not be added to nginx or exposed through a firewall rule, tunnel, or public reverse proxy.

The server creates the bearer-token file atomically on first startup and restricts it to owner read/write permissions where POSIX permissions are available.

## Create an ACTIVE account

Interactive:

```bash
sudo /opt/kanger/current/bin/kanger-admin create-user
```

Flag-based while retaining the hidden password prompt:

```bash
sudo /opt/kanger/current/bin/kanger-admin create-user \
  --login alice \
  --email alice@example.org \
  --name 'Alice Example' \
  --country Netherlands \
  --city Amsterdam \
  --privacy-consent true
```

Mixed mode is supported: supplied flags are used and missing values are prompted interactively.

Password rules:

- plaintext `--password` is deliberately forbidden;
- interactive mode uses a hidden password prompt;
- automation must opt in explicitly with `--password-stdin`;
- passwords and bearer tokens are not printed in success or error output.

For non-interactive automation, send the password on standard input from the operator's secret-management mechanism and add `--password-stdin`. Do not embed password literals in command lines or shell history.

Operator creation publishes a complete ACTIVE account immediately through `AccountLifecycleService`. An optional operator-supplied e-mail address is not marked as verified.

In `RegistrationPolicy.TRUSTED` (`server.email.mode=disabled`), this local operator plane is the normal account-provisioning path because public self-registration is disabled.

## Delete an account safely

By login:

```bash
sudo /opt/kanger/current/bin/kanger-admin delete-user --login alice
```

By user id:

```bash
sudo /opt/kanger/current/bin/kanger-admin delete-user --user-id 42
```

Interactive deletion requires typing `DELETE`. Non-interactive deletion additionally requires explicit acknowledgement:

```bash
sudo /opt/kanger/current/bin/kanger-admin delete-user --login alice --yes
```

The CLI also sends the protocol marker `confirm=DELETE`. Missing either confirmation boundary prevents lifecycle invocation.

## Deletion lifecycle

Deletion is persistent and forward-only:

```text
PREPARED
→ CREDENTIAL_REMOVED
→ HOME_QUARANTINED
→ COMPLETE
```

Before credential removal, a failure leaves the account able to authenticate again. After credential removal, recovery proceeds only forward; automatic rollback never republishes authentication.

A successful deletion:

- closes all sessions and runtime state;
- revokes legacy confirmation tokens;
- removes stale pending registration intent;
- removes the credential snapshot;
- moves the canonical account home into `.quarantine`;
- records the exact operation in `account-deletions.conf`;
- never reuses the deleted `userId`.

The login and e-mail identity become reusable only after the journal reaches `COMPLETE`.

## Exit codes

```text
0  operation completed
2  invalid or incomplete operator input
3  admin listener/token/connection failure
4  account lifecycle conflict or rejection
5  deletion is incomplete and requires recovery
```

For exit code `5`, preserve the reported deletion id and inspect the server log plus the persistent deletion journal before retrying or resuming the operation.

## Security boundary

- the admin listener accepts only loopback binding;
- bearer authentication occurs before operation dispatch;
- only POST mutation endpoints exist;
- the public API has no admin routes;
- the listener is not an application-user privilege plane;
- the bearer token and account passwords must never be copied into tickets, chat, logs, or shell history.
