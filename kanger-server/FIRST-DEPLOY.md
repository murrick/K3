# KANGER Server — first VPS deployment

This is the shortest qualified path for the first installation of the public
KANGER API at:

```text
https://api.kanger.org
```

The public web UI is a separate nginx virtual host and is intentionally outside
this first API deployment path:

```text
https://kanger.org
https://www.kanger.org
```

Target topology:

```text
Internet
   |
   v
Cloudflare or direct DNS
   |
   v
nginx api.kanger.org :80/:443
   |
   v
127.0.0.1:1964
   |
   v
KANGER Server JVM managed by systemd
```

Port `1964` is internal. Never expose it in the VPS firewall or bind it to a
public interface.

## Phase A — build and verify locally

From the repository root:

```bash
git fetch origin
git switch server/0.12-operational-boundary

git status --short

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

Set the actual SSH destination and port:

```bash
SSH_TARGET=user@vps
SSH_PORT=22
```

Prepare a clean temporary deployment directory:

```bash
ssh -p "${SSH_PORT}" "${SSH_TARGET}" \
  'rm -rf /tmp/kanger-deploy && mkdir -m 700 /tmp/kanger-deploy'
```

Copy the qualified JAR and deployment assets:

```bash
scp -P "${SSH_PORT}" \
  kanger-server/target/kanger-server.jar \
  "${SSH_TARGET}:/tmp/kanger-server.jar"

scp -P "${SSH_PORT}" -r \
  kanger-server/deploy/. \
  "${SSH_TARGET}:/tmp/kanger-deploy/"
```

Connect to the VPS:

```bash
ssh -p "${SSH_PORT}" "${SSH_TARGET}"
```

## Phase C — install the internal systemd service

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

The installer creates the dedicated `kanger` system account, installs the JAR,
configuration and systemd unit, starts the service, verifies both liveness and
readiness, and restores the previous JAR automatically if qualification fails.

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
sudo bash /tmp/kanger-deploy/verify-installed.sh
```

The required listener is equivalent to:

```text
127.0.0.1:1964
```

Linux may display the same IPv4 loopback endpoint as:

```text
[::ffff:127.0.0.1]:1964
```

The service must not listen on `0.0.0.0:1964` or `[::]:1964`.

At this point KANGER Server is running persistently under systemd but is not yet
publicly exposed. This is a valid deployment checkpoint.

## Phase D — prepare `api.kanger.org`

Create the API DNS record pointing to the VPS origin:

```text
Type: A
Name: api
Content: <VPS_IPV4>
Proxy: DNS only or Proxied
```

With Cloudflare proxying enabled, public DNS queries return Cloudflare addresses
rather than the origin address; that is expected.

Before enabling nginx HTTPS, make sure no other process or Docker container owns
public TCP port `443`:

```bash
sudo ss -ltnp | grep -E ':(80|443)\b' || true
sudo docker ps --format 'table {{.Names}}\t{{.Ports}}' 2>/dev/null || true
sudo iptables -t nat -S 2>/dev/null | grep -- '--dport 443' || true
```

If Docker publishes `0.0.0.0:443`, stop, remove or remap the owning container.
Do not delete generated Docker iptables rules manually: Docker will recreate
them. nginx must be the only public owner of TCP ports `80` and `443`.

Render the supplied nginx template for the API host:

```bash
sudo sed 's/KANGER_DOMAIN/api.kanger.org/g' \
  /tmp/kanger-deploy/nginx/kanger-server.conf.template \
  | sudo tee /etc/nginx/sites-available/kanger-server.conf >/dev/null
```

Choose exactly one certificate path below.

### TLS option 1 — Let's Encrypt

Install Certbot:

```bash
sudo apt update
sudo apt install -y certbot
sudo install -d -m 0755 /var/www/html/.well-known/acme-challenge
```

Create a temporary HTTP-only ACME configuration:

```bash
sudo tee /etc/nginx/sites-available/kanger-acme.conf >/dev/null <<'EOF'
server {
    listen 80;
    listen [::]:80;
    server_name api.kanger.org;

    location /.well-known/acme-challenge/ {
        root /var/www/html;
    }

    location / {
        return 404;
    }
}
EOF

sudo ln -sfn \
  /etc/nginx/sites-available/kanger-acme.conf \
  /etc/nginx/sites-enabled/kanger-acme.conf

sudo nginx -t
sudo systemctl restart nginx
```

Obtain the certificate:

```bash
sudo certbot certonly \
  --webroot \
  -w /var/www/html \
  -d api.kanger.org
```

The supplied nginx template already expects:

```text
/etc/letsencrypt/live/api.kanger.org/fullchain.pem
/etc/letsencrypt/live/api.kanger.org/privkey.pem
```

Remove the temporary ACME-only site after the certificate exists:

