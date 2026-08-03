# KANGER Server VPS deployment

This guide deploys the standalone KANGER Server JAR on a Debian/Ubuntu-style
systemd host behind nginx.

The public API endpoint is:

```text
https://api.kanger.org
```

The browser UI is a separate deployment target:

```text
https://kanger.org
https://www.kanger.org
```

The UI may be served by another nginx virtual host on the same VPS, using the
files in the repository `html/` directory. This document installs and exposes
the API only; the UI virtual host can be added independently without changing
the Java service.

Target topology:

```text
Internet
   |
   v
Cloudflare or direct DNS
   |
   +--> kanger.org / www.kanger.org --> nginx static UI --> K3/html
   |
   +--> api.kanger.org --> nginx reverse proxy
                              |
                              v
                         127.0.0.1:1964
                              |
                              v
                    KANGER Server JVM managed by systemd
```

Port `1964` is an internal application port. It must never be opened in the VPS
firewall or bound to a public interface.

## 1. Build and qualify the artifact

On the development machine, from the repository root:

```bash
git fetch origin
git switch server/0.12-operational-boundary

git status --short

mvn -B -ntp \
  -f kanger-server/pom.xml \
  -Dkanger.build.branch.override=deployment \
  clean verify
```

Do not build from a working tree with unexplained local changes. IDE metadata
such as `.idea` files is not part of the server artifact and should not be mixed
into deployment changes.

Start the isolated local process:

```bash
bash kanger-server/scripts/run-local.sh
```

In another terminal:

```bash
bash kanger-server/scripts/smoke-local.sh
bash kanger-server/scripts/smoke-auth-local.sh
```

Stop the local process with `Ctrl+C`.

The deployable artifact is:

```text
kanger-server/target/kanger-server.jar
```

## 2. Copy the distribution to the VPS

Set the actual SSH destination and port:

```bash
SSH_TARGET=user@vps
SSH_PORT=22
```

Prepare a clean temporary deployment directory. This avoids accidental nested
`deploy/deploy` directories from a previous copy:

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

The deployment directory contains:

```text
install.sh
verify-installed.sh
kanger.conf.example
systemd/kanger-server.service
nginx/kanger-server.conf.template
```

Connect to the VPS:

```bash
ssh -p "${SSH_PORT}" "${SSH_TARGET}"
```

## 3. Verify Java and host prerequisites

KANGER Server is qualified on Java 8 and Java 21. Java 21 is recommended for
the service installation.

```bash
java -version
readlink -f "$(command -v java)"
test -x /usr/bin/java
command -v curl systemctl nginx
```

When Java is absent on a compatible Debian/Ubuntu release:

```bash
sudo apt update
sudo apt install -y openjdk-21-jre-headless curl
```

Do not continue until `java -version`, `/usr/bin/java`, `curl`, `systemctl` and
`nginx` are valid.

## 4. Install the internal service

Run the installer:

```bash
sudo bash /tmp/kanger-deploy/install.sh \
  /tmp/kanger-server.jar
```

The installer:

1. creates the system user and group `kanger` when absent;
2. creates `/opt/kanger-server`, `/var/lib/kanger-server`, and
   `/etc/kanger-server`;
3. installs the JAR as `/opt/kanger-server/kanger-server.jar`;
4. preserves the previous JAR as `kanger-server.jar.previous` during updates;
5. installs `/etc/systemd/system/kanger-server.service`;
6. creates `/etc/kanger-server/kanger.conf` only on first installation;
7. links that configuration into the service `user.home`;
8. enables and starts the service;
9. waits for both `http://127.0.0.1:1964/health` and
   `http://127.0.0.1:1964/ready`;
10. restores the previous JAR automatically if qualification fails.

A manual `curl` issued during the short restart window may briefly fail before
the Java listener is open. The installer itself waits for the qualified
liveness and readiness endpoints before reporting success.

Check the internal endpoints:

```bash
curl --fail --silent --show-error \
  http://127.0.0.1:1964/health
echo

curl --fail --silent --show-error \
  http://127.0.0.1:1964/ready
echo
```

Expected liveness response shape:

```json
{"result":"OK","status":"UP","version":"..."}
```

Expected readiness response shape includes `status: READY` and bounded executor
counters such as `active_requests`, `queued_requests`, `queue_capacity`,
`queue_remaining`, `failed_requests`, and `overload_rejections`.

