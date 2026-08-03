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
git switch server/0.6-authenticated-smoke
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

Stop the server with `Ctrl+C`.

## 3. Verify the transport

In a second terminal:

```bash
curl --fail --silent --show-error http://127.0.0.1:1964/health
curl --fail --silent --show-error http://127.0.0.1:1964/version
```

Or run the supplied transport smoke check:

```bash
bash kanger-server/scripts/smoke-local.sh
```

Expected health response shape:

```json
{"result":"OK","status":"UP","version":"..."}
```

Expected version response shape:

```json
{"result":"OK","version":"..."}
```

`localhost` is equivalent for interactive use:

```text
http://localhost:1964/health
```

The explicit `127.0.0.1` form is used in scripts to make the IPv4 loopback
boundary unambiguous.

## 4. Verify authentication and session lifecycle

With the server still running, execute:

```bash
bash kanger-server/scripts/smoke-auth-local.sh
```

The authenticated smoke scenario:

1. registers a unique temporary user in the isolated sandbox;
2. receives a cryptographic session token;
3. executes an authenticated `ping` command;
4. logs out and verifies that the old token is rejected;
5. logs in again using the stored PBKDF2 credential;
6. verifies that a new session token is issued;
7. executes another authenticated command and closes the session.

The script does not send e-mail and does not require SMTP settings. Every run
creates a unique smoke user inside the local sandbox. Remove the sandbox as
described below when those records are no longer needed.

When using another local port, pass the base URL explicitly:

```bash
KANGER_BASE_URL=http://127.0.0.1:1965 \
  bash kanger-server/scripts/smoke-auth-local.sh
```

## 5. Change local settings

Edit:

```text
kanger-server/run/local/home/kanger.conf
```

For example, to use another port:

```properties
server.port=1965
```

Restart the server after changing settings. Pass the same address to either
smoke script through `KANGER_BASE_URL`.

## 6. Reset the local sandbox

Stop the server, then remove only the isolated runtime directory:

```bash
rm -rf kanger-server/run/local
```

The next local start creates a clean environment. Repository sources and the
normal operating-system home directory are unaffected.

## Current qualification boundary

The transport, credential migration, cryptographic session tokens, per-user
request serialization, logout/timeout cleanup and filesystem-facing input
confinement are covered by automated Java 8/21 qualification. The authenticated
smoke is also executed against a real loopback server process in GitHub Actions.

This remains a local and pre-deployment qualification scenario. Public exposure
still requires the nginx/systemd deployment slice and the remaining outbound
TLS, settings, mail and operational-hardening work.