```bash
sudo rm -f /etc/nginx/sites-enabled/kanger-acme.conf
```

### TLS option 2 — shared Cloudflare Origin CA certificate

This option is valid only when `api.kanger.org` is proxied through Cloudflare
and Cloudflare SSL/TLS mode is `Full (strict)`.

The certificate files keep the base-domain names because the same certificate
may later be used by the UI virtual hosts:

```text
kanger.org.pem
kanger.org.key
origin_ca_rsa_root.pem
```

For all three public hosts, the certificate must cover:

```text
kanger.org
*.kanger.org
```

The apex entry covers `kanger.org`; the wildcard covers `api.kanger.org` and
`www.kanger.org`.

On the machine holding the files, verify the certificate before copying it:

```bash
openssl x509 \
  -in kanger.org.pem \
  -noout -subject -issuer -dates -ext subjectAltName

openssl verify \
  -CAfile origin_ca_rsa_root.pem \
  kanger.org.pem

openssl x509 -in kanger.org.pem -pubkey -noout \
  | openssl pkey -pubin -outform DER \
  | shasum -a 256

openssl pkey -in kanger.org.key -pubout -outform DER \
  | shasum -a 256
```

The two SHA-256 values must match.

Copy the files to the VPS, then install them:

```bash
sudo install -d \
  -o root -g root -m 0700 \
  /etc/nginx/ssl/kanger.org

sudo install \
  -o root -g root -m 0644 \
  /tmp/kanger.org.pem \
  /etc/nginx/ssl/kanger.org/kanger.org.pem

sudo install \
  -o root -g root -m 0600 \
  /tmp/kanger.org.key \
  /etc/nginx/ssl/kanger.org/kanger.org.key

sudo install \
  -o root -g root -m 0644 \
  /tmp/origin_ca_rsa_root.pem \
  /etc/nginx/ssl/kanger.org/origin_ca_rsa_root.pem
```

Replace the Let's Encrypt paths in the rendered API configuration:

```bash
sudo sed -i \
  -e 's#/etc/letsencrypt/live/api.kanger.org/fullchain.pem#/etc/nginx/ssl/kanger.org/kanger.org.pem#' \
  -e 's#/etc/letsencrypt/live/api.kanger.org/privkey.pem#/etc/nginx/ssl/kanger.org/kanger.org.key#' \
  /etc/nginx/sites-available/kanger-server.conf
```

Remove temporary copies after installation:

```bash
rm -f \
  /tmp/kanger.org.key \
  /tmp/kanger.org.pem \
  /tmp/origin_ca_rsa_root.pem
```

A Cloudflare Origin CA certificate is trusted by Cloudflare, not necessarily by
a browser connecting directly to the VPS. Public clients receive Cloudflare's
edge certificate.

## Phase E — enable nginx and verify HTTPS

Enable the KANGER API site:

```bash
sudo ln -sfn \
  /etc/nginx/sites-available/kanger-server.conf \
  /etc/nginx/sites-enabled/kanger-server.conf

sudo nginx -t
sudo systemctl restart nginx
```

Confirm that nginx owns the public HTTPS port:

```bash
sudo ss -ltnp | grep -E ':(80|443)\b'
```

Verify the API origin locally, bypassing public DNS and Cloudflare:

```bash
curl -k --fail --silent --show-error \
  --resolve api.kanger.org:443:127.0.0.1 \
  https://api.kanger.org/health
echo

curl -k --fail --silent --show-error \
  --resolve api.kanger.org:443:127.0.0.1 \
  https://api.kanger.org/ready
echo
```

Then verify the public route from another machine:

```bash
curl --fail --silent --show-error \
  https://api.kanger.org/health
echo
```

Expected liveness status:

```json
{"result":"OK","status":"UP","version":"..."}
```

Detailed `/ready` counters remain local to the VPS. A public request to
`https://api.kanger.org/ready` must be rejected by nginx with HTTP `403`.

## Diagnosis

```bash
sudo journalctl -u kanger-server.service -n 200 --no-pager
sudo journalctl -u kanger-server.service -f
sudo nginx -t
sudo tail -n 100 /var/log/nginx/error.log
sudo ss -ltnp | grep -E ':(80|443|1964)\b'
sudo docker ps --format 'table {{.Names}}\t{{.Ports}}' 2>/dev/null || true
sudo iptables -t nat -S 2>/dev/null | grep -- '--dport 443' || true
```

When a public HTTPS connection completes TLS but hangs after sending the HTTP
request, inspect the route to the origin. A Docker DNAT rule for public TCP/443
can silently divert Cloudflare traffic away from nginx.

## First-deployment boundary

Keep mail disabled for the initial launch:

```properties
server.email.mode=disabled
```

Password-only registration and authentication remain available. Configure SMTP
only after the internal service and the complete public API boundary at
`https://api.kanger.org` have both been verified.
