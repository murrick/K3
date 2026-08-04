# KANGER Server repeatable update

This procedure updates the existing production service from a controlled Git
reference without rebuilding the VPS configuration by hand.

The updater is:

```text
kanger-server/deploy/kanger-update.sh
```

Its default production source is the shelf branch:

```text
develop/server/0.12
```

The updater deliberately does not deploy the latest arbitrary working branch.
Move the shelf branch only after code qualification and production approval.

## Default update

From any local copy of the script:

```bash
bash kanger-server/deploy/kanger-update.sh
```

The current project defaults are:

```text
repository: https://github.com/murrick/K3.git
source ref: develop/server/0.12
SSH target: murray@94.103.94.41
SSH port:   4211
public API: https://api.kanger.org
public UI:  https://kanger.org
```

No password, private key or application credential is stored in the script.
Normal SSH and sudo authentication continue to apply.

To keep the command outside a source checkout:

```bash
install -m 0755 \
  kanger-server/deploy/kanger-update.sh \
  "$HOME/bin/kanger-update.sh"

"$HOME/bin/kanger-update.sh"
```

## What the updater does

1. creates or reuses a dedicated deployment checkout under
   `~/.cache/kanger-server-updater/K3`;
2. fetches and prunes the latest repository refs;
3. resolves the requested shelf branch, tag or commit to one exact commit SHA;
4. checks out that commit in detached mode and removes stale untracked build
   output from the dedicated checkout;
5. reads the production deployment receipt from
   `/opt/kanger-server/deployment.properties` when present;
6. when the same source commit is already deployed, skips Maven and restart but
   still re-runs service, nginx and public-API verification;
7. when a previous run installed the qualified cached JAR but stopped before
   writing its receipt, compares the cached and installed SHA-256 values and
   recovers the receipt without Maven or restart;
8. otherwise runs `mvn clean verify` for `kanger-server/pom.xml`;
9. validates the packaged JAR and its build metadata;
10. rejects empty or operational public versions such as `deployment` and
    `first-vps-deploy`;
11. calculates the local JAR SHA-256 and verifies it after upload;
12. serializes installation through
    `/run/lock/kanger-server-update.lock` so concurrent update attempts cannot
    replace the JAR simultaneously;
13. delegates installation and automatic rollback to the existing `install.sh`;
14. runs `verify-installed.sh` to confirm systemd, readiness, loopback binding
    and nginx configuration;
15. confirms that local `/health` and `/ready` expose the expected artifact
    version;
16. confirms through the public API boundary that `/health` is UP and `/ready`
    remains HTTP 403;
17. writes an auditable production receipt containing artifact version, source
    ref, source commit, JAR checksum, build date and deployment time;
18. checks the separate public UI with a bounded `HEAD` request. UI timeout or an
    unexpected UI status is reported as a warning and cannot invalidate an
    already verified backend deployment;
19. prints the same deployment identity as the local completion receipt.

Temporary VPS staging files are removed on both success and failure.

The source-commit receipt is necessary because the JAR contains build time
metadata. Two builds of the same source commit can therefore have different
binary checksums even though no source update exists. Commit identity decides
whether an update is required; SHA-256 validates the exact binary being moved.

## Interrupted completion recovery

A backend deployment can succeed while a later external check is interrupted.
The updater therefore treats the public API as the authoritative deployment
boundary and the separate UI as advisory.

When the exact JAR from the previous qualified checkout is already installed,
the next run compares its SHA-256 with the VPS JAR. If the checkout still points
to the same resolved source commit, the updater verifies the service and public
API, writes the missing receipt and exits without rebuilding or restarting the
server.

## Preview

Print the resolved configuration without fetching, building or connecting:

```bash
bash kanger-server/deploy/kanger-update.sh --dry-run
```

## Explicit source override

A non-shelf branch, tag or commit must be requested explicitly:

```bash
bash kanger-server/deploy/kanger-update.sh \
  --ref server/0.12.1-update-automation
```

This capability is intended for controlled qualification. Production updates
should normally use `develop/server/0.12` or a later approved shelf branch.

## Other options

```text
--repo-url URL
--checkout DIR
--target USER@HOST
--port PORT
--api-url URL
--ui-url URL
--force
--no-public-checks
--dry-run
```

`--force` rebuilds and reinstalls even when the production receipt already names
the same source commit.

The equivalent environment variables are:

```text
KANGER_REF
KANGER_REPO_URL
KANGER_CHECKOUT_DIR
KANGER_SSH_TARGET
KANGER_SSH_PORT
KANGER_PUBLIC_API_URL
KANGER_PUBLIC_UI_URL
```

Example with environment variables:

```bash
KANGER_REF=develop/server/0.12 \
KANGER_SSH_TARGET=murray@94.103.94.41 \
KANGER_SSH_PORT=4211 \
  "$HOME/bin/kanger-update.sh"
```

## Deployment receipt

After a successful update the VPS contains:

```text
/opt/kanger-server/deployment.properties
```

Example shape:

```properties
artifact.version=server-0.12
source.ref=develop/server/0.12
source.commit=3cdb39dece2d2294046e150808e02753bc7bf36e
jar.sha256=<qualified JAR SHA-256>
build.date=<JAR build metadata date>
deployed.at=<UTC deployment timestamp>
```

The receipt is operational metadata, contains no credential, and is installed
read-only for ordinary users. It is written only after installation, local
verification and required public-API verification succeed. A timeout from the
independent public UI check does not suppress the receipt.

## Failure and rollback semantics

The update stops before installation when Git resolution, Maven qualification,
JAR metadata or checksum verification fails.

Once installation begins, `install.sh` stores the current JAR as:

```text
/opt/kanger-server/kanger-server.jar.previous
```

If the newly installed server does not pass loopback liveness and readiness,
`install.sh` restores that previous JAR and restarts the service. The persistent
configuration in `/etc/kanger-server/kanger.conf` and user state in
`/var/lib/kanger-server` are not replaced by the updater.

The updater does not create or delete production users. After an update that
changes runtime behavior, manually confirm login with an existing production
account and verify its persistent state.