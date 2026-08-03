# Deployment target

The supported production topology is:

```text
client -> nginx:443 -> 127.0.0.1:<kanger-server-port>
```

The Java process must bind only to loopback. nginx terminates TLS and applies public request limits. The operating system service manager owns process restart, resource limits and graceful shutdown.

The legacy embedded wrapper and repository certificate material are not part of the target production topology.