Verify systemd and the listener:

```bash
sudo systemctl status kanger-server.service --no-pager
sudo systemctl is-enabled kanger-server.service
sudo systemctl is-active kanger-server.service
sudo ss -ltnp | grep 1964
sudo bash /tmp/kanger-deploy/verify-installed.sh
```

The KANGER listener must be equivalent to:

```text
127.0.0.1:1964
```

Linux may display the same IPv4 loopback endpoint in IPv4-mapped IPv6 notation:

```text
[::ffff:127.0.0.1]:1964
```

It must not appear as:

```text
0.0.0.0:1964
[::]:1964
```

At this point KANGER Server is running persistently under systemd but is not yet
publicly exposed. This is a valid deployment checkpoint.

## 5. Review the service configuration

The persistent configuration is:

```text
/etc/kanger-server/kanger.conf
```

It must contain:

```properties
server.bind.address=127.0.0.1
server.port=1964
server.email.mode=disabled
```

The public API URL used in generated confirmation links is:

```properties
server.url=https://api.kanger.org
```

The Java transport uses an exact CORS allow-list. When both UI hostnames may
call the API directly, configure both:

```properties
server.cors.allowed.origin.1=https://kanger.org
server.cors.allowed.origin.2=https://www.kanger.org
server.cors.allow.credentials=false
```

If `www.kanger.org` is permanently redirected to `kanger.org` before the UI is
loaded, only the canonical UI origin is needed. Never use `*` with credentials.

After changing configuration:

```bash
sudo systemctl restart kanger-server.service
sudo systemctl status kanger-server.service --no-pager
curl --fail http://127.0.0.1:1964/health
curl --fail http://127.0.0.1:1964/ready
```

Never change `server.bind.address` to `0.0.0.0` or to a public VPS address.
nginx is the public boundary.

## 6. Configure DNS

Create the API record:

```text
Type: A
Name: api
Content: <VPS_IPV4>
Proxy: DNS only or Proxied
```

The later UI deployment uses separate records:

```text
Type: A or CNAME
Name: @
Target: <VPS origin or provider-specific target>

Type: A or CNAME
Name: www
Target: <VPS origin, kanger.org, or provider-specific target>
```

With Cloudflare proxying enabled, public `dig` queries return Cloudflare edge
addresses rather than `<VPS_IPV4>`. That is expected.

Useful checks:

```bash
dig +short A api.kanger.org
dig +short A kanger.org
dig +short A www.kanger.org
dig +short NS kanger.org
```

A registrar panel may describe a domain as not delegated to that registrar's
own DNS service even while the domain is correctly delegated to Cloudflare or
another authoritative DNS provider. The authoritative NS chain and successful
public DNS answers are the source of truth.

## 7. Reserve public TCP ports for nginx

Before enabling public HTTPS, confirm that no other process or container owns
TCP ports `80` or `443`:

```bash
sudo ss -ltnp | grep -E ':(80|443)\b' || true
sudo docker ps --format 'table {{.ID}}\t{{.Names}}\t{{.Ports}}' 2>/dev/null || true
sudo iptables -t nat -S 2>/dev/null | grep -- '--dport 443' || true
```

A Docker publication such as:

```text
0.0.0.0:443->443/tcp
```

creates a DNAT rule and can divert all external HTTPS traffic away from nginx
even when nginx works correctly on loopback. A typical packet trace then shows
traffic arriving on the public interface and being forwarded to a container
address.

Identify the owner with:

```bash
sudo docker inspect $(sudo docker ps -q) \
  --format '{{.Name}} {{range .NetworkSettings.Networks}}{{.IPAddress}} {{end}} {{json .HostConfig.PortBindings}}' \
  | grep 443 || true
```

Stop, remove or remap an unneeded conflicting container. Do not delete generated
Docker iptables rules manually: Docker will recreate them. nginx must be the
only public owner of TCP ports `80` and `443`.

## 8. Render the API nginx configuration

Render the supplied template for the API host:

```bash
sudo sed 's/KANGER_DOMAIN/api.kanger.org/g' \
  /tmp/kanger-deploy/nginx/kanger-server.conf.template \
  | sudo tee /etc/nginx/sites-available/kanger-server.conf >/dev/null
```

The template:

