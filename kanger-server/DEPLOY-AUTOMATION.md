# KANGER Server deployment automation

Two local orchestration scripts define the controlled production lifecycle:

```text
kanger-server/deploy/kanger-deploy.sh
kanger-server/deploy/kanger-update.sh
```

`kanger-deploy.sh` performs the first application deployment to a provisioned
VPS. `kanger-update.sh` performs every later shelf-to-production update.

Both scripts default to the approved shelf branch:

```text
develop/server/0.12
```

Both resolve one exact Git commit, run Maven qualification, validate packaged
metadata, calculate SHA-256, use the existing rollback-capable installer, verify
the loopback/nginx/public boundary and maintain the deployment receipt at:

```text
/opt/kanger-server/deployment.properties
```

The first-deployment script refuses an existing KANGER systemd installation by
default. The updater performs a true no-op when the receipt already records the
resolved source commit.

Detailed first-deployment host provisioning remains in `FIRST-DEPLOY.md` and
`DEPLOYMENT.md`. Automated first-deployment usage is described in
`FIRST-DEPLOY-AUTOMATION.md`; repeatable updates are described in `UPDATE.md`.
