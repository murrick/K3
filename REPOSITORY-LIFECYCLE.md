# KANGER repository lifecycle

Date established: 2026-08-05  
Updated: 2026-08-06

## Canonical branch families

```text
release/<version>     immutable accepted snapshot of the complete repository
develop/<version>     integration line for the next complete artifact
<work>/<version.x>    temporary scoped work branch derived from current develop
ops/<artifact>        operations-only packaging, deployment and evidence line
antiguedades/<name>   preserved historical branch ref; never a development base
```

## Governing rules

1. New work starts from the latest accepted `release/*` through its immediate
   successor `develop/*`.
2. Scoped branches such as `audit/*`, `fix/*`, `server/*`, `ui/*`, `docs/*`,
   `arch/*` and `perf/*` are temporary lines of work and must return to the
   current `develop/*` integration line.
3. A qualified `develop/*` state becomes the next immutable `release/*`
   snapshot only after explicit acceptance.
4. Qualification does not itself authorize release creation, tagging,
   publication or production deployment.
5. A `release/*` branch is never advanced, rewritten or repaired in place. Any
   change creates a successor artifact.
6. Component shelves remain evidence of their original artifact but are not the
   default base for new work.
7. `ops/*` branches derive from one exact qualified integration head. They may
   add only packaging, snapshot, deployment, rollback and evidence material.
   They never become a source of product code.
8. `antiguedades/*` preserves pre-lifecycle historical refs. These branches are
   read-only archaeological material.
9. Product identities remain independent from repository artifact numbering:

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
```

## Current candidate lifecycle

```text
develop/3.5.2
  baseline: 22918a09ce443e87cf0ee7397ff1b9f1b70f09e8

3.5.2.1 ... 3.5.2.8
  qualified audit and implementation stages

3.5.2.9
  branch: develop/3.5.2.9-integration-release-shelf
  original qualified shelf: 7946d3969302aa198fea506f419a885565db118a
  runtime result: Server 0.17 VPS soak failed

3.5.2.10
  branch: develop/3.5.2.10-explicit-storage-lifecycle
  head:   99185db7e1effccd810c9e8479bdceca5d61b31a
  PR:     #74, merged
  role:   Core-wide explicit storage lifecycle correction
  integrated shelf: 03310482cebdf55b34829f3d59bdd197edb6275b

3.5.2.11
  branch: server/3.5.2.11-server-0.18
  base:   03310482cebdf55b34829f3d59bdd197edb6275b
  qualified code: a16ec7abb9b2df1aebbaed921088184f0e571c47
  documentation:  0213e82023a313641b05ff62d7381da5adc6da09
  PR:     #75, merged
  integrated shelf: b967846832586858d42a5e21091154c682948d00
  role:   fresh Server 0.18 candidate identity and qualification

3.5.2.12
  branch: fix/3.5.2.12-server-0.18-release-contract
  base:   b967846832586858d42a5e21091154c682948d00
  role:   replace stale Server 0.17 release tooling and qualify the integrated
          Server 0.18 source/deployment contract
```

The original `3.5.2.9` shelf at `7946d396...`, Server 0.17 JAR, operations
package and damaged soak database are immutable failed-soak evidence. They must
not be reused, repaired in place or treated as a deployable candidate. The
damaged database must not be opened, repaired, reindexed or deleted.

The corrected shelf at `03310482...` is the sole baseline of Server 0.18 work.
PR #75 integrated the exact qualified Server 0.18 state into the shelf as
`b9678468...`.

The merge exposed a stale specialized release-shelf workflow that still expected
Server 0.17. Stage `3.5.2.12` corrects only release workflow, qualification,
manifest, lifecycle, closure and deployment-contract files. It introduces no
Core, API, Server production, Server test or Browser runtime delta.

Server 0.18 becomes eligible for a fresh operations package only after the
`3.5.2.12` exact-head gate passes. It remains unaccepted as `release/3.5.2` and
undeployed until the fresh package, checksums, off-host snapshot receipt and
new-disposable-database VPS soak succeed.

## Integration rule for component changes

No pairwise synchronization triangle is maintained between Core, Server and
Browser UI. Every accepted change returns to one current `develop/*` line,
which contains the latest mutually compatible state of all repository
components.

A Core change that affects the packaged server requires qualification and a
successor Server artifact before the next complete release. A Server API or
session change requires Browser UI compatibility qualification. A Browser
UI-only change does not rewrite an accepted Server release; it enters the next
repository release through the current develop line.

## Release-shelf qualification rule

The final integration state must prove from one source tree:

```text
complete ancestry from the develop baseline through every scoped checkpoint
no undeclared product-code delta after the final implementation checkpoint
independent core, storage, DUMB, server, Javadoc and browser qualification
exact product and distribution identities
matched browser/server deployment and rollback contract
explicit unresolved-risk and exclusion record
```

A successful shelf is described as **qualified**, not **released** or
**deployed**.

After explicit acceptance, the intended transition is:

```text
qualified candidate shelf
    -> accepted integration commit
    -> immutable release/3.5.2 branch
    -> optional immutable tag and GitHub Release
    -> separately evidenced production cutover
```

For Server 0.18 the accepted integration decision has already authorized PR #75
merge and fresh operations-package construction. It has not yet created
`release/3.5.2`, a tag, a GitHub Release or permanent production acceptance.

## Operations boundary

A fresh Server 0.18 operations line must derive exactly from the
release-contract-qualified shelf and must prove:

```text
operations-only delta
new Server 0.18 JAR and browser checksums
immutable source and packaging provenance
transactionally quiet host snapshot
verified off-host snapshot receipt
atomic versioned-directory UI publication
rollback to the exact Server 0.14 JAR/UI pair
fresh disposable database
nested transaction -> level-zero commit -> explicit close -> use/reopen
```

An operations package is deployment evidence, not a source release. It must not
be merged as product code or used as a base for successor development.

## Distribution boundary

Source snapshots live in `release/*`. Externally distributable bundles should
additionally use an immutable tag and a GitHub Release containing the qualified
fat JAR, browser inventory/checksums, administration kit, release notes and
installation assets.