- redirects API HTTP to HTTPS;
- proxies `/health` publicly;
- restricts detailed `/ready` metrics to local VPS requests;
- forwards `X-Request-ID`, client address and original scheme;
- keeps the Java service private on `127.0.0.1:1964`.

Choose exactly one TLS option below before enabling the site.

## 9A. TLS option 1 — Let's Encrypt for `api.kanger.org`

Use this option when the VPS should obtain and renew a publicly trusted API
certificate directly.

Install Certbot and prepare the webroot:

```bash
sudo apt update
sudo apt install -y certbot
sudo install -d -m 0755 /var/www/html/.well-known/acme-challenge
```

Create a temporary HTTP-only ACME site:

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

Make sure public TCP/80 reaches this nginx instance, then obtain the certificate:

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

Test automatic renewal without changing the certificate:

```bash
sudo certbot renew --dry-run
```

## 9B. TLS option 2 — shared Cloudflare Origin CA certificate

Use this option only when the public hosts are proxied through Cloudflare.
Configure Cloudflare SSL/TLS mode as:

```text
Full (strict)
```

This branch is intentionally self-contained. Render or re-render the API nginx
configuration before replacing its default Let's Encrypt certificate paths:

```bash
sudo sed 's/KANGER_DOMAIN/api.kanger.org/g' \
  /tmp/kanger-deploy/nginx/kanger-server.conf.template \
  | sudo tee /etc/nginx/sites-available/kanger-server.conf >/dev/null
```

The certificate files deliberately use the base-domain names even though this
nginx virtual host serves `api.kanger.org`:

```text
kanger.org.pem
kanger.org.key
origin_ca_rsa_root.pem
```

To serve the API and both UI hostnames with this one certificate, request SANs
covering:

```text
kanger.org
*.kanger.org
```

The apex entry covers `kanger.org`. The wildcard covers `api.kanger.org` and
`www.kanger.org`; a wildcard alone does not cover the apex domain.

Before copying the files, inspect the certificate:

```bash
openssl x509 \
  -in kanger.org.pem \
  -noout -subject -issuer -dates -ext subjectAltName
```

Verify the chain against the supplied Cloudflare Origin CA root:

```bash
openssl verify \
  -CAfile origin_ca_rsa_root.pem \
  kanger.org.pem
```

Expected result:

```text
kanger.org.pem: OK
```

Verify that the private key matches the certificate without displaying private
key material:

```bash
openssl x509 -in kanger.org.pem -pubkey -noout \
  | openssl pkey -pubin -outform DER \
  | shasum -a 256

openssl pkey -in kanger.org.key -pubout -outform DER \
  | shasum -a 256
```

The two SHA-256 values must match. If the key is encrypted and prompts for a
password, nginx cannot restart unattended without additional key handling.

Copy the files to the VPS using the configured SSH port. On the VPS, install the
certificate and key with strict permissions:

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

Enable this rendered site immediately, validate it and start nginx:

```bash
sudo ln -sfn \
  /etc/nginx/sites-available/kanger-server.conf \
  /etc/nginx/sites-enabled/kanger-server.conf

sudo nginx -t
sudo systemctl restart nginx
sudo systemctl status nginx --no-pager
sudo ss -ltnp | grep -E ':(80|443)\b'
```

Do not continue to HTTPS checks unless nginx owns public TCP ports `80` and
`443`.

Remove temporary copies after installation:

```bash
rm -f \
  /tmp/kanger.org.key \
  /tmp/kanger.org.pem \
  /tmp/origin_ca_rsa_root.pem
```

A Cloudflare Origin CA certificate is designed for the encrypted and
authenticated Cloudflare-to-origin connection. A browser connecting directly
to the VPS may not trust it; public clients receive Cloudflare's edge
certificate instead.

The same installed files may later be referenced by the separate UI virtual
host for `kanger.org` and `www.kanger.org`.

## 10. Enable API HTTPS

For Let's Encrypt, enable the API site now. For Cloudflare Origin CA, these
commands were already run inside option 2 and are safe to repeat:

```bash
sudo ln -sfn \
  /etc/nginx/sites-available/kanger-server.conf \
  /etc/nginx/sites-enabled/kanger-server.conf

sudo nginx -t
sudo systemctl restart nginx
sudo systemctl status nginx --no-pager
```

Confirm that nginx owns the public ports:

```bash
sudo ss -ltnp | grep -E ':(80|443)\b'
```

Verify the API origin locally, bypassing DNS and Cloudflare:

