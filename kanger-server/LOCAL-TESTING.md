# Local testing

This scenario runs KANGER Server directly on loopback without nginx. It is
intended for API development and qualification on macOS or Linux.

## Prerequisites

- Git
- JDK 8 or JDK 21
- Maven 3.8+
- curl

## 1. Select the server branch

```bash
git fetch origin
git switch server/0.2-http-boundary
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

Or run the supplied smoke check:

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

## 4. Change local settings

Edit:

```text
kanger-server/run/local/home/kanger.conf
```

For example, to use another port:

```properties
server.port=1965
```

Restart the server after changing settings. When changing the port, pass the
same address to the smoke script:

```bash
KANGER_BASE_URL=http://127.0.0.1:1965 \
  bash kanger-server/scripts/smoke-local.sh
```

## 5. Reset the local sandbox

Stop the server, then remove only the isolated runtime directory:

```bash
rm -rf kanger-server/run/local
```

The next local start creates a clean environment. Repository sources and the
production operating-system home directory are unaffected.

## Current qualification boundary

`/health` and `/version` are safe transport-level checks. Authentication,
registration and persistent user sessions remain under active stabilization;
they should not yet be treated as a production security contract.
