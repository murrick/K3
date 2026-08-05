# KANGER Server version identity

Every non-empty JSON response produced by the HTTP boundary carries the same
four-part version identity:

```json
{
  "version": "3.3",
  "core_version": "3.3",
  "api_version": "1",
  "server_version": "server-0.17"
}
```

`version` is the user-facing compatibility alias for `core_version`. It names
the semantic KANGER product reached through the API.

`core_version` changes when KANGER semantics or core compatibility changes.

`api_version` identifies the public protocol contract independently from both
the core and its transport implementation.

`server_version` identifies the qualified deployable REST-server artifact. It is
the value used by build qualification, deployment receipts, rollback diagnostics
and release verification.

The fields are deliberately independent:

```text
version == core_version
server_version != version
```

Release tooling must verify `server_version` when it needs the deployable server
artifact identity. It must not use `version` as a fallback because `version`
continues to report semantic core compatibility (`3.3`) across server releases.

The generated `org/kanger/build.properties` resource records both:

```properties
branch=server-0.17
server.version=server-0.17
source.branch=<source Git branch>
```

The source branch is provenance, not public release identity. Building the same
qualified release from a shelf, pull-request branch or local checkout must not
change `server_version`.

HTTP 204 responses have no body and therefore carry no version fields. All JSON
success and error responses, including `/health`, `/ready` and `/version`, are
decorated at the transport boundary rather than independently by each handler.
