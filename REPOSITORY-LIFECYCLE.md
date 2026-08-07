# KANGER repository lifecycle

Date established: 2026-08-05  
Updated: 2026-08-07

## Canonical branch families

```text
release/<version>     immutable accepted snapshot of the complete repository
develop/<version>     integration line for the next complete artifact
<work>/<version.x>    temporary scoped work branch derived from current develop
ops/<artifact>        operations-only packaging, deployment and evidence line
antiguedades/<name>   preserved historical branch ref; never a development base
```

## Governing rules

1. New work starts from the latest accepted `release/*` through its immediate successor `develop/*`.
2. Scoped branches such as `audit/*`, `fix/*`, `server/*`, `ui/*`, `docs/*`, `arch/*` and `perf/*` are temporary lines and must return to the current `develop/*` integration line.
3. A qualified `develop/*` state becomes the next immutable `release/*` snapshot only after explicit acceptance.
4. Qualification does not itself authorize release creation, tagging, publication or production deployment.
5. A `release/*` branch is never advanced, rewritten or repaired in place. Any change creates a successor artifact.
6. Component shelves remain evidence of their original artifact but are not the default base for new work.
7. `ops/*` branches derive from one exact qualified integration head. They may add only packaging, snapshot, deployment, rollback and evidence material. They never become a source of product code.
8. `antiguedades/*` preserves pre-lifecycle historical refs. These branches are read-only archaeological material.
9. Product identities remain independent from repository artifact numbering:

```text
core_version
api_version
server_version
repository release artifact
```

## Architectural dependency principle

The Core remains a self-contained library boundary:

```text
kanger MUST NOT depend on:
  kanger-console
  kanger-data-dumb
  a concrete UDF implementation

Console / DB / UDF implementations MAY depend on kanger.
Qualification MAY depend on all of them.
```

This is a standing architectural principle. It is not a new 3.5.2 product change and is not a release blocker because the current repository already respects the boundary.

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
```

## Current candidate lifecycle

```text
develop/3.5.2
  baseline: 22918a09ce443e87cf0ee7397ff1b9f1b70f09e8

3.5.2.1 ... 3.5.2.8
  qualified audit and Browser/Server integration stages

3.5.2.9
  branch: develop/3.5.2.9-integration-release-shelf
  original qualified shelf: 7946d3969302aa198fea506f419a885565db118a
  runtime result: Server 0.17 VPS soak failed

3.5.2.10
  branch: develop/3.5.2.10-explicit-storage-lifecycle
  qualified head: 99185db7e1effccd810c9e8479bdceca5d61b31a
  PR: #74, merged
  integrated shelf: 03310482cebdf55b34829f3d59bdd197edb6275b
  role: Core-wide explicit storage lifecycle correction

3.5.2.11
  branch: server/3.5.2.11-server-0.18
  qualified code: a16ec7abb9b2df1aebbaed921088184f0e571c47
  documentation: 0213e82023a313641b05ff62d7381da5adc6da09
  PR: #75, merged
  integrated shelf: b967846832586858d42a5e21091154c682948d00
  role: fresh Server 0.18 candidate identity and qualification

3.5.2.12
  branch: fix/3.5.2.12-server-0.18-release-contract
  qualified head: d0dbfa89ef8bc82eb23113061a73d43166f13609
  PR: #76, merged
  integrated shelf: b0ed1cee70d6a4bbaf3b7690df766b9eae41f891
  role: Server 0.18 release contract and operations eligibility

3.5.2.13
  branch: fix/3.5.2.13-console-shutdown-lifecycle
  qualified head: df738ca6657fcc1fa15619e1d2b3cccd4e51b397
  PR: #78, merged
  integrated shelf: ddbf5ab380b4124013f58bbd655a2131ccba536b
  role: interactive Console shutdown lifecycle correction
  server identity: unchanged server-0.18

3.5.2.14
  branch: fix/3.5.2.14-post-shutdown-release-contract
  qualified head: 628295ead2fcd627c5e0302140278978caaeeff6
  PR: #79, merged
  integrated shelf: f9419c9428424b958bd938db0f6cf29650acf3f0
  role: re-qualify release shelf and operations provenance after 3.5.2.13