```bash
curl -k --fail --silent --show-error \
  --connect-timeout 5 \
  --max-time 10 \
  --resolve api.kanger.org:443:127.0.0.1 \
  https://api.kanger.org/health
echo

curl -k --fail --silent --show-error \
  --connect-timeout 5 \
  --max-time 10 \
  --resolve api.kanger.org:443:127.0.0.1 \
  https://api.kanger.org/ready
echo
```

For a Cloudflare Origin CA certificate, verify the origin chain explicitly:

```bash
openssl s_client \
  -connect 127.0.0.1:443 \
  -servername api.kanger.org \
  -CAfile /etc/nginx/ssl/kanger.org/origin_ca_rsa_root.pem \
  </dev/null 2>/dev/null \
  | grep 'Verify return code'
```

Expected result:

```text
Verify return code: 0 (ok)
```

Verify the public API route from a different machine:

```bash
curl --fail --silent --show-error \
  --connect-timeout 5 \
  --max-time 20 \
  https://api.kanger.org/health
echo
```

Expected response:

```json
{"result":"OK","status":"UP","version":"..."}
```

Detailed readiness metrics remain local. A public request must be rejected:

```bash
curl -i \
  --connect-timeout 5 \
  --max-time 20 \
  https://api.kanger.org/ready
```

Expected public result:

```text
HTTP/... 403
```

## 11. Firewall boundary

Allow only the intended public services and the actual SSH port:

```text
80/tcp
443/tcp
<SSH_PORT>/tcp
```

Port `1964` must not be allowed publicly.

For UFW, inspect before modifying:

```bash
sudo ufw status verbose
```

Typical rules are:

```bash
sudo ufw allow 80/tcp
sudo ufw allow 443/tcp
sudo ufw allow "${SSH_PORT}/tcp"
sudo ufw reload
```

Also inspect provider-level firewalls or security groups when packets never
reach the VPS.

## 12. Request correlation and operational endpoints

nginx assigns `X-Request-ID` and forwards it to KANGER Server. The Java service
returns the same identifier and writes a structured journald entry containing:

```text
request_id
method
sanitized path without query string
status
duration_ms
active worker count
queued request count
```

Request bodies, query strings, passwords and session tokens are not written to
transport logs.

Endpoints:

```text
/health  process liveness; public through api.kanger.org
/ready   bounded-capacity readiness; local VPS only
```

When worker and queue capacity is exhausted, KANGER Server returns explicit HTTP
`503` with `Retry-After: 1` rather than accepting unbounded work.

## 13. Enable confirmation mail

Leave mail disabled until the internal service, nginx, DNS, `server.url` and the
complete API route at `https://api.kanger.org` are correct.

Then follow:

```text
kanger-server/MAIL-CONFIGURATION.md
```

Choose exactly one mode:

```properties
server.email.mode=starttls
```

or:

```properties
server.email.mode=smtps
```

Never enable STARTTLS and implicit TLS simultaneously. The transport uses the
Java platform trust store and hostname verification, finite connection/read/
write timeouts, JavaMail debug off by default, and a bounded worker queue.

Protect the configuration after adding credentials:

```bash
sudo chown root:kanger /etc/kanger-server/kanger.conf
sudo chmod 0640 /etc/kanger-server/kanger.conf
sudo systemctl restart kanger-server.service
```

When `server.email.mode=disabled`, registrations containing a non-empty e-mail
address and resend requests are rejected before the legacy mail paths. Ordinary
password-only registration and authentication remain available.

## 14. Logs and diagnosis

Service state and logs:

```bash
sudo systemctl status kanger-server.service --no-pager
sudo journalctl -u kanger-server.service -n 200 --no-pager
sudo journalctl -u kanger-server.service -f
```

nginx validation and logs:

```bash
sudo nginx -t
sudo systemctl status nginx --no-pager
sudo tail -n 200 /var/log/nginx/error.log
sudo tail -n 200 /var/log/nginx/access.log
```

Listener and Docker routing diagnosis:

```bash
sudo ss -ltnp | grep -E ':(80|443|1964)\b'
sudo docker ps --format 'table {{.ID}}\t{{.Names}}\t{{.Ports}}' 2>/dev/null || true
sudo iptables -t nat -S 2>/dev/null | grep -- '--dport 443' || true
sudo nft list ruleset 2>/dev/null | grep -nE '80|443|drop|reject' || true
```

