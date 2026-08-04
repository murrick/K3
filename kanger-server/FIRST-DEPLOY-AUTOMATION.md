# KANGER Server automated first deployment

The automated first-deployment entrypoint is:

```text
kanger-server/deploy/kanger-deploy.sh
```

It is the deployment-side pair of:

```text
kanger-server/deploy/kanger-update.sh
```

Use `kanger-deploy.sh` only for the first KANGER Server application installation
on a provisioned VPS. Use `kanger-update.sh` for all later releases.

## Host boundary required before running

The script deliberately does not create external infrastructure or store secret
material. Before running it, the target VPS must already provide:

- SSH and sudo access;
- Java, systemd, curl, nginx, `sha256sum`, `flock` and `install`;
- DNS and TLS for `api.kanger.org`;
- the nginx API/UI boundary described in `FIRST-DEPLOY.md` and `DEPLOYMENT.md`.

The Java service itself may not already be installed unless `--force` is supplied
explicitly.

## Default deployment

```bash
bash kanger-server/deploy/kanger-deploy.sh
```

Defaults:

```text
source ref: develop/server/0.13
SSH target: murray@94.103.94.41
SSH port:   4211
API:        https://api.kanger.org
UI:         https://kanger.org
```

Inspect the resolved plan without repository, build, SSH or HTTP actions:

```bash
bash kanger-server/deploy/kanger-deploy.sh --dry-run
```

## Pipeline

```text
fetch controlled shelf ref
→ resolve one exact commit
→ clean detached checkout
→ verify host prerequisites and absence of an installation
→ Maven clean verify
→ validate JAR metadata and provenance
→ calculate SHA-256
→ upload deployment assets and JAR
→ verify uploaded checksum
→ run install.sh under the deployment lock
→ verify systemd, loopback readiness and nginx
→ verify public /health, protected /ready and UI
→ write /opt/kanger-server/deployment.properties
```

`install.sh` remains responsible for installation and automatic JAR rollback.
The script does not replace `/etc/kanger-server/kanger.conf` or
`/var/lib/kanger-server` when they already exist.

## Existing-installation guard

When a KANGER systemd unit or active service is detected, the script stops before
Maven is run and directs the operator to `kanger-update.sh`.

An exceptional deliberate redeployment is possible with:

```bash
bash kanger-server/deploy/kanger-deploy.sh --force
```

`--force` must not be used for routine releases.

## Public-boundary option

For an internal checkpoint before Cloudflare/public nginx is reachable:

```bash
bash kanger-server/deploy/kanger-deploy.sh --no-public-checks
```

Local service, readiness, loopback confinement and nginx configuration checks
still run. Public validation must then be completed separately before closure.

## Receipt

After successful validation, the VPS contains:

```text
/opt/kanger-server/deployment.properties
```

with the artifact version, source ref, exact source commit, JAR SHA-256, build
date, deployment timestamp and deployment mode. Later `kanger-update.sh` runs use
this receipt for commit-aware no-op behavior.