3.5.2.15
  branch: fix/3.5.2.15-storage-baseline-insertion
  qualified product head: 6b4b8e51c8ab4023cb5e81c2b2d9ec9ad9d5cdc3
  final documentation head: d894e6e3d17a3c7bfb6c7a5c110664f838c489bf
  PR: #81, merged
  integrated shelf: a70dd388576882aa4cf827a31b3f4724ac339b16
  role: storage baseline insertion and transaction quiescence stabilization
  manual torture qualification: PASS
  server identity: unchanged server-0.18

3.5.2.16
  branch: fix/3.5.2.16-post-baseline-insertion-release-contract
  base: a70dd388576882aa4cf827a31b3f4724ac339b16
  role: re-qualify canonical release shelf and operations provenance after 3.5.2.15
```

The original `3.5.2.9` shelf at `7946d396...`, Server 0.17 JAR, operations package and damaged soak database are immutable failed-soak evidence. They must not be reused, repaired in place or treated as a deployable candidate. The damaged database must not be opened, repaired, reindexed or deleted.

Server 0.18 was source-qualified before the Console-only and Core/Console storage-baseline stages. These later changes leave the Server product identity at `server-0.18`, but each changes the complete repository candidate and therefore requires repository-level release-contract qualification and fresh operations provenance.

## Storage baseline insertion contract

The only supported workspace-preserving `use` transformation in 3.5.2 is:

```text
offline workspace L0
        -> use database
workspace overlay L1
persistent database L0
```

The workspace is replayed/recompiled in the database context and remains provisional until explicit commit. Rollback leaves the database unchanged. Generated rules are derived state and are rebuilt in the new context rather than replayed as source.

A normal L1+ transaction stack cannot open a database. Generalized multi-level rebase is deliberately out of scope. `use`, `checkpoint` and `close` require both visible transaction level zero and no pending root child reservations.

## Integration rule for component changes

No pairwise synchronization triangle is maintained between Core, Server and Browser UI. Every accepted change returns to one current `develop/*` line containing the latest mutually compatible repository state.

A Core change that affects the packaged server requires qualification and a successor Server artifact before the next complete release. A Server API or session change requires Browser UI compatibility qualification. A Browser UI-only or Console-only change may leave the deployable Server identity unchanged, but it still requires repository-level qualification before release acceptance.

## Release-shelf qualification rule

The final integration state must prove from one source tree:

```text
complete ancestry from the develop baseline through every scoped checkpoint
exact declaration of each product-bearing delta
no undeclared product-code delta after the latest qualified checkpoint
independent core, console, storage, DUMB, server, Javadoc and browser qualification
exact product and distribution identities
matched browser/server deployment and rollback contract
explicit unresolved-risk and exclusion record
```

For `3.5.2.16`, the qualifier must separately prove:

```text
f9419c... -> a70dd388... = exactly the five declared 3.5.2.15 files
a70dd388... -> HEAD         = release-contract/documentation files only
server identity             = server-0.18 unchanged
```

A successful shelf is described as **qualified**, not **released** or **deployed**.

After explicit acceptance, the intended transition remains:

```text
qualified candidate shelf
    -> immutable release/3.5.2 branch
    -> optional immutable tag and GitHub Release
    -> separately evidenced production cutover
```

## Operations boundary

A fresh Server 0.18 operations line must derive exactly from the post-`3.5.2.15` release-contract-qualified shelf and must prove:

```text
operations-only delta
new Server 0.18 package and browser checksums
immutable source and packaging provenance
transactionally quiet host snapshot
verified off-host snapshot receipt
atomic versioned-directory UI publication
rollback to the exact Server 0.14 JAR/UI pair
fresh disposable database
nested transaction -> level-zero commit -> explicit close -> use/reopen
storage-baseline insertion -> explicit commit/rollback -> reopen
clean restart and abrupt-shutdown persistence checks
```

All earlier Server 0.18 packages are superseded as deployment provenance for the current complete candidate. They may remain historical build evidence but must not be used for the new soak.

An operations package is deployment evidence, not a source release. It must not be merged as product code or used as a base for successor development.

## Distribution boundary

Source snapshots live in `release/*`. Externally distributable bundles should additionally use an immutable tag and a GitHub Release containing the qualified fat JAR, browser inventory/checksums, administration kit, release notes and installation assets.
