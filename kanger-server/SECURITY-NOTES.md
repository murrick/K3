# Deployment blockers

The server is not approved for public deployment until the following boundaries are covered by executable tests and corrected:

- default TLS certificate and hostname validation for outbound HTTPS;
- cryptographically strong password storage, session tokens and confirmation tokens;
- filesystem containment for source-file operations;
- bounded HTTP parsing, request timeouts and overload handling;
- explicit CORS allow-list without reflected credentialed origins;
- serialized mutation of each user's current KANGER session;
- deterministic storage and Mind shutdown on logout, timeout and process termination;
- validated configuration without write-on-read side effects.

This file describes only `kanger-server`. It does not change or redefine KANGER core semantics.
