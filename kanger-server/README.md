# KANGER Server

`kanger-server` is the network delivery shell for KANGER III.

This directory is maintained as an independent server project. The production KANGER source roots are consumed by the server build but are not modified as part of server stabilization.

## Target runtime boundary

The application server is intended to run as an unprivileged local service:

```text
Internet -> nginx (HTTPS) -> 127.0.0.1:<internal port> -> kanger-server.jar
```

nginx owns public TLS, request admission and reverse-proxy concerns. The Java process owns the KANGER API, user sessions and KANGER resource lifecycle.

## Build

From the repository root:

```bash
mvn -f kanger-server/pom.xml clean verify
```

The standalone distribution is written to:

```text
kanger-server/target/kanger-server.jar
```

The build includes the unchanged `kanger`, `kanger-udf` and `kanger-data-dumb` production source roots, plus the server's Maven-managed JSON and JavaMail dependencies.

## Status

The current server implementation is under stabilization and must not yet be exposed directly to the public Internet. Deployment blockers include authentication, request parsing, filesystem containment, TLS client behaviour and per-session concurrency.
