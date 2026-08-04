# KANGER Server 0.14 — final integration boundary

Date: 2026-08-04

Status: OPEN

## Integration base

```text
base artifact: develop/server/0.13
base SHA: db439e9c20835e9d918b19be216a598320458acd
working branch: server/0.14-account-lifecycle
latest qualified stage shelf: develop/server/0.14.5
latest qualified stage SHA: 7f7b0c198c57b30f37e741c031954a714f776829
draft PR: #62
```

## Composition already proved

The five qualified stage shelves form one monotonic line:

```text
develop/server/0.13
→ develop/server/0.14.1
→ develop/server/0.14.2
→ develop/server/0.14.3
→ develop/server/0.14.4
→ develop/server/0.14.5
```

Every shelf is an exact ancestor of the next one with zero `behind` commits.
The working branch and `develop/server/0.14.5` were identical when this
integration boundary opened.

## Governing release invariant

The account semantics closed by Server 0.14.1–0.14.5 are frozen.

Final integration may change only:

- public deployable artifact identity from `server-0.13` to `server-0.14`;
- build, test, smoke and CI assertions that prove that identity;
- installed-topology verification for the application and local operator
  listeners;
- release/deployment documentation that still describes superseded Server 0.12
  or Server 0.13 behavior;
- final integration and closure documentation.

It must not change:

- account publication or deletion semantics;
- pending-registration persistence or recovery;
- credential formats or migration behavior;
- mail transport and registration-policy resolution;
- public authentication capabilities;
- local operator protocol or CLI semantics;
- owner-console behavior;
- KANGER semantic core or storage engines.

## Public version identity

The qualified release must report one consistent identity from `/health`,
`/ready`, `/version`, ordinary JSON responses and generated build metadata:

```json
{
  "version": "3.3",
  "core_version": "3.3",
  "api_version": "1",
  "server_version": "server-0.14"
}
```

`version` remains the compatibility alias for the semantic core. It must never
be treated as a fallback server-artifact version.

## Installed topology

The release deployment boundary is:

```text
public nginx
→ 127.0.0.1:1964 application API

host operator
→ sudo kanger-admin
→ 127.0.0.1:1965 owner-only admin listener
```

Both Java listeners must remain loopback-only. nginx must proxy only the
application listener. The operator listener and owner token must never be
exposed through the public API or browser UI.

## Required integration changes

1. Promote Maven-generated server artifact metadata to `server-0.14`.
2. Promote `VersionTest`, local smoke and permanent Server workflow assertions.
3. Make installed verification prove loopback confinement of ports 1964 and
   1965.
4. Align `VERSION-CONTRACT.md` with the final four-field identity.
5. Replace stale deployment guidance with the actual Server 0.14 topology and
   registration policies.
6. Run all permanent workflows on the exact functional integration HEAD.
7. Add a documentation-only closure commit and qualify that exact HEAD.
8. Create immutable shelf `develop/server/0.14` only after full success.

## Qualification gate

Required permanent workflows:

```text
KANGER qualification isolation
KANGER Server
KANGER CI
KANGER III semantic planning
KANGER III storage optimization
KANGER III DUMB reliability qualification
```

KANGER Server must pass Java 8 and Java 21 with zero test failures, errors or
skips. Java 21 must additionally prove the complete live application/admin
round-trip and clean shutdown.

## Merge boundary

PR #62 remains Draft and unmerged throughout integration. Creating the immutable
`develop/server/0.14` shelf does not itself authorize marking the PR ready or
merging it. Those actions require explicit approval after the final artifact is
qualified.