Packet tracing when Cloudflare or another external client hangs:

```bash
sudo tcpdump -ni any tcp port 443
```

Interpretation:

- no inbound SYN reaches the VPS: check provider firewall, route and DNS origin;
- inbound traffic reaches the VPS but is DNATed to a container: free or remap
  that container's public `443` binding;
- nginx receives the connection but `/health` fails: check nginx error log and
  `http://127.0.0.1:1964/health`;
- public TLS completes and the HTTP request hangs: inspect the Cloudflare-to-
  origin path and Docker NAT before changing DNS.

Direct API-origin test from another machine, bypassing public DNS and
Cloudflare:

```bash
curl -k -i \
  --connect-timeout 5 \
  --max-time 10 \
  --resolve api.kanger.org:443:<VPS_IPV4> \
  https://api.kanger.org/health
```

## 15. Update and rollback

Build and copy a newly qualified JAR, then run the same installer:

```bash
sudo bash /tmp/kanger-deploy/install.sh \
  /tmp/kanger-server.jar
```

Configuration and user data are retained. The previous JAR is saved before the
service restart. An unsuccessful liveness or readiness check automatically
restores it.

Manual rollback:

```bash
sudo systemctl stop kanger-server.service
sudo cp \
  /opt/kanger-server/kanger-server.jar.previous \
  /opt/kanger-server/kanger-server.jar
sudo chown root:kanger /opt/kanger-server/kanger-server.jar
sudo chmod 0640 /opt/kanger-server/kanger-server.jar
sudo systemctl start kanger-server.service
curl --fail http://127.0.0.1:1964/health
curl --fail http://127.0.0.1:1964/ready
```

## 16. Backup

The durable server state consists of:

```text
/etc/kanger-server/kanger.conf
/var/lib/kanger-server/
```

Shared Cloudflare Origin CA material must also be backed up separately:

```text
/etc/nginx/ssl/kanger.org/
```

For a transactionally quiet filesystem backup:

```bash
stamp="$(date -u +%Y%m%dT%H%M%SZ)"
sudo systemctl stop kanger-server.service
sudo tar -C / -czf "/root/kanger-server-${stamp}.tar.gz" \
  etc/kanger-server \
  var/lib/kanger-server
sudo systemctl start kanger-server.service
curl --fail http://127.0.0.1:1964/health
curl --fail http://127.0.0.1:1964/ready
```

Copy the archive off the VPS. A backup stored only on the same VPS is not a
disaster-recovery backup. Never store an unencrypted private key in the Git
repository.

## 17. Restore

```bash
sudo systemctl stop kanger-server.service
sudo tar -C / -xzf /root/kanger-server-<timestamp>.tar.gz
sudo chown -R root:kanger /etc/kanger-server
sudo chmod 0750 /etc/kanger-server
sudo chmod 0640 /etc/kanger-server/kanger.conf
sudo chown -R kanger:kanger /var/lib/kanger-server
sudo ln -sfn \
  /etc/kanger-server/kanger.conf \
  /var/lib/kanger-server/kanger.conf
sudo chown -h root:kanger /var/lib/kanger-server/kanger.conf
sudo systemctl start kanger-server.service
curl --fail http://127.0.0.1:1964/health
curl --fail http://127.0.0.1:1964/ready
```

Restore shared Cloudflare Origin CA material separately under
`/etc/nginx/ssl/kanger.org/`, preserving root ownership and private-key mode
`0600`, then run:

```bash
sudo nginx -t
sudo systemctl restart nginx
```

## Current deployment boundary

This package qualifies build, bounded loopback HTTP, liveness/readiness,
request correlation, overload rejection, authentication/session lifecycle,
filesystem input confinement, atomic settings, platform TLS for outbound HTTP,
graceful SIGTERM shutdown, reproducible systemd/nginx API deployment, and
bounded explicit confirmation-mail transport.

The static UI deployment for `kanger.org` and `www.kanger.org` is deliberately
separate from the Java service boundary. It can reuse the shared certificate and
call `https://api.kanger.org` through the explicit CORS allow-list.

The historical mail helper remains present for source compatibility, but the
active server request path intercepts new e-mail registrations and resend
requests before those legacy raw-thread branches. A later consolidation slice
may remove that unreachable compatibility code after protocol compatibility is
frozen.
