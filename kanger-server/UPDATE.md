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
5. runs `mvn clean verify` for `kanger-server/pom.xml`;
6. validates the packaged JAR and its build metadata;
7. rejects empty or operational public versions such as `deployment` and
   `first-vps-deploy`;
8. calculates the local JAR SHA-256 and compares it with the installed JAR;
9. skips installation and restart when the exact same JAR is already installed;
10. otherwise stages the qualified JAR and deployment assets through SSH/SCP;
11. verifies the uploaded checksum on the VPS;
12. invokes `install.sh`, which preserves configuration/state and restores the
    previous JAR automatically when loopback health/readiness fail;
13. runs `verify-installed.sh` to confirm systemd, readiness, loopback binding
    and nginx configuration;
14. confirms that local `/health` and `/ready` expose the expected artifact
    version;
15. confirms through the public boundary that `/health` is UP, `/ready` remains
    HTTP 403, and the UI responds;
16. prints a deployment receipt with artifact version, source ref, commit and
    SHA-256.

Temporary VPS staging files are removed on both success and failure.

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
