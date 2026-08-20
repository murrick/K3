# KANGER Server — 3.7.0 release qualification marker

This file is release bookkeeping only. It deliberately lives under `kanger-server/` so the final 3.7.0 closure head is rebuilt by the normal `KANGER Server` workflow and produces a Java 21 distribution artifact from that exact repository snapshot.

It does not change Server runtime semantics, API behavior or deployment configuration.

Canonical release identities:

```text
KANGER product/Core: 3.7.0
Core compatibility: 3.3
API:                1
Server:             server-0.18
```

The generated Server `build.properties` remains the provenance authority for the build source branch and timestamp. Public release identity is independent from that provenance, as defined by `VERSION-CONTRACT.md`.
