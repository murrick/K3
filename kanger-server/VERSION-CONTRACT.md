# KANGER Server version identity

Every non-empty JSON response produced by the HTTP boundary carries the same
four-part version identity:

```json
{
  "version": "3.7.0",
  "core_version": "3.7.0",
  "api_version": "1",
  "server_version": "server-0.18"
}
```

`version` is the user-facing compatibility alias for `core_version`. Both name
the canonical KANGER product/Core release identity.

`core_version` is independent from the legacy binary/serialization compatibility
constants retained by `org.kanger.Version`. Those compatibility constants remain
`3.3` for the current artifact and are not a public release label.

`api_version` identifies the public protocol contract independently from both
the Core and its transport implementation.

`server_version` identifies the qualified deployable REST-server component. It
is the value used by build qualification, deployment receipts, rollback
diagnostics and server release verification.

The fields are deliberately independent:

```text
version == core_version
server_version != version
```

Release tooling must verify `server_version` when it needs the deployable server
component identity. It must not use `version` as a fallback.

The generated `org/kanger/build.properties` resource records build provenance
and server packaging identity separately:

```properties
branch=server-0.18
server.version=server-0.18
source.branch=<source Git branch>
date=<build timestamp>
```

The legacy `branch` property is retained in Server packaging metadata for
compatibility. Runtime source provenance is `source.branch`; `Version.BRANCH`
and `Version.SOURCE_BRANCH` resolve to that value when it is present.

The source branch and build timestamp are provenance, not public release
identity. Building the same qualified release from a shelf, pull-request branch
or local checkout must not change `version`, `core_version` or `server_version`.
If generated build metadata is absent, the Core/product identity remains
`3.7.0`; source provenance becomes `unknown` and the build date is reported as
unavailable rather than falling back to a historical branch or timestamp.

HTTP 204 responses have no body and therefore carry no version fields. All JSON
success and error responses, including `/health`, `/ready` and `/version`, are
decorated at the transport boundary rather than independently by each handler.
