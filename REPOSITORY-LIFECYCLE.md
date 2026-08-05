# KANGER repository lifecycle

Date established: 2026-08-05

## Canonical branch families

```text
release/<version>     immutable accepted snapshot of the complete repository
develop/<version>     integration line for the next complete artifact
<work>/<version.x>    temporary scoped work branch derived from current develop
antiguedades/<name>   preserved historical branch ref; never a development base
```

## Governing rules

1. New work starts from the latest accepted `release/*` through its immediate successor `develop/*`.
2. Scoped branches such as `audit/*`, `fix/*`, `server/*`, `ui/*`, `docs/*`, `arch/*` and `perf/*` are temporary lines of work and must return to the current `develop/*` integration line.
3. A qualified `develop/*` state becomes the next immutable `release/*` snapshot.
4. A `release/*` branch is never advanced, rewritten or repaired in place. Any change creates a successor artifact.
5. Component shelves such as historical `develop/server/*` and `develop/3.5.0.*` branches remain evidence of their original artifact but are not the default base for new work.
6. `antiguedades/*` preserves pre-lifecycle historical refs. These branches are read-only archaeological material.
7. Product identities remain independent from repository artifact numbering:

```text
core_version
api_version
server_version
repository release artifact
```

## Established cornerstone artifacts

```text
release/3.5.0
  source ref: develop/3.5.0.7
  commit:     10bd30e08a6b45465a4de4369608d995df2dbbb9
  meaning:    accepted KANGER III core consolidation baseline

release/3.5.1
  source ref: develop/server/0.14
  commit:     35166d91d8082ec267d892d4451504bcb9890330
  meaning:    complete accepted repository after KANGER Server 0.14

current integration line:
  develop/3.5.2

next scoped artifact:
  audit/3.5.2.1-browser-js
```

## Integration rule for component changes

No pairwise synchronization triangle is maintained between Core, Server and Browser UI. Instead, every accepted change returns to one current `develop/*` line, which always contains the latest mutually compatible state of all repository components.

A new Core change that affects the packaged server requires qualification and a successor Server artifact before the next complete release. A Server API or session change requires Browser UI compatibility qualification. A Browser UI-only change does not rewrite an accepted Server release; it enters the next repository release through the current develop line.

## Distribution boundary

Source snapshots live in `release/*`. Future externally distributable bundles should additionally use an immutable tag and a GitHub Release containing the qualified fat JAR, checksum manifest, administration kit, release notes and installation assets.
