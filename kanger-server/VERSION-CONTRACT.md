# KANGER Server version identity

Every non-empty JSON response produced by the HTTP boundary carries the same
version identity:

```json
{
  "version": "3.3",
  "core_version": "3.3",
  "api_version": "1",
  "server_version": "server-0.13"
}
```

`version` is the user-facing compatibility alias for `core_version`. It names
the semantic KANGER product reached through the API.

`core_version` changes when KANGER semantics or core compatibility changes.

`api_version` identifies the public protocol contract independently from both
the core and its transport implementation.

`server_version` identifies the qualified deployable REST-server artifact. It is
the value used by deployment receipts, rollback diagnostics and
`kanger-deploy.sh` / `kanger-update.sh` qualification.

During the transition from Server 0.12, the deployment scripts accept the old
`version` field as a fallback for server-artifact verification. Newly built
responses are verified through `server_version`; the fallback exists only so a
current production installation can be inspected safely during a rolling
upgrade.

HTTP 204 responses have no body and therefore carry no version fields. All JSON
success and error responses, including `/health`, `/ready` and `/version`, are
decorated at the transport boundary rather than independently by each handler.
