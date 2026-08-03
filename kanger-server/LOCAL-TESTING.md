# Local testing

This scenario runs KANGER Server directly on loopback without nginx. It is
intended for API development and qualification on macOS or Linux.

## Prerequisites

- Git
- JDK 8 or JDK 21
- Maven 3.8+
- curl
- Python 3 for authenticated smoke response parsing

## 1. Select the current server branch

```bash
git fetch origin
git switch server/0.11-mail-transport
```

## 2. Start the isolated local server

From the repository root:

```bash
bash kanger-server/scripts/run-local.sh
```

The runner:

1. builds `kanger-server/target/kanger-server.jar`;
2. creates an isolated runtime under `kanger-server/run/local`;
3. copies the local configuration to
   `kanger-server/run/local/home/kanger.conf` on first start;
4. starts the JVM with that directory as `user.home`;
5. listens only on `127.0.0.1:1964`.

The sandbox contains all local users, databases, sources, logs and runtime
markers. It does not use the normal operating-system home directory.

To start without rebuilding an existing JAR:

```bash
KANGER_LOCAL_REBUILD=0 bash kanger-server/scripts/run-local.sh
```

Stop the server with `Ctrl+C`. The normal process shutdown hook stops the HTTP
listener, drains the bounded mail executor, closes active user runtime/storage
state and removes the active marker.

## 3. Verify the transport

In a second terminal:

```bash
curl --fail --silent --show-error http://127.0.0.1:1964/health
curl --fail --silent --show-error http://127.0.0.1:1964/version
```

Or run:

```bash
bash kanger-server/scripts/smoke-local.sh
```

Expected health response shape:

```json
{"result":"OK","status":"UP","version":"..."}
```

`localhost` is equivalent for interactive use:

```text
http://localhost:1964/health
```

## 4. Verify authentication and session lifecycle

With the server still running:

```bash
bash kanger-server/scripts/smoke-auth-local.sh
```

The scenario registers a unique user without e-mail, receives a cryptographic
session token, executes `ping`, logs out, verifies rejection of the old token,
logs in again, verifies token rotation and closes the new session.

The default local configuration contains:

```properties
server.email.mode=disabled
```

Therefore the smoke does not connect to SMTP and remains deterministic.

When using another local port:

```bash
KANGER_BASE_URL=http://127.0.0.1:1965 \
  bash kanger-server/scripts/smoke-auth-local.sh
```

## 5. Verify disabled-mail admission

With the default configuration, a registration containing a non-empty e-mail
address must return a normal JSON error before the legacy processor creates its
historical mail thread. Password-only registration remains available.

SMTP modes and functional mail testing are documented in:

```text
kanger-server/MAIL-CONFIGURATION.md
```

Use a dedicated test mailbox when enabling mail locally. Never commit SMTP
credentials into `config/kanger.local.conf.example` or any repository file.

## 6. Change local settings

Edit:

```text
kanger-server/run/local/home/kanger.conf
```

For example:

```properties
server.port=1965
```

Restart the server after changing settings.

## 7. Reset the local sandbox

Stop the server, then remove only the isolated runtime directory:

```bash
rm -rf kanger-server/run/local
```

The next local start creates a clean environment. Repository sources and the
normal operating-system home directory are unaffected.

## Current qualification boundary

Automated Java 8/21 qualification covers bounded loopback HTTP, credential
migration, cryptographic sessions, per-user serialization, logout/timeout
cleanup, filesystem input confinement, atomic settings, platform TLS for
outbound HTTP, graceful SIGTERM shutdown, deployment assets and the bounded
explicit mail transport.

The process smoke starts the packaged JAR, executes the complete authenticated
lifecycle, sends SIGTERM, checks active-marker removal and verifies that the
HTTP port closes. Mail-specific tests use an injected sender and never create an
external SMTP connection.
