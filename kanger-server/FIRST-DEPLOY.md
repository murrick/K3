# KANGER Server — first VPS deployment

This is the shortest qualified path for the first installation. It deliberately
separates the internal systemd service from public nginx exposure.

## Phase A — build and verify locally

From the repository root:

```bash
git fetch origin
git switch server/0.12-operational-boundary

mvn -B -ntp \
  -f kanger-server/pom.xml \
  -Dkanger.build.branch.override=first-vps-deploy \
  clean verify
```

Start the local isolated process:

```bash
bash kanger-server/scripts/run-local.sh
```

In another terminal:

```bash
bash kanger-server/scripts/smoke-local.sh
bash kanger-server/scripts/smoke-auth-local.sh
```

Stop the local process with `Ctrl+C`.

The deployable JAR is:

```text
kanger-server/target/kanger-server.jar
```

## Phase B — copy files to the VPS

Set the actual SSH target in the current shell:

```bash
SSH_TARGET=user@vps
```

Copy the qualified JAR and deployment assets:

```bash
scp kanger-server/target/kanger-server.jar \
  "${SSH_TARGET}:/tmp/kanger-server.jar"

scp -r kanger-server/deploy \
  "${SSH_TARGET}:/tmp/kanger-deploy"
```

## Phase C — install the internal service

Connect to the VPS:

```bash
ssh "${SSH_TARGET}"
```

Verify Java and required commands:

```bash
java -version
command -v java curl systemctl nginx
```

On Debian/Ubuntu, install Java 21 and curl when needed:

```bash
sudo apt update
sudo apt install -y openjdk-21-jre-headless curl
```

Install or update KANGER Server:

```bash
sudo bash /tmp/kanger-deploy/install.sh \
  /tmp/kanger-server.jar
```

The installer creates a dedicated `kanger` system account, installs the JAR,
config and systemd unit, starts the service, checks both liveness and readiness,
and restores the previous JAR automatically if either check fails.

Verify the internal service:

```bash
curl --fail --silent --show-error \
  http://127.0.0.1:1964/health
echo

curl --fail --silent --show-error \
  http://127.0.0.1:1964/ready
echo

sudo systemctl status kanger-server.service --no-pager
sudo ss -ltnp | grep 1964
```

Required listener:

```text
127.0.0.1:1964
```

The service must not listen on `0.0.0.0:1964` or `[::]:1964`.

Run the complete installed-service check:

```bash
sudo bash /tmp/kanger-deploy/verify-installed.sh
```

At this point KANGER Server is running persistently under systemd but is not yet
publicly exposed. This is a valid first deployment checkpoint.

## Phase D — enable public nginx HTTPS

This phase requires the final public domain and an existing valid certificate.
Do not expose Java port 1964 directly.

Set the domain:

```bash
DOMAIN=kanger.example.org
```

Render the supplied nginx template:

```bash
sudo cp \
  /tmp/kanger-deploy/nginx/kanger-server.conf.template \
  /etc/nginx/sites-available/kanger-server.conf

sudo sed -i "s/KANGER_DOMAIN/${DOMAIN}/g" \
  /etc/nginx/sites-available/kanger-server.conf
```

Verify these rendered certificate paths exist:

```text
/etc/letsencrypt/live/<domain>/fullchain.pem
/etc/letsencrypt/live/<domain>/privkey.pem
```

Enable and reload nginx:

```bash
sudo ln -sfn \
  /etc/nginx/sites-available/kanger-server.conf \
  /etc/nginx/sites-enabled/kanger-server.conf

sudo nginx -t
sudo systemctl reload nginx
```

External liveness check:

```bash
curl --fail --silent --show-error \
  "https://${DOMAIN}/health"
echo
```

Detailed `/ready` metrics remain restricted to local VPS requests by the nginx
template. nginx generates an `X-Request-ID` and the Java service writes the same
identifier, response status and latency to journald without logging request
bodies, query strings, passwords or session tokens.

## Diagnosis

```bash
sudo journalctl -u kanger-server.service -n 200 --no-pager
sudo journalctl -u kanger-server.service -f
sudo nginx -t
sudo tail -n 100 /var/log/nginx/error.log
```

## First-deployment boundary

Keep mail disabled for the initial launch:

```properties
server.email.mode=disabled
```

Password-only registration and authentication remain available. Configure SMTP
only after the internal service and public HTTPS boundary have both been
verified.
