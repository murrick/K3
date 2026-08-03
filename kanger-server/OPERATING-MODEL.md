# Operating model

- nginx owns the public HTTP and TLS boundary.
- systemd owns process lifecycle and restart policy.
- kanger-server owns authenticated API sessions and KANGER resource lifecycle.
- KANGER core semantics remain outside the server stabilization scope.
